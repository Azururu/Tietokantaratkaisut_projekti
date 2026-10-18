package com.webstore.api.customer;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Profiles are saved and deleted through Customer (cascade), but they can be read
 * directly: the profile's id is the customer's id, so findById(customerId) finds a
 * customer's profile without loading the customer.
 */
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Integer> {
}
