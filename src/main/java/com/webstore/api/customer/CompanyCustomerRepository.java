package com.webstore.api.customer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Company customers only. Queries here join customers with company_customers (inner
 * join), so plain customers are never returned. CustomerRepository, in contrast,
 * returns both types (polymorphic queries).
 */
public interface CompanyCustomerRepository extends JpaRepository<CompanyCustomer, Integer> {

    /** Derived query on a subclass field: ... where company_customers.business_id = ? */
    Optional<CompanyCustomer> findByBusinessId(String businessId);
}
