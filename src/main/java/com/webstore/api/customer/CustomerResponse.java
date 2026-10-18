package com.webstore.api.customer;

import java.util.List;

/**
 * type is "Customer" or "CompanyCustomer". Because of the JOINED inheritance,
 * loading a customer returns the right subclass, and company customers also get
 * their company details (null for plain customers).
 */
public record CustomerResponse(
        int id,
        String type,
        String firstName,
        String lastName,
        String email,
        String phone,
        List<Address> addresses,
        Company company) {

    public record Address(
            int id,
            String streetAddress,
            String postalCode,
            String city,
            String country) {

        static Address from(CustomerAddress a) {
            return new Address(a.getId(), a.getStreetAddress(), a.getPostalCode(), a.getCity(), a.getCountry());
        }
    }

    public record Company(
            String companyName,
            String businessId,
            String vatNumber,
            String invoiceReference) {

        static Company from(CompanyCustomer c) {
            return new Company(c.getCompanyName(), c.getBusinessId(), c.getVatNumber(), c.getInvoiceReference());
        }
    }

    /** Reads customer.getAddresses(), so it must run inside a transaction (the list is LAZY). */
    static CustomerResponse from(Customer c) {
        Company company = c instanceof CompanyCustomer cc ? Company.from(cc) : null;
        String type = company != null ? "CompanyCustomer" : "Customer";
        return new CustomerResponse(c.getId(), type, c.getFirstName(), c.getLastName(),
                c.getEmail(), c.getPhone(), c.getAddresses().stream().map(Address::from).toList(), company);
    }
}
