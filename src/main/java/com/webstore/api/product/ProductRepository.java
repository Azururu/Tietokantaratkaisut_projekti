package com.webstore.api.product;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Writable access to the products table. Reads for the API go through {@link CatalogProductRepository}. */
public interface ProductRepository extends JpaRepository<Product, Integer>, ProductRepositoryCustom {

    boolean existsByCategoryId(Integer categoryId);

    /** True if the product appears on any order line. */
    @Query("select count(oi) > 0 from OrderItem oi where oi.product.id = :productId")
    boolean isOrdered(@Param("productId") int productId);

    /**
     * N:M: every product the supplier can deliver, through the product_suppliers join
     * table (Product.suppliers), not only those where it is the primary supplier
     * (products.supplier_id). Hibernate turns the join into:
     * products join product_suppliers on product_id join suppliers on supplier_id.
     */
    @Query("select p from Product p join p.suppliers s where s.id = :supplierId")
    List<Product> findBySupplierId(@Param("supplierId") Integer supplierId);

    /**
     * Multiplies the price of every product in the category in one UPDATE statement,
     * instead of loading and saving the products one by one.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.price = p.price * :factor where p.category.id = :categoryId")
    int updatePriceByCategory(@Param("factor") BigDecimal factor, @Param("categoryId") int categoryId);
}