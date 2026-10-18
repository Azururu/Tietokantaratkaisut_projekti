package com.webstore.api.customer;

import java.time.LocalDate;

/** customerId is both the profile's id and the customer's id (shared primary key, @MapsId). */
public record ProfileResponse(
        int customerId,
        LocalDate birthDate,
        String preferredLanguage,
        boolean marketingConsent,
        int loyaltyPoints) {

    static ProfileResponse from(CustomerProfile p) {
        return new ProfileResponse(p.getCustomerId(), p.getBirthDate(), p.getPreferredLanguage(),
                p.isMarketingConsent(), p.getLoyaltyPoints());
    }
}
