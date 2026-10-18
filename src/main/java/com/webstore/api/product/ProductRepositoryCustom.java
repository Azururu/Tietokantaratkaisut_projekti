package com.webstore.api.product;

/**
 * Repository methods written by hand with the Criteria API. Spring Data finds the
 * implementation (ProductRepositoryCustomImpl) by its name and adds these methods
 * to ProductRepository.
 */
public interface ProductRepositoryCustom {

    /**
     * Adds amount to the stock of every product that matches the given conditions.
     * Null conditions are left out. Returns the number of updated products.
     */
    int addStock(int amount, Integer categoryId, Integer supplierId, Integer stockBelow);
}
