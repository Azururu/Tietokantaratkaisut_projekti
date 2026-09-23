package com.webstore.api.product;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

    /** Loads category and supplier in the same query to avoid N+1 selects. */
    @Override
    @EntityGraph(attributePaths = { "category", "supplier" })
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Prefix match (LIKE 'x%') so the idx_name index can be used. */
    static Specification<Product> nameStartsWith(String prefix) {
        return (root, query, cb) -> cb.like(root.get("name"), prefix + "%");
    }

    static Specification<Product> inCategory(Integer categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }
}
