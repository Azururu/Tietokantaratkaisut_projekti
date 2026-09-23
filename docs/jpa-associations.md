# One-to-many associations, cascade and lazy loading

How the 1:M relationships are modelled in JPA, what cascade does, and why everything is loaded lazily. All results below were measured against the full dataset with Hibernate's SQL logging on.

## The relationships

| One | Many | Why 1:M | Cascade | Fetch |
|---|---|---|---|---|
| `Customer` | `CustomerAddress` | A customer has several addresses (home, work, summer cottage); an address belongs to exactly one customer and means nothing without it | `ALL` + `orphanRemoval` | `LAZY` |
| `Customer` | `Order` | A customer places many orders over time | **none** | `LAZY` |
| `Order` | `OrderItem` | An order has one line per product | none | `LAZY` |
| `ProductCategory`, `Supplier` | `Product` | Many products per category and supplier | none (`@ManyToOne` side only) | `LAZY` |

The main example here is **customer → addresses**. It shows both sides of the mapping and the full cascade behaviour. **Customer → orders** is the deliberate contrast: the same kind of relationship, but without cascade.

## Mapping both sides

The foreign key `customeraddresses.customer_id` lives on the "many" side, so `CustomerAddress` **owns** the relationship with `@ManyToOne`, and `Customer` points back to it with `mappedBy`:

```java
// CustomerAddress.java — the owning side (has the foreign key column)
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "customer_id", nullable = false)
private Customer customer;
```

```java
// Customer.java — the inverse side
@OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<CustomerAddress> addresses = new ArrayList<>();

@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
private List<Order> orders = new ArrayList<>();

/** Keeps both sides of the relationship in sync. */
public void addAddress(CustomerAddress address) {
    addresses.add(address);
    address.setCustomer(this);
}

public void removeAddress(CustomerAddress address) {
    addresses.remove(address);
    address.setCustomer(null);
}
```

JPA only looks at the **owning** side when writing the foreign key. If code only did `customer.getAddresses().add(address)`, the address would be saved with `customer_id = NULL`. The helper methods set both sides, so the Java objects and the database always agree.

## Cascade

`cascade = CascadeType.ALL` means every operation on the customer (`persist`, `merge`, `remove`, …) is also applied to its addresses. `orphanRemoval = true` also deletes an address that is removed from the list.

### Saving: one `save()` stores the customer and its addresses

`POST /api/customers` builds the customer, adds the addresses and calls the repository's `save()` **once**. The addresses are never saved through their own repository:

```java
Customer customer = new Customer(request.firstName(), request.lastName(), request.email(), request.phone());
request.addresses().forEach(a -> customer.addAddress(a.toEntity()));
return CustomerResponse.from(customers.save(customer));
```

```sh
curl -X POST http://localhost:8080/api/customers -H "Content-Type: application/json" \
  -d '{"firstName":"Maija","lastName":"Meikäläinen","email":"maija@example.fi","phone":"040-1111111",
       "addresses":[{"streetAddress":"Mannerheimintie 1","postalCode":"00100","city":"Helsinki","country":"Suomi"},
                    {"streetAddress":"Hämeenkatu 5","postalCode":"33100","city":"Tampere","country":"Suomi"}]}'
```

SQL run by Hibernate:

```
insert into customers (email,first_name,last_name,phone) values (?,?,?,?)
insert into customeraddresses (city,country,customer_id,postal_code,street_address) values (?,?,?,?,?)
insert into customeraddresses (city,country,customer_id,postal_code,street_address) values (?,?,?,?,?)
```

Response `201 Created`: the customer (id 100001) with both addresses (ids 100001 and 100002).

### Adding to an existing customer

`POST /api/customers/{id}/addresses` loads the customer, adds the address to its list and calls `save(customer)`. The cascade inserts the address:

```
select … from customers where id=?
insert into customeraddresses (city,country,customer_id,postal_code,street_address) values (?,?,?,?,?)
```

**Observation.** The first version returned the id of the `CustomerAddress` object it had created, and crashed with a `NullPointerException` because the id was `null`, even though the row had been inserted. The customer was already *managed* (loaded in the same transaction), so `save()` ran `merge`. A cascaded merge of a new object inserts a **copy** of it and puts the copy in the managed list; the original object is never given an id. The fix is to read the saved address from the entity that `save()` returns. Because of the exception, the whole transaction was rolled back, and no half-saved data remained.

### Removing from the list: `orphanRemoval`

`DELETE /api/customers/{id}/addresses/{addressId}` only removes the address from the customer's list, and Hibernate deletes the row:

```java
customer.removeAddress(address);   // no repository.delete() call
```

```
select … from customers where id=?
select … from customeraddresses where customer_id=?
delete from customeraddresses where id=?
```

