package com.webstore.api.customer;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webstore.api.common.ConflictException;
import com.webstore.api.common.NotFoundException;
import com.webstore.api.order.OrderRepository;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final OrderRepository orders;

    public CustomerService(CustomerRepository customers, OrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(int id) {
        return CustomerResponse.from(find(id));
    }

    /**
     * One save() call stores the customer and all its addresses: the addresses are
     * never saved through their own repository, the cascade on Customer.addresses does it.
     */
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer(request.firstName(), request.lastName(), request.email(), request.phone());
        if (request.addresses() != null) {
            request.addresses().forEach(a -> customer.addAddress(a.toEntity()));
        }
        return CustomerResponse.from(customers.save(customer));
    }

    /** Each update creates a new version in the system-versioned customers table. */
    public CustomerResponse update(int id, CustomerRequest request) {
        Customer customer = find(id);
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        return CustomerResponse.from(customer);
    }

    /**
     * Deletes the customer and, through the cascade, all its addresses. Orders are not
     * cascaded, so a customer with orders can't be deleted: the order history must stay.
     */
    public void delete(int id) {
        Customer customer = find(id);
        if (orders.existsByCustomerId(id)) {
            throw new ConflictException("Customer " + id + " has orders and can't be deleted");
        }
        customers.delete(customer);
    }

    public CustomerResponse.Address addAddress(int customerId, AddressRequest request) {
        Customer customer = find(customerId);
        customer.addAddress(request.toEntity());
        // Saving the customer cascades the new address to the database. The customer is
        // already managed, so save() merges, and the cascaded merge stores a managed copy
        // of the address: the saved address (with its id) is the one in the returned list.
        Customer saved = customers.saveAndFlush(customer);
        List<CustomerAddress> addresses = saved.getAddresses();
        return CustomerResponse.Address.from(addresses.get(addresses.size() - 1));
    }

    /** Removing the address from the customer's list deletes it (orphanRemoval). */
    public void removeAddress(int customerId, int addressId) {
        Customer customer = find(customerId);
        CustomerAddress address = customer.getAddresses().stream()
                .filter(a -> a.getId() == addressId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Address " + addressId + " not found for customer " + customerId));
        if (orders.existsByShippingAddressId(addressId)) {
            throw new ConflictException("Address " + addressId + " is used by orders and can't be deleted");
        }
        customer.removeAddress(address);
    }

    @Transactional(readOnly = true)
    public List<CustomerVersion> history(int id) {
        List<CustomerVersion> versions = customers.findHistory(id);
        if (versions.isEmpty()) {
            throw new NotFoundException("Customer " + id + " not found");
        }
        return versions;
    }

    private Customer find(int id) {
        return customers.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }
}
