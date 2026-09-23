package com.webstore.api.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 255) String streetAddress,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String country) {

    CustomerAddress toEntity() {
        return new CustomerAddress(streetAddress, postalCode, city, country);
    }
}
