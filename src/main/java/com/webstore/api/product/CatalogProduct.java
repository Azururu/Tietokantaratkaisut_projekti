package com.webstore.api.product;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;
import org.hibernate.type.NumericBooleanConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only mapping of the v_product_catalog view (sql/06_views.sql): products with
 * category and supplier names, without the supplier's contact details.
 */
@Entity
@Immutable
@Table(name = "v_product_catalog")
public class CatalogProduct {

    @Id
    private Integer id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal price;

    @Column(name = "stock_quantity")
    private int stockQuantity;

    /** The view returns 0/1 (stock_quantity > 0); converted to a Java boolean. */
    @Column(name = "in_stock")
    @Convert(converter = NumericBooleanConverter.class)
    private boolean inStock;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "supplier_id")
    private Integer supplierId;

    @Column(name = "supplier_name")
    private String supplierName;

    /** products.version, for optimistic locking: the client sends it back when updating. */
    private int version;

    protected CatalogProduct() {
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public boolean isInStock() {
        return inStock;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public int getVersion() {
        return version;
    }
}