### Deleting the customer: the addresses go too

`DELETE /api/customers/{id}` calls `customers.delete(customer)`. Cascade `REMOVE` deletes the addresses first, then the customer:

```
select … from customers where id=?
select o1_0.id from orders o1_0 where o1_0.customer_id=? limit ?      -- has orders? (see below)
select … from customeraddresses where customer_id=?
delete from customeraddresses where id=?
delete from customeraddresses where id=?
delete from customers where id=?
```

Afterwards `SELECT COUNT(*) FROM customeraddresses WHERE customer_id = 100001` returns `0`, and `GET /api/customers/100001` returns `404`. Because `customers` is system-versioned, the deleted customer's versions are still available from `GET /api/customers/100001/history`.

### Why orders are *not* cascaded

`Customer.orders` has no cascade on purpose:
- **Saving:** orders are created only by the `place_order` stored procedure, which checks and reduces stock in one transaction. Cascading `persist` would let an order bypass that.
- **Deleting:** an order is a business and accounting record. Cascading `remove` would silently delete a customer's whole order history. The API user isn't even allowed to delete orders (see `sql/08_security.sql`).

So deleting a customer who has orders is refused before anything is deleted:

```
DELETE /api/customers/24  →  409 {"detail": "Customer 24 has orders and can't be deleted"}
```

The same applies to an address still used as an order's shipping address (`409 Address 24 is used by orders and can't be deleted`).

## LAZY or EAGER

**Choice: every association is `LAZY`**, including `@ManyToOne`, where JPA's default is `EAGER`. When a request needs related data, the query asks for it explicitly (`@EntityGraph` for order details), or the data is read inside the service's transaction.

**Why:** almost no request needs a customer's orders, and many requests load customers (order details, customer updates, deletes). With EAGER, all of those would also load data they throw away, and loading a list of customers would run extra queries for every single customer.

### Experiment

The same requests were run with both collections on `Customer` set to `LAZY`, then to `EAGER`.

**1. `customer.getOrders()` inside a transaction** (customer 24, who has 4 orders)

| LAZY | EAGER |
|---|---|
| `findById` → `select … from customers where id=?` | `findById` → `select … from customers left join customeraddresses …` **and** `select … from orders where customer_id=?` |
| `getOrders().size()` → **now** `select … from orders where customer_id=?` | `getOrders().size()` → no query, already loaded |
| Result: `{"customer": "Christine Davidson", "orders": 4}` | Same result |

With LAZY the orders query runs at the moment `getOrders()` is called. With EAGER it runs up front, whether the orders are used or not.

**2. `customer.getOrders()` after the transaction has closed**

| LAZY | EAGER |
|---|---|
| `LazyInitializationException: Cannot lazily initialize collection of role 'Customer.orders' with key '24' (no session)` | `{"orders": 4}` |

A lazy collection can only be loaded while the database session is open. This is why the API maps entities to response DTOs **inside** the service's `@Transactional` methods, and why `spring.jpa.open-in-view` is turned off: a missing fetch fails loudly in development instead of running hidden queries while the JSON is being written.

**3. `GET /api/customers/24`** (the response includes addresses, not orders)

| LAZY | EAGER |
|---|---|
| 2 queries: customer, then addresses when the response is built | 2 queries: customer joined with addresses, **plus all the customer's orders**, which are never used |

**4. Listing 100 customers, using only their names** (`customers.findAll(PageRequest.of(0, 100))`)

| | LAZY | EAGER |
|---|---|---|
| SQL queries | **2** (page + count) | **202** (page + count + 100 × addresses + 100 × orders) |
| Time (median of 5) | **26 ms** | 50 ms |

This is the **N+1 problem**: one query for the list, then one more per row for each EAGER collection. With the database on another server, every extra query also costs a network round trip, so the difference grows.

**5. `GET /api/orders/58`** (loads the order's customer through `@EntityGraph`)

| LAZY | EAGER |
|---|---|
| 1 query | 1 query |

Spring Data's `@EntityGraph` treats every attribute *not* listed in the graph as lazy, so it overrides the EAGER setting here. That's why this endpoint wasn't affected.

### When EAGER would be fine

EAGER suits small, always-needed data, for example a `@ManyToOne` to a lookup table with a handful of rows that every response shows. Even then, the same result is usually achieved with LAZY plus a fetch join or entity graph on the specific query that needs it, which keeps the choice per query rather than global.

## Try it

[http/customers.http](../http/customers.http) contains all the customer requests in order: create, read, update, add and remove an address, history, delete. Open it in VS Code with the **REST Client** extension and click *Send Request* above each one; later requests reuse the id of the customer created by the first. To see the SQL yourself, start the API with SQL logging:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.hibernate.SQL=DEBUG
```
