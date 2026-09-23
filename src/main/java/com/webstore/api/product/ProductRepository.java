package com.webstore.api.product;

import org.springframework.data.jpa.repository.JpaRepository;

/** Writable access to the products table. Reads for the API go through {@link CatalogProductRepository}. */
public interface ProductRepository extends JpaRepository<Product, Integer> {
}
