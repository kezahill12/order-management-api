# Centrika Order Management API

Backend for an internal order management system used by an operations team.
Java 17 · Spring Boot 3 · PostgreSQL 15.

## Project layout

```
schema.sql           Full DDL — tables, constraints, indexes (Part 1)
queries.sql          The three required analytical queries (Part 1)
src/main/java/...    Spring Boot application (Part 2)
DESIGN.md            Indexing strategy, ORM choice, concurrency trade-off,
                     and the system-design write-up (Parts 1 & 3)
docker-compose.yml   Postgres 15 + the API, wired together
Dockerfile           Multi-stage build for the application image
```

## Prerequisites

- Docker Desktop (for the recommended path), or
- JDK 17 and Maven 3.9+ with a local PostgreSQL 15 instance

## Running with Docker Compose (recommended)

From the project root (the folder containing `docker-compose.yml`):

```bash
cp .env.example .env      # on Windows PowerShell: copy .env.example .env
docker-compose up --build
```

The first run takes a few minutes while base images are pulled and the
application jar is built. The stack is ready when the logs show:

```
Started OrderApiApplication in X seconds
```

Postgres creates the database and runs `schema.sql` automatically on first
startup, so no manual database setup is required. The API is then available
at `http://localhost:8080`.

To stop the stack, press `Ctrl+C`. To stop it and also delete the database
volume (a clean slate on the next run):

```bash
docker-compose down -v
```

### If port 5432 is already in use

Another PostgreSQL instance (local service or a container from a different
project) is holding the port. Stop it, or change the host-side port mapping
in `docker-compose.yml` from `"5432:5432"` to e.g. `"5433:5432"`.

## Running without Docker

1. Create the database and load the schema:
   ```bash
   createdb centrika
   psql -d centrika -f schema.sql
   ```
2. Set the datasource properties in `src/main/resources/application.yml`
   (or via environment variables) to match your local Postgres credentials.
3. Start the application:
   ```bash
   mvn spring-boot:run
   ```

## Running the tests

```bash
mvn test
```

`OrderConcurrencyTest` fires two simultaneous requests at a product holding a
single unit of stock and asserts that exactly one order succeeds — the
regression test for the Part 2 concurrency requirement. It runs against an
in-memory H2 database for speed; H2's row-locking semantics are not identical
to Postgres's, so the behaviour was also exercised manually against the real
Postgres instance.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/orders?page=&size=&status=&customerId=&from=&to=` | Paginated, filtered order list |
| GET | `/api/orders/{id}` | Single order with its line items |
| POST | `/api/orders` | Create an order; deducts stock atomically |
| PUT | `/api/orders/{id}/status` | Update an order's status |
| GET | `/api/customers/{id}/summary` | Total spend, order count, last order date |

All filtering and pagination is applied in the database — no full-table reads
into application memory.

### Status codes

| Code | When |
|---|---|
| 200 / 201 | Success |
| 400 | Validation failure; the response body lists the offending fields |
| 404 | Referenced order, customer, or product does not exist |
| 409 | Insufficient stock for a requested line item |

### Example requests

Create an order:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "items": [{"productId": 1, "quantity": 2}]}'
```

Update its status:

```bash
curl -X PUT http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "SHIPPED"}'
```

Fetch a customer summary:

```bash
curl http://localhost:8080/api/customers/1/summary
```

Note that the exam scope does not include endpoints for creating customers or
products, so seed rows for those must be inserted directly:

```bash
docker exec -it centrika-order-api-postgres-1 \
  psql -U postgres -d centrika \
  -c "INSERT INTO customers (name, email, region, tier)
      VALUES ('Acme Ltd', 'ops@acme.example', 'EU', 'STANDARD');"
```

## Design notes

`DESIGN.md` covers the indexing strategy and the one deliberate
denormalisation (Part 1), the ORM choice and the pessimistic-vs-optimistic
locking trade-off (Part 2), and the diagnosis, caching, pagination, and
constrained-optimisation write-up (Part 3).

## What I would add with more time

- Idempotency keys on `POST /api/orders` so client retries are safe.
- Flyway or Liquibase migrations in place of a single `schema.sql`, so schema
  changes are versioned rather than hand-applied.
- A Redis-backed implementation of the caching strategy described in
  `DESIGN.md`; the write-up covers the approach, but wiring it up was outside
  the time budget.
- Testcontainers-based integration tests, to validate `SELECT ... FOR UPDATE`
  against real Postgres rather than H2's approximation of it.
