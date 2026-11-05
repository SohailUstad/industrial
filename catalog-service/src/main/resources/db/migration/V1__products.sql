CREATE TABLE products (
  id VARCHAR(36) PRIMARY KEY,
  sku VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(120) NOT NULL,
  description VARCHAR(3000) NOT NULL,
  unit VARCHAR(40) NOT NULL,
  price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
  image_url VARCHAR(2000),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_products_active_category ON products(active, category);
