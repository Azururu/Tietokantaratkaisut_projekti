package com.webstore.api.customer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/company-customers. The first fields are the ones every customer
 * has (here: the contact person), the rest are CompanyCustomer's own fields.
 */
public record CompanyCustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        List<@Valid AddressRequest> addresses,
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Pattern(regexp = "\\d{7}-\\d", message = "must be a business ID like 1234567-8") String businessId,
        @Size(max = 20) String vatNumber,
        @Size(max = 50) String invoiceReference) {
}
