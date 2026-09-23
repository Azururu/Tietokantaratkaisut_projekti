package com.webstore.api.customer;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One version of a customer from the system-versioned customers table.
 * validTo is null for the current version.
 */
@JsonPropertyOrder({ "id", "firstName", "lastName", "email", "phone", "validFrom", "validTo" })
public interface CustomerVersion {

    Integer getId();

    String getFirstName();

    String getLastName();

    String getEmail();

    String getPhone();

    LocalDateTime getValidFrom();

    LocalDateTime getValidTo();
}
