USE enterprise_ecommerce;

START TRANSACTION;

INSERT INTO customers (id, name, email, city, country, created_at) VALUES
    (1, 'Avery Chen', 'customer01@example.com', 'Toronto', 'CA', '2024-01-03 09:00:00'),
    (2, 'Morgan Patel', 'customer02@example.com', 'Vancouver', 'CA', '2024-01-10 09:00:00'),
    (3, 'Jordan Reyes', 'customer03@example.com', 'Austin', 'US', '2024-01-18 09:00:00'),
    (4, 'Casey Brooks', 'customer04@example.com', 'Seattle', 'US', '2024-01-26 09:00:00'),
    (5, 'Taylor Kim', 'customer05@example.com', 'London', 'GB', '2024-02-02 09:00:00'),
    (6, 'Riley Singh', 'customer06@example.com', 'Manchester', 'GB', '2024-02-10 09:00:00'),
    (7, 'Jamie Rivera', 'customer07@example.com', 'Sydney', 'AU', '2024-02-18 09:00:00'),
    (8, 'Cameron Lee', 'customer08@example.com', 'Melbourne', 'AU', '2024-02-26 09:00:00'),
    (9, 'Drew Wilson', 'customer09@example.com', 'Berlin', 'DE', '2024-03-05 09:00:00'),
    (10, 'Quinn Martin', 'customer10@example.com', 'Hamburg', 'DE', '2024-03-13 09:00:00'),
    (11, 'Robin Davis', 'customer11@example.com', 'Amsterdam', 'NL', '2024-03-21 09:00:00'),
    (12, 'Skyler Ahmed', 'customer12@example.com', 'Rotterdam', 'NL', '2024-03-29 09:00:00'),
    (13, 'Alex Morgan', 'customer13@example.com', 'Chicago', 'US', '2024-04-06 09:00:00'),
    (14, 'Emerson Clark', 'customer14@example.com', 'Denver', 'US', '2024-04-14 09:00:00'),
    (15, 'Samira Khan', 'customer15@example.com', 'Calgary', 'CA', '2024-04-22 09:00:00'),
    (16, 'Noah Bennett', 'customer16@example.com', 'Ottawa', 'CA', '2024-04-30 09:00:00'),
    (17, 'Mila Thompson', 'customer17@example.com', 'Bristol', 'GB', '2024-05-08 09:00:00'),
    (18, 'Elliot Foster', 'customer18@example.com', 'Leeds', 'GB', '2024-05-16 09:00:00'),
    (19, 'Harper Nguyen', 'customer19@example.com', 'Brisbane', 'AU', '2024-05-24 09:00:00'),
    (20, 'Aria Campbell', 'customer20@example.com', 'Perth', 'AU', '2024-06-01 09:00:00'),
    (21, 'Finley Schmidt', 'customer21@example.com', 'Munich', 'DE', '2024-06-09 09:00:00'),
    (22, 'Sasha Laurent', 'customer22@example.com', 'Cologne', 'DE', '2024-06-17 09:00:00'),
    (23, 'Theo Janssen', 'customer23@example.com', 'Utrecht', 'NL', '2024-06-25 09:00:00'),
    (24, 'Ivy de Vries', 'customer24@example.com', 'Eindhoven', 'NL', '2024-07-03 09:00:00') AS incoming_customer
ON DUPLICATE KEY UPDATE
    name = incoming_customer.name, email = incoming_customer.email,
    city = incoming_customer.city, country = incoming_customer.country,
    created_at = incoming_customer.created_at;

