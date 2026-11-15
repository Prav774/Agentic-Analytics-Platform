CREATE DATABASE IF NOT EXISTS enterprise_ecommerce
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE enterprise_ecommerce;

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    city VARCHAR(100) NOT NULL,
    country CHAR(2) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT chk_ecommerce_customer_email CHECK (email LIKE '%@%.%'),
    INDEX idx_ecommerce_customers_country_created_at (country, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(180) NOT NULL UNIQUE,
    category VARCHAR(80) NOT NULL,
    price DECIMAL(12, 2) NOT NULL CHECK (price > 0),
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0),
    created_at DATETIME NOT NULL,
    INDEX idx_ecommerce_products_category (category)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_date DATETIME NOT NULL,
    order_status VARCHAR(20) NOT NULL
        CHECK (order_status IN ('pending', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded')),
    total_amount DECIMAL(14, 2) NOT NULL CHECK (total_amount >= 0),
    CONSTRAINT fk_ecommerce_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT,
    INDEX idx_ecommerce_orders_customer_date (customer_id, order_date),
    INDEX idx_ecommerce_orders_status_date (order_status, order_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(12, 2) NOT NULL CHECK (unit_price > 0),
    CONSTRAINT fk_ecommerce_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ecommerce_order_items_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    UNIQUE KEY uq_ecommerce_order_product (order_id, product_id),
    INDEX idx_ecommerce_order_items_product (product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    payment_method VARCHAR(32) NOT NULL
        CHECK (payment_method IN ('card', 'digital_wallet', 'bank_transfer', 'buy_now_pay_later')),
    payment_status VARCHAR(20) NOT NULL
        CHECK (payment_status IN ('pending', 'authorized', 'paid', 'failed', 'refunded')),
    amount DECIMAL(14, 2) NOT NULL CHECK (amount >= 0),
    paid_at DATETIME NULL,
    CONSTRAINT fk_ecommerce_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    INDEX idx_ecommerce_payments_order_status (order_id, payment_status),
    INDEX idx_ecommerce_payments_paid_at (paid_at)
) ENGINE=InnoDB;
