package com.learning.discovery.product.repository;

import com.learning.discovery.product.model.Product;
import org.springframework.data.repository.ListCrudRepository;

public interface ProductRepository extends ListCrudRepository<Product, Long> {
}
