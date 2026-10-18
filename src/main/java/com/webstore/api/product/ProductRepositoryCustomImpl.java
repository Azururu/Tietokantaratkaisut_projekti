package com.webstore.api.product;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Bulk stock update built with the Criteria API (CriteriaUpdate). The WHERE clause is
 * assembled at runtime from the conditions that were given, so one method covers every
 * combination; with JPQL each combination would need its own query string.
 */
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int addStock(int amount, Integer categoryId, Integer supplierId, Integer stockBelow) {
        // 1. CriteriaBuilder: creates the query and its conditions
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        // 2. An UPDATE instead of a query: createCriteriaUpdate (not createQuery)
        CriteriaUpdate<Product> update = cb.createCriteriaUpdate(Product.class);
        // 3. Root: the entity being updated, "UPDATE Product p"
        Root<Product> p = update.from(Product.class);

        // 4a. SET stockQuantity = stockQuantity + amount
        update.set(p.<Integer>get("stockQuantity"), cb.sum(p.get("stockQuantity"), amount));

        // 4b. WHERE: only the conditions that were given
        List<Predicate> conditions = new ArrayList<>();
        if (categoryId != null) {
            conditions.add(cb.equal(p.get("category").get("id"), categoryId));
        }
        if (supplierId != null) {
            conditions.add(cb.equal(p.get("supplier").get("id"), supplierId));
        }
        if (stockBelow != null) {
            conditions.add(cb.lessThan(p.get("stockQuantity"), stockBelow));
        }
        update.where(conditions.toArray(new Predicate[0]));

        // 5. Run it: executeUpdate (not getResultList), returns the number of rows
        return entityManager.createQuery(update).executeUpdate();
    }
}
