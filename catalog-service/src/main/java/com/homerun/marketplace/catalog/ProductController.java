package com.homerun.marketplace.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class ProductController {
    private final ProductRepository products;
    public ProductController(ProductRepository products) { this.products = products; }

    @GetMapping("/api/products")
    public List<Product> publicList() { return products.findAll(false); }

    @GetMapping("/api/products/{id}")
    public Product publicOne(@PathVariable String id) {
        Product product = get(id);
        if (!product.active()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return product;
    }

    @GetMapping("/api/admin/products")
    public List<Product> adminList() { return products.findAll(true); }

    @PostMapping("/api/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody Product.Input input) { return products.create(input); }

    @PutMapping("/api/admin/products/{id}")
    public Product update(@PathVariable String id, @Valid @RequestBody Product.Input input) {
        if (!products.update(id, input)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return get(id);
    }

    @DeleteMapping("/api/admin/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!products.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private Product get(String id) {
        return products.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
