package com.webstore.api.customer;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Company customers (CompanyCustomer extends Customer). Reading, updating and deleting
 * a single one goes through /api/customers/{id}, which works for both types.
 */
@RestController
@RequestMapping("/api/company-customers")
public class CompanyCustomerController {

    private final CustomerService customers;

    public CompanyCustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return customers.listCompanies();
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CompanyCustomerRequest request) {
        CustomerResponse company = customers.createCompany(request);
        return ResponseEntity.created(URI.create("/api/customers/" + company.id())).body(company);
    }
}
