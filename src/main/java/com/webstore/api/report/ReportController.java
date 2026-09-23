package com.webstore.api.report;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Sales reports read from the v_product_sales and v_monthly_sales views. These
 * aggregate all order lines, so they take a few seconds on the full dataset.
 */
@RestController
@RequestMapping("/api/reports")
@Transactional(readOnly = true)
public class ReportController {

    private static final String MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";

    private final ProductSalesRepository productSales;
    private final MonthlySalesRepository monthlySales;

    public ReportController(ProductSalesRepository productSales, MonthlySalesRepository monthlySales) {
        this.productSales = productSales;
        this.monthlySales = monthlySales;
    }

    @GetMapping("/top-products")
    public List<ProductSales> topProducts(
            @RequestParam(defaultValue = "units") @Pattern(regexp = "units|revenue") String by,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        Sort sort = Sort.by(Sort.Direction.DESC, "units".equals(by) ? "unitsSold" : "revenue");
        return productSales.findAllBy(PageRequest.of(0, limit, sort));
    }

    @GetMapping("/monthly-sales")
    public List<MonthlySales> monthlySales(
            @RequestParam(defaultValue = "0000-01") @Pattern(regexp = MONTH_PATTERN) String from,
            @RequestParam(defaultValue = "9999-12") @Pattern(regexp = MONTH_PATTERN) String to) {
        return monthlySales.findByMonthBetweenOrderByMonth(from, to);
    }
}
