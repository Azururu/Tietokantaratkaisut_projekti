package com.webstore.api.supplier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.webstore.api.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 30)
    private String phone;

    private String email;

    @OneToMany(mappedBy = "supplier")
    private List<SupplierAddress> addresses = new ArrayList<>();

    /**
     * N:M, inverse side of Product.suppliers (join table product_suppliers).
     * mappedBy: changes made only here are NOT saved; use Product.addSupplier /
     * removeSupplier, which update both sides. LAZY by default for @ManyToMany.
     */
    @ManyToMany(mappedBy = "suppliers")
    private Set<Product> products = new HashSet<>();

    protected Supplier() {
    }

    public Supplier(String name, String contactName, String phone, String email) {
        this.name = name;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
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

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<SupplierAddress> getAddresses() {
        return addresses;
    }

    public Set<Product> getProducts() {
        return products;
    }

    /** Same id-based equality as Product, needed because suppliers are kept in a Set. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Supplier other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Supplier.class.hashCode();
    }
}
