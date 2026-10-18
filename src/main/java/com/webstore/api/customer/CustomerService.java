package com.webstore.api.customer;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webstore.api.common.ConflictException;
import com.webstore.api.common.NotFoundException;
import com.webstore.api.order.OrderRepository;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final CompanyCustomerRepository companyCustomers;
    private final OrderRepository orders;

    public CustomerService(CustomerRepository customers, CompanyCustomerRepository companyCustomers,
            OrderRepository orders) {
        this.customers = customers;
        this.companyCustomers = companyCustomers;
        this.orders = orders;
    }

    /** Polymorphic: returns a CompanyCustomer (with company details) when the customer is one. */
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
     * Deletes the customer and, through the cascade, all its addresses and its profile.
     * For a company customer, Hibernate deletes the company_customers row first, then
     * the customers row (JOINED inheritance). Orders are not cascaded, so a customer
     * with orders can't be deleted: the order history must stay.
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

    // ---------------------------------------------------------------------
    // 1:1 Customer <-> CustomerProfile
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(int customerId) {
        return ProfileResponse.from(findProfile(find(customerId)));
    }

    /**
     * Creates the profile, or updates the existing one. A new profile is saved through
     * the cascade on Customer.profile: no repository call, the flush at the end of the
     * transaction inserts it, and @MapsId gives it the customer's id.
     * An existing profile is updated in place; replacing it with a new object would
     * try to insert a second row with the same primary key.
     */
    public ProfileResponse putProfile(int customerId, ProfileRequest request) {
        Customer customer = find(customerId);
        CustomerProfile profile = customer.getProfile();
        if (profile == null) {
            profile = new CustomerProfile(request.birthDate(), request.languageOrDefault(),
                    request.marketingConsent(), request.loyaltyPoints());
            customer.setProfile(profile);
        } else {
            profile.setBirthDate(request.birthDate());
            profile.setPreferredLanguage(request.languageOrDefault());
            profile.setMarketingConsent(request.marketingConsent());
            profile.setLoyaltyPoints(request.loyaltyPoints());
        }
        customers.flush();   // write now so the response shows the stored id
        return ProfileResponse.from(profile);
    }

    /** Detaching the profile from the customer deletes it (orphanRemoval); the customer stays. */
    public void deleteProfile(int customerId) {
        Customer customer = find(customerId);
        findProfile(customer);
        customer.setProfile(null);
    }

    // ---------------------------------------------------------------------
    // Inheritance: CompanyCustomer extends Customer (JOINED)
    // ---------------------------------------------------------------------

    /** One save() inserts two rows: customers (shared fields), then company_customers. */
    public CustomerResponse createCompany(CompanyCustomerRequest request) {
        if (companyCustomers.findByBusinessId(request.businessId()).isPresent()) {
            throw new ConflictException("Business ID " + request.businessId() + " is already in use");
        }
        CompanyCustomer company = new CompanyCustomer(request.firstName(), request.lastName(), request.email(),
                request.phone(), request.companyName(), request.businessId(), request.vatNumber(),
                request.invoiceReference());
        if (request.addresses() != null) {
            request.addresses().forEach(a -> company.addAddress(a.toEntity()));
        }
        return CustomerResponse.from(customers.save(company));
    }

    /** Company customers only: an inner join of customers and company_customers. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> listCompanies() {
        return companyCustomers.findAll(Sort.by("id")).stream().map(CustomerResponse::from).toList();
    }

    private Customer find(int id) {
        return customers.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    private CustomerProfile findProfile(Customer customer) {
        CustomerProfile profile = customer.getProfile();
        if (profile == null) {
            throw new NotFoundException("Customer " + customer.getId() + " has no profile");
        }
        return profile;
    }
}
