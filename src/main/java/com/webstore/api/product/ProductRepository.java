package com.webstore.api.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Writable access to the products table. Reads for the API go through {@link CatalogProductRepository}. */
public interface ProductRepository extends JpaRepository<Product, Integer> {

    boolean existsByCategoryId(Integer categoryId);

    /** True if the product appears on any order line. */
    @Query("select count(oi) > 0 from OrderItem oi where oi.product.id = :productId")
    boolean isOrdered(@Param("productId") int productId);
}