INSERT INTO products (id, product_name, category, price, stock_quantity, created_at) VALUES
    (1, 'Wireless Keyboard', 'Electronics', 79.00, 125, '2024-01-01 00:00:00'),
    (2, 'Noise-Cancelling Headphones', 'Electronics', 189.00, 64, '2024-01-01 00:00:00'),
    (3, 'USB-C Dock', 'Electronics', 129.00, 83, '2024-01-01 00:00:00'),
    (4, 'Portable SSD 1TB', 'Electronics', 109.00, 91, '2024-01-01 00:00:00'),
    (5, 'Desk Lamp', 'Home', 54.00, 142, '2024-01-01 00:00:00'),
    (6, 'Ceramic Planter Set', 'Home', 38.00, 177, '2024-01-01 00:00:00'),
    (7, 'Standing Desk Mat', 'Home', 68.00, 73, '2024-01-01 00:00:00'),
    (8, 'Insulated Bottle', 'Home', 32.00, 208, '2024-01-01 00:00:00'),
    (9, 'Merino Crew Sweater', 'Apparel', 118.00, 56, '2024-01-01 00:00:00'),
    (10, 'Everyday Canvas Tote', 'Apparel', 29.00, 194, '2024-01-01 00:00:00'),
    (11, 'Lightweight Rain Jacket', 'Apparel', 145.00, 49, '2024-01-01 00:00:00'),
    (12, 'Trail Running Socks', 'Apparel', 22.00, 266, '2024-01-01 00:00:00'),
    (13, 'Yoga Mat', 'Sports', 74.00, 101, '2024-01-01 00:00:00'),
    (14, 'Adjustable Dumbbell Pair', 'Sports', 249.00, 28, '2024-01-01 00:00:00'),
    (15, 'Cycling Helmet', 'Sports', 96.00, 67, '2024-01-01 00:00:00'),
    (16, 'Foam Roller', 'Sports', 35.00, 159, '2024-01-01 00:00:00'),
    (17, 'Data Systems Handbook', 'Books', 48.00, 84, '2024-01-01 00:00:00'),
    (18, 'Modern Product Design', 'Books', 42.00, 115, '2024-01-01 00:00:00'),
    (19, 'Practical SQL Patterns', 'Books', 56.00, 97, '2024-01-01 00:00:00'),
    (20, 'Field Notes Set', 'Books', 18.00, 301, '2024-01-01 00:00:00'),
    (21, 'Smart Speaker Mini', 'Electronics', 89.00, 73, '2024-01-01 00:00:00'),
    (22, 'Wool Throw Blanket', 'Home', 132.00, 38, '2024-01-01 00:00:00'),
    (23, 'Running Belt', 'Sports', 31.00, 123, '2024-01-01 00:00:00'),
    (24, 'Reusable Lunch Kit', 'Home', 44.00, 139, '2024-01-01 00:00:00') AS incoming_product
ON DUPLICATE KEY UPDATE
    product_name = incoming_product.product_name,
    category = incoming_product.category, price = incoming_product.price,
    stock_quantity = incoming_product.stock_quantity,
    created_at = incoming_product.created_at;

DROP TEMPORARY TABLE IF EXISTS seed_order_lines;
CREATE TEMPORARY TABLE seed_order_lines (
    order_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_date DATETIME NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    line_number TINYINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (item_id)
) ENGINE=MEMORY;

INSERT INTO seed_order_lines
    (order_id, customer_id, order_date, order_status, line_number, item_id, product_id, quantity, unit_price)
WITH RECURSIVE order_sequence AS (
    SELECT 1 AS order_id
    UNION ALL
    SELECT order_id + 1 FROM order_sequence WHERE order_id < 540
), order_lines AS (
    SELECT 1 AS line_number
    UNION ALL
    SELECT 2
)
SELECT s.order_id,
       MOD(s.order_id - 1, 24) + 1,
       TIMESTAMPADD(DAY, s.order_id - 1, '2024-01-01 10:00:00'),
       CASE MOD(s.order_id, 20)
           WHEN 0 THEN 'cancelled'
           WHEN 1 THEN 'refunded'
           WHEN 2 THEN 'pending'
           WHEN 3 THEN 'processing'
           WHEN 4 THEN 'shipped'
           ELSE 'delivered'
       END,
       l.line_number,
       ((s.order_id - 1) * 2) + l.line_number,
       MOD(s.order_id + (l.line_number * 5) - 2, 24) + 1,
       MOD(s.order_id + l.line_number, 3) + 1,
       p.price
