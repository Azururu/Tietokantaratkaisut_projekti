package com.webstore.api.report;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only mapping of the v_monthly_sales view: sales per month (YYYY-MM), cancelled orders excluded. */
@Entity
@Immutable
@Table(name = "v_monthly_sales")
@JsonPropertyOrder({ "month", "orderCount", "customerCount", "unitsSold", "revenue", "avgOrderValue" })
public class MonthlySales {

    @Id
    private String month;

    @Column(name = "order_count")
    private long orderCount;

    @Column(name = "customer_count")
    private long customerCount;

    @Column(name = "units_sold")
    private long unitsSold;

    private BigDecimal revenue;

    @Column(name = "avg_order_value")
    private BigDecimal avgOrderValue;

    protected MonthlySales() {
    }

    public String getMonth() {
        return month;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public long getCustomerCount() {
        return customerCount;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public BigDecimal getAvgOrderValue() {
        return avgOrderValue;
    }
}
