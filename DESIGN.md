# Design Notes

## Part 1 — Indexing strategy & denormalisation

**Indexes.** Every listed query filters or sorts on some combination of
`customer_id`, `status`, and `created_at`, so those are the composite indexes
built in `schema.sql`:

- `(customer_id, created_at DESC)` — serves "orders for customer X, most
  recent first" without a sort step.
- `(status, created_at DESC)` — serves status-filtered listings and the
  admin "pending orders" view.
- `(created_at DESC)` alone — covers date-range scans that aren't scoped to
  a customer or status (e.g. the revenue-trend query).
- A **partial index** on `products (stock_quantity) WHERE stock_quantity <
  20` — the low-stock alert query only ever cares about a small slice of
  the table, so indexing just that slice keeps the index small and cheap
  to maintain on every stock update, instead of indexing all stock values.

Composite indexes put the equality-filter column first and the range/sort
column last, which is what lets Postgres use the same index for both parts
of a query like "this customer's orders, newest first."

**Denormalisation.** The one deliberate denormalisation is
`order_items.unit_price_at_purchase`. If order items only stored a foreign
key to `products` and looked up the price at read time, a price change six
months from now would silently rewrite the historical value of every past
order — invoices and revenue reports would stop matching what the customer
was actually charged. Storing the price snapshot trades a small amount of
duplicated data for correctness of historical records, which is the right
trade for a system that reports on revenue.

## Part 2 — ORM choice

**Spring Data JPA**, for three reasons specific to this system:

1. The domain has real relationships (`Order` → `OrderItem` → `Product`,
   `Order` → `Customer`) that map cleanly onto JPA associations, and
   cascading the order/order-item write as a unit is exactly what
   `@OneToMany(cascade = ALL)` is for.
2. `JpaSpecificationExecutor` gives composable, server-side dynamic
   filtering (`OrderSpecifications`) for the `GET /api/orders` query
   parameters, without writing a bespoke query per filter combination.
3. `@Lock(LockModeType.PESSIMISTIC_WRITE)` and `@Version` are both native
   JPA concepts, so the concurrency mechanism (below) doesn't need any
   hand-rolled SQL.

The trade-off: JPA can hide N+1 queries and generate suboptimal SQL for
complex aggregations. I mitigated the first with `default_batch_fetch_size`
and `open-in-view: false`, and sidestepped the second by writing the
customer-summary aggregation as a direct JPQL projection
(`OrderItemRepository.summarizeForCustomer`) rather than loading entities
and summing in Java. For the three Part-1 analytical queries specifically,
raw SQL (as delivered in `queries.sql`) is the better tool regardless of
ORM — aggregation-heavy reporting queries are exactly where an ORM adds
overhead without adding clarity.

## Part 2 — Concurrency & stock deduction

**Chosen approach: pessimistic locking** (`SELECT ... FOR UPDATE` via
`@Lock(LockModeType.PESSIMISTIC_WRITE)` in `ProductRepository`), combined
with a `@Version` column on `Product` for defence in depth against any
write path that bypasses the locked read.

How it prevents overselling: when `POST /api/orders` processes a line
item, it locks the product row before reading `stockQuantity`. A second
concurrent request for the same product blocks at that same line until the
first transaction commits or rolls back — it cannot read a stale
`stockQuantity` and make a decision based on it. Only after the lock is
released does the second transaction see the updated stock and correctly
reject the order if there isn't enough left.

**Trade-off vs. optimistic locking:** optimistic locking (relying solely on
`@Version` and retrying on a `OptimisticLockException`) avoids holding a
DB-level lock and so scales better under low contention, but under high
contention on a *specific* hot product (a flash sale, a single popular
SKU) it means most concurrent requests fail and must retry, which pushes
complexity into the client/retry layer and can thrash under load.
Pessimistic locking is the better fit here specifically because the
requirement is about *low-stock* products — by definition the exact
scenario where contention on one row is likely, and where correctness
(never overselling) matters more than raw throughput. The cost is that a
slow transaction holding the lock briefly serialises other orders for that
one product; that's an acceptable trade for an internal ops tool that
isn't expected to run tens of thousands of concurrent orders against a
single SKU.

## Part 3 — Scaling `GET /api/orders` at 50,000 req/min

### 1. Diagnosis first

I'd work from the outside in, cheapest checks first:

1. **Connection pool saturation.** Check HikariCP metrics
   (`hikaricp.connections.pending`, `.active`, `.timeout`) first — if
   requests are queuing for a DB connection, no query-level optimisation
   will show up in the app's own latency numbers, because the time is
   being spent waiting for a connection, not executing SQL. This takes
   five minutes to check and rules out (or confirms) a whole class of
   causes.
