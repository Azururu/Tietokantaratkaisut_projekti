package com.webstore.api.customer;

import java.util.List;

public record CustomerResponse(
        int id,
        String firstName,
        String lastName,
        String email,
        String phone,
        List<Address> addresses) {

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

    /** Reads customer.getAddresses(), so it must run inside a transaction (the list is LAZY). */
    static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
                c.getAddresses().stream().map(Address::from).toList());
    }
}
