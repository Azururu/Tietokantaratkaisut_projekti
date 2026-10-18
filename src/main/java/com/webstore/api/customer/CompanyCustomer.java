package com.webstore.api.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * A business customer: everything a Customer has (contact person's name, email,
 * phone, addresses, orders, profile) plus the company's own details.
 *
 * Mapped with the JOINED strategy set on Customer. The inherited fields are stored in
 * customers, the fields below in company_customers. @PrimaryKeyJoinColumn names the
 * column that is both this table's primary key and the foreign key to customers.id.
 *
 * Saving a CompanyCustomer inserts two rows (customers first, to get the id, then
 * company_customers); deleting one deletes both.
 */
@Entity
@Table(name = "company_customers")
@PrimaryKeyJoinColumn(name = "customer_id")
public class CompanyCustomer extends Customer {

    @Column(name = "company_name", nullable = false)
    private String companyName;

    /** Finnish business ID (Y-tunnus), format 1234567-8. Unique in the database. */
    @Column(name = "business_id", nullable = false, length = 9)
    private String businessId;

    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    @Column(name = "invoice_reference", length = 50)
    private String invoiceReference;

    protected CompanyCustomer() {
    }

    /** firstName, lastName, email and phone are the contact person's details (shared fields). */
    public CompanyCustomer(String firstName, String lastName, String email, String phone,
            String companyName, String businessId, String vatNumber, String invoiceReference) {
        super(firstName, lastName, email, phone);
        this.companyName = companyName;
        this.businessId = businessId;
        this.vatNumber = vatNumber;
        this.invoiceReference = invoiceReference;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }

    public String getInvoiceReference() {
        return invoiceReference;
    }

    public void setInvoiceReference(String invoiceReference) {
        this.invoiceReference = invoiceReference;
    }
}
