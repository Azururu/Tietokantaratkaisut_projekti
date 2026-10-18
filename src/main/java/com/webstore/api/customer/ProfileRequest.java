package com.webstore.api.customer;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/customers/{id}/profile. Creates the profile if the customer has
 * none, otherwise replaces its values. preferredLanguage defaults to "fi".
 */
public record ProfileRequest(
        @Past LocalDate birthDate,
        @Size(max = 5) String preferredLanguage,
        boolean marketingConsent,
        @Min(0) int loyaltyPoints) {

    String languageOrDefault() {
        return preferredLanguage == null || preferredLanguage.isBlank() ? "fi" : preferredLanguage;
    }
}
