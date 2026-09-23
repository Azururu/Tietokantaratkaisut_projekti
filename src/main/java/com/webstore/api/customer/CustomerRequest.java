package com.webstore.api.customer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/customers. Addresses are optional; they are saved together with
 * the customer through the cascade on Customer.addresses. PUT uses the same body but
 * ignores addresses, which have their own endpoints.
 */
public record CustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        List<@Valid AddressRequest> addresses) {
}