2. **`pg_stat_statements`** to find the actual slow query, ranked by total
   time, not just mean time — at 50k req/min a query that's individually
   fast but runs constantly can dominate total load more than a rare slow
   one.
3. **`EXPLAIN (ANALYZE, BUFFERS)`** on the identified query, run with
   realistic filter parameters (a wide date range, a common status) — I'd
   specifically look for a sequential scan where an index scan is
   expected, or a large gap between planned and actual row counts
   (stale statistics → `ANALYZE` the table).
4. **Missing or unused indexes** — cross-reference the query's WHERE/ORDER
   BY columns against `pg_indexes`, and check `pg_stat_user_indexes` for
   indexes that exist but aren't being used (wrong column order, or the
   planner choosing a seq scan anyway due to low selectivity).

### 2. Caching strategy

Cache **`GET /api/customers/{id}/summary`** and the **first page of
`GET /api/orders` for common filter combinations** (e.g. `status=pending`
with no date range) — these are read-heavy, computed from data that
changes less often than it's read, and expensive to recompute (aggregation
across `order_items`).

- **What, not how much:** cache the serialised response body keyed by the
  request parameters (`orders:status=pending:page=0`), not raw query
  results — this avoids re-running the JPA mapping on every cache hit.
- **TTL:** short — 30-60 seconds. This system has "occasional bulk
  writes," so a short TTL bounds staleness without needing perfect
  invalidation, and is simple to reason about.
- **Invalidation on status update:** rather than trying to invalidate
  every cache key that might contain a given order (impractical with
  arbitrary filter combinations), I'd evict by a coarser key: bump a
  `orders:version` counter on every `PUT /api/orders/{id}/status` and
  fold that counter into every cache key
  (`orders:v{version}:status=pending:page=0`). A status update instantly
  invalidates *all* list-view cache entries in one write, at the cost of
  a cache-hit-rate dip right after any update — an acceptable trade given
  updates are far less frequent than reads at this traffic profile.
  The individual-order cache (`GET /api/orders/{id}`) can instead be
  evicted precisely by ID on update, since that key doesn't fan out.

### 3. Pagination at scale

**OFFSET pagination degrades because `OFFSET n` doesn't skip rows for
free** — Postgres still has to scan and discard the first `n` rows before
returning the page. At 50 million rows, requesting page 10,000 means
scanning and throwing away ~10 million rows on every single request, and
that cost grows linearly with page depth regardless of indexes.

**Alternative: keyset (cursor) pagination.** Instead of `OFFSET`/`LIMIT`,
paginate on the last-seen sort key:

```sql
SELECT * FROM orders
WHERE (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

This uses the existing `(created_at DESC)` / `(customer_id, created_at
DESC)` indexes directly — the query jumps straight to the right position
via the index rather than counting through discarded rows, so latency stays
roughly constant regardless of how deep into the result set the client
is. The trade-off is that clients can no longer jump to an arbitrary page
number ("go to page 500") — only forward/backward from a cursor — which is
an acceptable UX change for an internal ops tool that mostly scrolls
through recent orders rather than page-hopping.

### 4. Constrained optimisation without new indexes

If the bottleneck is one expensive JOIN on `orders` and I can't add an
index to that table, my options, roughly in order of effort:

- **Push the JOIN to the other side.** If the expensive join is
  `orders ⋈ order_items` or `orders ⋈ customers`, check whether an index
  *on the other table* (`order_items.order_id`, `customers.id` — already
  present here) can drive the join instead, with `orders` as the inner,
  indexed-lookup side rather than the table being scanned.
- **Materialised view / summary table**, refreshed on a schedule (e.g.
  every minute) or incrementally on write, that pre-joins and
  pre-aggregates the expensive combination. The read path queries the
  summary table (which *can* have its own indexes) instead of doing the
  JOIN live. This trades some staleness for eliminating the join cost
  entirely on the hot path — reasonable for an ops dashboard that doesn't
  need sub-second freshness.
- **Read replica** for `GET` traffic specifically, so the expensive join
  runs against a replica and can't starve the primary's write capacity
  (bulk writes) or other read queries.
- **Application-level caching** (Part 3.2) to reduce how often the join
  needs to run at all — the cheapest fix if the traffic is dominated by a
  small number of repeated filter combinations rather than being truly
  unique per request.
- If none of the above is available, **denormalise the join away**: store
  the specific columns the join exists to fetch (e.g. a customer's tier)
  directly on `orders` at write time, updated via a trigger or the
  application, at the cost of the same duplication trade-off described in
  Part 1.

I'd reach for the materialised view or read replica first — both fix the
problem structurally rather than shifting load around, and neither
requires touching the `orders` table's indexes, which the constraint rules
out.
