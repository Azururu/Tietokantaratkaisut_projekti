package com.webstore.api.product;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CatalogProductRepository
        extends JpaRepository<CatalogProduct, Integer>, JpaSpecificationExecutor<CatalogProduct> {

    /** Prefix match (LIKE 'x%') so the idx_name index can be used. */
    static Specification<CatalogProduct> nameStartsWith(String prefix) {
        return (root, query, cb) -> cb.like(root.get("name"), prefix + "%");
    }

    static Specification<CatalogProduct> inCategory(Integer categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    static Specification<CatalogProduct> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    static Specification<CatalogProduct> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    static Specification<CatalogProduct> inStockOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("inStock"));
    }
}
