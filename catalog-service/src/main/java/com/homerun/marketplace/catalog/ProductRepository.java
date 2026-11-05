package com.homerun.marketplace.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductRepository {
    private static final RowMapper<Product> MAPPER = (rs, row) -> new Product(
            rs.getString("id"), rs.getString("sku"), rs.getString("name"),
            rs.getString("category"), rs.getString("description"), rs.getString("unit"),
            rs.getLong("price_minor"), rs.getString("image_url"), rs.getBoolean("active"));
    private final JdbcTemplate db;

    public ProductRepository(JdbcTemplate db) { this.db = db; }

    public List<Product> findAll(boolean includeInactive) {
        String sql = "SELECT * FROM products" + (includeInactive ? "" : " WHERE active = TRUE") + " ORDER BY name";
        return db.query(sql, MAPPER);
    }

    public Optional<Product> find(String id) {
        return db.query("SELECT * FROM products WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Product create(Product.Input input) {
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO products(id,sku,name,category,description,unit,price_minor,image_url,active) VALUES (?,?,?,?,?,?,?,?,?)",
                id, input.sku(), input.name(), input.category(), input.description(), input.unit(),
                input.priceMinor(), input.imageUrl(), input.active());
        return find(id).orElseThrow();
    }

    public boolean update(String id, Product.Input input) {
        return db.update("UPDATE products SET sku=?,name=?,category=?,description=?,unit=?,price_minor=?,image_url=?,active=? WHERE id=?",
                input.sku(), input.name(), input.category(), input.description(), input.unit(),
                input.priceMinor(), input.imageUrl(), input.active(), id) > 0;
    }

    public boolean delete(String id) {
        return db.update("UPDATE products SET active=FALSE WHERE id=?", id) > 0;
    }
}
