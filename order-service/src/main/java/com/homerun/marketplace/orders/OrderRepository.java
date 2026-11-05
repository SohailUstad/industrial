package com.homerun.marketplace.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.homerun.marketplace.orders.OrderModels.*;

@Repository
public class OrderRepository {
    private static final RowMapper<OrderItem> ITEM_MAPPER = (rs, row) -> new OrderItem(
            rs.getString("product_id"), rs.getString("sku"), rs.getString("name"), rs.getString("unit"),
            rs.getInt("quantity"), rs.getLong("unit_price_minor"), rs.getLong("line_total_minor"));
    private final JdbcTemplate db;

    public OrderRepository(JdbcTemplate db) { this.db = db; }

    public void insert(String id, String key, String hash, CreateOrder request,
                       List<OrderItem> items, long total) {
        db.update("""
                INSERT INTO customer_orders(id,idempotency_key,request_hash,customer_name,phone,address_line,city,postal_code,payment_method,status,total_minor)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, id, key, hash, request.customerName().trim(), request.phone().trim(),
                request.addressLine().trim(), request.city().trim(), request.postalCode().trim(),
                "COD", "PLACED", total);
        for (var item : items) {
            db.update("""
                    INSERT INTO order_items(order_id,product_id,sku,name,unit,quantity,unit_price_minor,line_total_minor)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, id, item.productId(), item.sku(), item.name(), item.unit(), item.quantity(),
                    item.unitPriceMinor(), item.lineTotalMinor());
        }
    }

    public Optional<String> idForKey(String key) {
        return db.query("SELECT id FROM customer_orders WHERE idempotency_key=?", (rs, row) -> rs.getString(1), key)
                .stream().findFirst();
    }

    public Optional<String> hashForKey(String key) {
        return db.query("SELECT request_hash FROM customer_orders WHERE idempotency_key=?", (rs, row) -> rs.getString(1), key)
                .stream().findFirst();
    }

    public Optional<Receipt> receipt(String id) {
        return db.query("SELECT id,status,payment_method,total_minor FROM customer_orders WHERE id=?",
                (rs, row) -> new Receipt(rs.getString("id"), rs.getString("status"), rs.getString("payment_method"),
                        rs.getLong("total_minor"), items(rs.getString("id"))), id).stream().findFirst();
    }

    public List<AdminOrder> all() {
        return db.query("SELECT * FROM customer_orders ORDER BY created_at DESC LIMIT 100", (rs, row) -> admin(rs.getString("id")));
    }

    public Optional<AdminOrder> one(String id) {
        return db.query("SELECT id FROM customer_orders WHERE id=?", (rs, row) -> admin(rs.getString("id")), id)
                .stream().findFirst();
    }

    private AdminOrder admin(String id) {
        return db.queryForObject("SELECT * FROM customer_orders WHERE id=?", (rs, row) -> new AdminOrder(
                rs.getString("id"), rs.getString("customer_name"), rs.getString("phone"),
                rs.getString("address_line"), rs.getString("city"), rs.getString("postal_code"),
                rs.getString("status"), rs.getString("payment_method"), rs.getLong("total_minor"),
                rs.getTimestamp("created_at").toLocalDateTime(), items(id)), id);
    }

    private List<OrderItem> items(String id) {
        return db.query("SELECT * FROM order_items WHERE order_id=? ORDER BY name", ITEM_MAPPER, id);
    }

    public boolean updateStatus(String id, String expected, String next) {
        return db.update("UPDATE customer_orders SET status=? WHERE id=? AND status=?", next, id, expected) == 1;
    }
}
