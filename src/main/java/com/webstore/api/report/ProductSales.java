package com.webstore.api.report;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only mapping of the v_product_sales view: sales per product, cancelled orders excluded. */
@Entity
@Immutable
@Table(name = "v_product_sales")
@JsonPropertyOrder({ "productId", "productName", "categoryName", "unitsSold", "orderCount", "revenue" })
public class ProductSales {

    @Id
    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "units_sold")
    private long unitsSold;

    @Column(name = "order_count")
    private long orderCount;

    private BigDecimal revenue;

    protected ProductSales() {
    }

    public Integer getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }
}
