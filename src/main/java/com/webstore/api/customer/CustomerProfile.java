package com.webstore.api.customer;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 1:1 with Customer (sql/13_customer_profiles.sql): optional extra details that
 * ordering and address handling don't need.
 *
 * Shared primary key: customer_profiles.customer_id is both the primary key and the
 * foreign key to customers.id. @MapsId tells JPA to take this entity's id from the
 * customer, so there is no separate id column or sequence, and the database itself
 * guarantees that a customer can't have two profiles.
 *
 * This is the owning side (it has the foreign key column); Customer.profile is the
 * inverse side with mappedBy.
 */
@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

    /** Same value as the customer's id; filled in by @MapsId when the profile is saved. */
    @Id
    @Column(name = "customer_id")
    private Integer customerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** NOT NULL in the database; Java default matches the column default. */
    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = "fi";

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(name = "loyalty_points", nullable = false)
    private int loyaltyPoints;

    protected CustomerProfile() {
    }

    /** The customer is set by Customer.setProfile, which keeps both sides in sync. */
    public CustomerProfile(LocalDate birthDate, String preferredLanguage, boolean marketingConsent,
            int loyaltyPoints) {
        this.birthDate = birthDate;
        if (preferredLanguage != null) {
            this.preferredLanguage = preferredLanguage;
        }
        this.marketingConsent = marketingConsent;
        this.loyaltyPoints = loyaltyPoints;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public Customer getCustomer() {
        return customer;
    }

    void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public boolean isMarketingConsent() {
        return marketingConsent;
    }

    public void setMarketingConsent(boolean marketingConsent) {
        this.marketingConsent = marketingConsent;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public void setLoyaltyPoints(int loyaltyPoints) {
        this.loyaltyPoints = loyaltyPoints;
    }
}
