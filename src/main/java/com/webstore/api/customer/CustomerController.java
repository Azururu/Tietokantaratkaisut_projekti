package com.webstore.api.customer;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable int id) {
        return customers.get(id);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customers.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + customer.id())).body(customer);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable int id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        customers.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<CustomerResponse.Address> addAddress(
            @PathVariable int id, @Valid @RequestBody AddressRequest request) {
        CustomerResponse.Address address = customers.addAddress(id, request);
        return ResponseEntity.created(URI.create("/api/customers/" + id)).body(address);
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<Void> removeAddress(@PathVariable int id, @PathVariable int addressId) {
        customers.removeAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<CustomerVersion> history(@PathVariable int id) {
        return customers.history(id);
    }

    // 1:1 profile: the URL has no profile id, the customer's id identifies it (shared primary key).

    @GetMapping("/{id}/profile")
    public ProfileResponse getProfile(@PathVariable int id) {
        return customers.getProfile(id);
    }

    @PutMapping("/{id}/profile")
    public ProfileResponse putProfile(@PathVariable int id, @Valid @RequestBody ProfileRequest request) {
        return customers.putProfile(id, request);
    }

    @DeleteMapping("/{id}/profile")
    public ResponseEntity<Void> deleteProfile(@PathVariable int id) {
        customers.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }
}
