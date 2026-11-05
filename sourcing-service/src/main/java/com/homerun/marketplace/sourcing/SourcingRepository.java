package com.homerun.marketplace.sourcing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SourcingRepository {
    private static final RowMapper<Vendor> VENDOR = (rs, row) -> new Vendor(rs.getString("id"),
            rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("active"));
    private static final RowMapper<VendorOffer> OFFER = (rs, row) -> new VendorOffer(rs.getString("id"),
            rs.getString("product_id"), rs.getString("vendor_id"), rs.getString("vendor_sku"),
            rs.getLong("cost_minor"), rs.getInt("available_quantity"), rs.getInt("lead_days"), rs.getBoolean("active"));
    private final JdbcTemplate db;

    public SourcingRepository(JdbcTemplate db) { this.db = db; }
    public List<Vendor> vendors() { return db.query("SELECT * FROM vendors ORDER BY name", VENDOR); }
    public Optional<Vendor> vendor(String id) { return db.query("SELECT * FROM vendors WHERE id=?", VENDOR, id).stream().findFirst(); }
    public Vendor createVendor(Vendor.Input input) {
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO vendors(id,name,email,phone,active) VALUES (?,?,?,?,?)",
                id, input.name(), input.email(), input.phone(), input.active());
        return vendor(id).orElseThrow();
    }
    public boolean updateVendor(String id, Vendor.Input input) {
        return db.update("UPDATE vendors SET name=?,email=?,phone=?,active=? WHERE id=?",
                input.name(), input.email(), input.phone(), input.active(), id) > 0;
    }
    public boolean deleteVendor(String id) { return db.update("UPDATE vendors SET active=FALSE WHERE id=?", id) > 0; }
    public List<VendorOffer> offers(String productId) {
        return productId == null || productId.isBlank()
                ? db.query("SELECT * FROM vendor_offers ORDER BY product_id,vendor_id", OFFER)
                : db.query("SELECT * FROM vendor_offers WHERE product_id=? ORDER BY vendor_id", OFFER, productId);
    }
    public Optional<VendorOffer> offer(String id) { return db.query("SELECT * FROM vendor_offers WHERE id=?", OFFER, id).stream().findFirst(); }
    public VendorOffer createOffer(VendorOffer.Input input) {
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO vendor_offers(id,product_id,vendor_id,vendor_sku,cost_minor,available_quantity,lead_days,active) VALUES (?,?,?,?,?,?,?,?)",
                id, input.productId(), input.vendorId(), input.vendorSku(), input.costMinor(),
                input.availableQuantity(), input.leadDays(), input.active());
        return offer(id).orElseThrow();
    }
    public boolean updateOffer(String id, VendorOffer.Input input) {
        return db.update("UPDATE vendor_offers SET product_id=?,vendor_id=?,vendor_sku=?,cost_minor=?,available_quantity=?,lead_days=?,active=? WHERE id=?",
                input.productId(), input.vendorId(), input.vendorSku(), input.costMinor(),
                input.availableQuantity(), input.leadDays(), input.active(), id) > 0;
    }
    public boolean deleteOffer(String id) { return db.update("DELETE FROM vendor_offers WHERE id=?", id) > 0; }
}
