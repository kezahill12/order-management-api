-- =========================================================
-- Query 1: Top 10 customers by total revenue in the last 90 days
-- Uses idx_orders_created_at (or idx_orders_customer_created) to
-- prune the range, then aggregates order_items via idx_order_items_order_id.
-- =========================================================
SELECT
    c.id,
    c.name,
    c.tier,
    SUM(oi.quantity * oi.unit_price_at_purchase) AS total_revenue
FROM customers c
JOIN orders o       ON o.customer_id = c.id
JOIN order_items oi ON oi.order_id = o.id
WHERE o.created_at >= now() - INTERVAL '90 days'
  AND o.status <> 'cancelled'
GROUP BY c.id, c.name, c.tier
ORDER BY total_revenue DESC
LIMIT 10;

-- =========================================================
-- Query 2: Products with stock below 20 units that have had at
-- least one order in the last 30 days.
-- The partial index idx_products_stock_quantity makes the stock
-- filter effectively free; EXISTS avoids fan-out from multiple
-- matching order_items rows (a plain JOIN would need DISTINCT).
-- =========================================================
SELECT
    p.id,
    p.name,
    p.sku,
    p.stock_quantity
FROM products p
WHERE p.stock_quantity < 20
  AND EXISTS (
      SELECT 1
      FROM order_items oi
      JOIN orders o ON o.id = oi.order_id
      WHERE oi.product_id = p.id
        AND o.created_at >= now() - INTERVAL '30 days'
  )
ORDER BY p.stock_quantity ASC;

-- =========================================================
-- Query 3: Monthly revenue trend for the past 12 months,
-- broken down by customer tier.
-- date_trunc is applied to o.created_at only inside the predicate's
-- lower bound (not wrapped around the indexed column itself), so the
-- created_at index is still usable for the range scan; the GROUP BY
-- truncation itself is unavoidable and happens on already-filtered rows.
-- =========================================================
SELECT
    date_trunc('month', o.created_at) AS month,
    c.tier,
    SUM(oi.quantity * oi.unit_price_at_purchase) AS revenue
FROM orders o
JOIN customers c    ON c.id = o.customer_id
JOIN order_items oi ON oi.order_id = o.id
WHERE o.created_at >= date_trunc('month', now()) - INTERVAL '12 months'
  AND o.status <> 'cancelled'
GROUP BY 1, 2
ORDER BY 1, 2;
