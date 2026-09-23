package com.webstore.api.report;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSalesRepository extends JpaRepository<ProductSales, Integer> {

    /** Returns a List instead of a Page, so no extra COUNT(*) over the whole view is run. */
    List<ProductSales> findAllBy(Pageable pageable);
}
