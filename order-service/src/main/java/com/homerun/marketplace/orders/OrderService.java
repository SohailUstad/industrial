package com.homerun.marketplace.orders;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.homerun.marketplace.orders.OrderModels.*;

@Service
public class OrderService {
    private static final Map<String, List<String>> NEXT = Map.of(
            "PLACED", List.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", List.of("PACKING", "CANCELLED"),
            "PACKING", List.of("OUT_FOR_DELIVERY", "CANCELLED"),
            "OUT_FOR_DELIVERY", List.of("DELIVERED"),
            "DELIVERED", List.of(), "CANCELLED", List.of());
    private final CatalogClient catalog;
    private final OrderRepository orders;
    private final TransactionTemplate transaction;

    public OrderService(CatalogClient catalog, OrderRepository orders, TransactionTemplate transaction) {
        this.catalog = catalog; this.orders = orders; this.transaction = transaction;
    }

    public Receipt place(String key, CreateOrder request) {
        try { UUID.fromString(key); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be a UUID"); }
        String hash = hash(request.toString());
        var previous = orders.idForKey(key);
        if (previous.isPresent()) return existing(key, hash, previous.get());

        var seen = new HashSet<String>();
        var lines = new ArrayList<OrderItem>();
        long total = 0;
        for (var requested : request.items()) {
            if (!seen.add(requested.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate product in cart");
            var product = catalog.product(requested.productId());
            long line;
            try {
                line = Math.multiplyExact(product.priceMinor(), requested.quantity());
                total = Math.addExact(total, line);
            } catch (ArithmeticException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total is too large");
            }
            lines.add(new OrderItem(product.id(), product.sku(), product.name(), product.unit(),
                    requested.quantity(), product.priceMinor(), line));
        }
        if (total != request.expectedTotalMinor())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prices changed. Refresh the cart and review the total.");
        String id = UUID.randomUUID().toString();
        long orderTotal = total;
        try {
            transaction.executeWithoutResult(status -> orders.insert(id, key, hash, request, lines, orderTotal));
        } catch (DuplicateKeyException ex) {
            String existingId = orders.idForKey(key).orElseThrow(() -> ex);
            return existing(key, hash, existingId);
        }
        return orders.receipt(id).orElseThrow();
    }

    public List<AdminOrder> all() { return orders.all(); }
    public AdminOrder one(String id) {
        return orders.one(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public AdminOrder changeStatus(String id, String next) {
        var current = one(id);
        if (!NEXT.getOrDefault(current.status(), List.of()).contains(next))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid order status transition");
        if (!orders.updateStatus(id, current.status(), next))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order was changed by another request");
        return one(id);
    }

    private Receipt existing(String key, String hash, String id) {
        if (!orders.hashForKey(key).orElseThrow().equals(hash))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was used for a different order");
        return orders.receipt(id).orElseThrow();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
