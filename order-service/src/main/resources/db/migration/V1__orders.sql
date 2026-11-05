CREATE TABLE customer_orders (
  id VARCHAR(36) PRIMARY KEY,
  idempotency_key VARCHAR(36) NOT NULL UNIQUE,
  request_hash VARCHAR(64) NOT NULL,
  customer_name VARCHAR(120) NOT NULL,
  phone VARCHAR(24) NOT NULL,
  address_line VARCHAR(255) NOT NULL,
  city VARCHAR(120) NOT NULL,
  postal_code VARCHAR(16) NOT NULL,
  payment_method VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  total_minor BIGINT NOT NULL CHECK (total_minor >= 0),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_items (
  order_id VARCHAR(36) NOT NULL REFERENCES customer_orders(id),
  product_id VARCHAR(36) NOT NULL,
  sku VARCHAR(80) NOT NULL,
  name VARCHAR(255) NOT NULL,
  unit VARCHAR(40) NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor >= 0),
  line_total_minor BIGINT NOT NULL CHECK (line_total_minor >= 0),
  PRIMARY KEY(order_id, product_id)
);
CREATE INDEX idx_customer_orders_created ON customer_orders(created_at DESC);
