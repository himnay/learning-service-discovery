package com.learning.discovery.product.controller;

import com.learning.discovery.product.model.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

@RestController
class ProductController {

    private static final List<Product> PRODUCTS = List.of(
            new Product(201L, "Keyboard", new BigDecimal("49.99")),
            new Product(202L, "Monitor", new BigDecimal("199.99")),
            new Product(203L, "Mouse", new BigDecimal("19.99")));

    @GetMapping("/api/products")
    List<Product> getProducts() {
        return PRODUCTS;
    }

    @GetMapping("/api/products/{id}")
    Product getProduct(@PathVariable Long id) {
        return PRODUCTS.stream()
                .filter(product -> product.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + id + " not found"));
    }
}
