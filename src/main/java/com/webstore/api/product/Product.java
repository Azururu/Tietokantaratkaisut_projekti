package com.webstore.api.product;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import com.webstore.api.supplier.Supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    /**
     * Primary supplier (products.supplier_id). Kept alongside the N:M relationship
     * below so that v_product_catalog and the existing API keep working.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /**
     * N:M: every supplier this product can be ordered from (sql/12_product_suppliers.sql).
     * Product is the owning side: the @JoinTable is defined here, and only changes to
     * this set are written to product_suppliers. Supplier.products is the inverse side.
     *
     * No cascade: a product never creates or deletes suppliers. Deleting a product
     * only removes its rows in the join table, which Hibernate does automatically.
     * Set instead of List: removing one supplier deletes just that one join row,
     * whereas with a List Hibernate deletes and re-inserts all of them.
     * LAZY: suppliers are loaded only when needed.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_suppliers",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "supplier_id"))
    private Set<Supplier> suppliers = new HashSet<>();

    protected Product() {
    }

    public Product(String name, String description, BigDecimal price, int stockQuantity,
            ProductCategory category, Supplier supplier) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.supplier = supplier;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public Set<Supplier> getSuppliers() {
        return suppliers;
    }

    /** Keeps both sides of the N:M relationship in sync. */
    public void addSupplier(Supplier supplier) {
        suppliers.add(supplier);
        supplier.getProducts().add(this);
    }

    public void removeSupplier(Supplier supplier) {
        suppliers.remove(supplier);
        supplier.getProducts().remove(this);
    }

    /**
     * Entities in a Set need equals/hashCode. Based on the database id: two objects
     * are equal only if both are saved and have the same id. hashCode is constant per
     * class so it doesn't change when a new entity gets its id on save.
     * instanceof + getId() (not getClass() / the field) so Hibernate proxies compare correctly.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Product.class.hashCode();
    }
}
