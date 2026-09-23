package com.webstore.api.report;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlySalesRepository extends JpaRepository<MonthlySales, String> {

    /** Months are 'YYYY-MM' strings, so they sort and compare correctly as text. */
    List<MonthlySales> findByMonthBetweenOrderByMonth(String from, String to);
}
