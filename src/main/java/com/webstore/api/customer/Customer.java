package com.webstore.api.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.webstore.api.order.Order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * The customers table is system-versioned (WITH SYSTEM VERSIONING). Its hidden
 * row_start / row_end columns are managed by MariaDB and are not mapped here.
 *
 * Inheritance root: CompanyCustomer extends Customer (sql/14_company_customers.sql).
 * Strategy JOINED: the fields shared by all customers stay in customers, and each
 * subclass has its own table whose primary key is also a foreign key to customers.id.
 * Every customer query therefore left-joins company_customers, and Hibernate returns a
 * CompanyCustomer when the join finds a row, otherwise a plain Customer.
 *
 * Chosen over SINGLE_TABLE (would add nullable company columns and a type column to
 * the large system-versioned customers table) and TABLE_PER_CLASS (orders and
 * addresses could no longer reference all customers through one foreign key, and
 * IDENTITY ids can't be used). The cost of JOINED is one extra join.
 */
@Entity
@Table(name = "customers")
@Inheritance(strategy = InheritanceType.JOINED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(length = 30)
    private String phone;

    /**
     * 1:M, owned by CustomerAddress.customer (the foreign key side).
     * Addresses belong to the customer: saving the customer saves new addresses,
     * deleting it deletes them (cascade ALL), and an address removed from this list
     * is deleted (orphanRemoval). LAZY: loaded only when the addresses are needed.
     */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerAddress> addresses = new ArrayList<>();

    /**
     * 1:M without cascade: orders are business records created only by the
     * place_order procedure, and must never be saved or deleted through a customer.
     * LAZY: a customer can have many orders, and most requests don't need them.
     */
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

    /**
     * 1:1, inverse side of CustomerProfile.customer (which owns the shared primary key).
     * The profile belongs entirely to the customer, like the addresses: saving the
     * customer saves the profile, deleting the customer deletes it (cascade ALL), and
     * setProfile(null) deletes it (orphanRemoval). Optional: null if there is no profile.
     *
     * LAZY is only a hint here. On the inverse side Hibernate can't know whether a
     * profile exists without querying customer_profiles, so it can't hand out a lazy
     * proxy and loads the profile together with the customer.
     */
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CustomerProfile profile;

    protected Customer() {
    }

    public Customer(String firstName, String lastName, String email, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
    }

    public Integer getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<CustomerAddress> getAddresses() {
        return addresses;
    }

    /** Keeps both sides of the relationship in sync. */
    public void addAddress(CustomerAddress address) {
        addresses.add(address);
        address.setCustomer(this);
    }

    public void removeAddress(CustomerAddress address) {
        addresses.remove(address);
        address.setCustomer(null);
    }

    public List<Order> getOrders() {
        return orders;
    }

    public CustomerProfile getProfile() {
        return profile;
    }

    /**
     * Sets or removes (null) the profile and keeps both sides in sync.
     * To change an existing profile, update its fields instead of passing a new
     * object: the new one would have the same primary key (the customer's id), and
     * Hibernate inserts it before deleting the old one, which fails as a duplicate key.
     */
    public void setProfile(CustomerProfile profile) {
        this.profile = profile;
        if (profile != null) {
            profile.setCustomer(this);
        }
    }

        /**
     * Lifecycle event: runs automatically before every INSERT (@PrePersist) and
     * UPDATE (@PreUpdate) of a customer, whichever code path saves it.
     * "  Maija@Example.FI " and "maija@example.fi" are the same address; storing one
     * form keeps lookups through the idx_email index reliable.
     */
    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