FROM order_sequence AS s
CROSS JOIN order_lines AS l
JOIN products AS p
    ON p.id = MOD(s.order_id + (l.line_number * 5) - 2, 24) + 1;

INSERT INTO orders (id, customer_id, order_date, order_status, total_amount)
SELECT seed_orders.id,
       seed_orders.customer_id,
       seed_orders.order_date,
       seed_orders.order_status,
       seed_orders.total_amount
FROM (
    SELECT order_id AS id,
           customer_id,
           order_date,
           order_status,
           SUM(quantity * unit_price) AS total_amount
    FROM seed_order_lines
    GROUP BY order_id, customer_id, order_date, order_status
) AS seed_orders
ON DUPLICATE KEY UPDATE
    customer_id = seed_orders.customer_id,
    order_date = seed_orders.order_date,
    order_status = seed_orders.order_status,
    total_amount = seed_orders.total_amount;

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price)
SELECT seed_items.id,
       seed_items.order_id,
       seed_items.product_id,
       seed_items.quantity,
       seed_items.unit_price
FROM (
    SELECT item_id AS id, order_id, product_id, quantity, unit_price
    FROM seed_order_lines
) AS seed_items
ON DUPLICATE KEY UPDATE
    order_id = seed_items.order_id,
    product_id = seed_items.product_id,
    quantity = seed_items.quantity,
    unit_price = seed_items.unit_price;

INSERT INTO payments (id, order_id, payment_method, payment_status, amount, paid_at)
SELECT seed_payments.id,
       seed_payments.order_id,
       seed_payments.payment_method,
       seed_payments.payment_status,
       seed_payments.amount,
       seed_payments.paid_at
FROM (
    SELECT o.id,
           o.id AS order_id,
           CASE MOD(o.id, 4)
               WHEN 0 THEN 'card'
               WHEN 1 THEN 'digital_wallet'
               WHEN 2 THEN 'bank_transfer'
               ELSE 'buy_now_pay_later'
           END AS payment_method,
           CASE o.order_status
               WHEN 'cancelled' THEN 'failed'
               WHEN 'refunded' THEN 'refunded'
               WHEN 'pending' THEN 'pending'
               WHEN 'processing' THEN 'authorized'
               ELSE 'paid'
           END AS payment_status,
           o.total_amount AS amount,
           CASE
               WHEN o.order_status IN ('shipped', 'delivered', 'refunded')
                   THEN TIMESTAMPADD(DAY, 1, o.order_date)
               ELSE NULL
           END AS paid_at
    FROM orders AS o
    WHERE o.id BETWEEN 1 AND 540
) AS seed_payments
ON DUPLICATE KEY UPDATE
    order_id = seed_payments.order_id,
    payment_method = seed_payments.payment_method,
    payment_status = seed_payments.payment_status,
    amount = seed_payments.amount,
    paid_at = seed_payments.paid_at;

DROP TEMPORARY TABLE seed_order_lines;
COMMIT;

SELECT 'customers' AS table_name, COUNT(*) AS row_count FROM customers
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL SELECT 'payments', COUNT(*) FROM payments
ORDER BY table_name;

-- Monthly completed order revenue and volume.
SELECT DATE_FORMAT(order_date, '%Y-%m') AS sales_month,
       COUNT(*) AS completed_orders,
       SUM(total_amount) AS revenue
FROM orders
WHERE order_status IN ('shipped', 'delivered')
GROUP BY sales_month
ORDER BY sales_month;

-- Revenue and units sold by product category.
SELECT p.category,
       SUM(oi.quantity) AS units_sold,
       SUM(oi.quantity * oi.unit_price) AS item_revenue
FROM order_items AS oi
JOIN products AS p ON p.id = oi.product_id
JOIN orders AS o ON o.id = oi.order_id
WHERE o.order_status IN ('shipped', 'delivered')
GROUP BY p.category
ORDER BY item_revenue DESC;
