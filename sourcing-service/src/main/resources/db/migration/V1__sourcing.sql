CREATE TABLE vendors (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  phone VARCHAR(80),
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE vendor_offers (
  id VARCHAR(36) PRIMARY KEY,
  product_id VARCHAR(36) NOT NULL,
  vendor_id VARCHAR(36) NOT NULL REFERENCES vendors(id),
  vendor_sku VARCHAR(100),
  cost_minor BIGINT NOT NULL CHECK (cost_minor >= 0),
  available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
  lead_days INTEGER NOT NULL CHECK (lead_days >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT uq_product_vendor UNIQUE(product_id, vendor_id)
);
CREATE INDEX idx_vendor_offers_product ON vendor_offers(product_id);
