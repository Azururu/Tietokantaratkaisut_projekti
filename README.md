# Webstore API

REST API backend for a small webstore, built with **Spring Boot 4** and **MariaDB 11** on top of the course's `webstore` schema (about 100 000 customers, 200 000 orders and 1.1 million order lines).

The API serves a store front (product search, customer accounts and addresses, order history, placing and cancelling orders) and store management (product and category maintenance, shipping orders, sales reports). The database does the heavy lifting: orders are placed through **stored procedures with transactions and row locking**, status changes are logged by a **trigger**, lists and reports come from **views**, customer changes are kept with **system versioning**, and every database user has only the **permissions** its job needs.

## Contents

- [Features at a glance](#features-at-a-glance)
- [Architecture](#architecture)
- [API specification](#api-specification)
- [Getting started](#getting-started)
- [API reference](#api-reference)
- [Database design](#database-design)
- [Security](#security)
- [Backups and schema changes](#backups-and-schema-changes)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Troubleshooting](#troubleshooting)

## Features at a glance

| Technique | What it does in this project | Details |
|---|---|---|
| **Indexes** | Name search, price filter, email lookup and order history use indexes instead of scanning whole tables | [Indexes](#indexes) |
| **Views** | 4 views: product catalog, order summaries, product sales, monthly sales. The API reads lists and reports from them | [Views](#views) |
| **Transactions** | Placing and cancelling an order is all-or-nothing: order, order lines and stock change together or not at all | [Stored procedures and transactions](#stored-procedures-and-transactions) |
| **Locking** | `SELECT … FOR UPDATE` stops two buyers from buying the last item at the same time, and two requests from shipping or cancelling the same order | [Concurrency and locking](#concurrency-and-locking) |
| **Trigger** | Every order status change is written to `orderlog` automatically; the API shows it (`GET /api/orders/{id}/status-history`) | [Trigger](#trigger) |
| **Temporal data** | `customers` is system-versioned: every earlier version of a customer is kept, even after deletion, and the API returns it (`GET /api/customers/{id}/history`) | [Temporal data](#temporal-data) |
| **Users and roles** | 4 roles (admin, API, reporting, backup); the API can't change the schema or bypass the order procedures | [Security](#security) |
| **Backups** | Nightly backup script and an automatic restore test | [Backups](#backups-and-schema-changes) |
| **Schema changes** | Plan for changing the production schema without downtime | [Schema changes](#backups-and-schema-changes) |
| **JPA / Hibernate** | 14 entities (10 tables, 4 views); the mapping is checked against the real schema at startup | [JPA entities](#jpa-entities) |
| **1:M associations, cascade, lazy loading** | Customer → addresses with `cascade = ALL` + `orphanRemoval` (one `save()` stores a customer and its addresses); customer → orders deliberately without cascade; everything `LAZY`, measured against `EAGER` (2 vs 202 queries for a list of 100 customers) | [docs/jpa-associations.md](docs/jpa-associations.md) |

## Architecture

```
 HTTP client (browser, curl, front end)
        │  JSON
        ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Spring Boot API                                              │
 │  Controllers   → validate input, map errors to HTTP statuses │
 │  Services      → read-only transactions for queries          │
 │  Repositories  → Spring Data JPA (entities for tables/views) │
 │  OrderProcedures → calls the stored procedures               │
 └──────────────────────────────────────────────────────────────┘
        │  JDBC (connection pool of 10), user webstore_app
        ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ MariaDB: webstore                                            │
 │  tables (customers is system-versioned)                      │
 │  views      v_product_catalog, v_order_summary, …            │
 │  procedures place_order, cancel_order (transactions, locks)  │
 │  trigger    orderstatuschange → orderlog                     │
 └──────────────────────────────────────────────────────────────┘
```

- **Tables and views are JPA entities.** Views are mapped as read-only entities (`@Immutable`), for example `CatalogProduct` for `v_product_catalog`.
- **Responses are separate DTOs** (Java records), so the JSON shape doesn't change when the entities do, and lazy-loaded relations never leak into responses.
- **Schema validation at startup** (`spring.jpa.hibernate.ddl-auto=validate`): if a table, view or column the code expects is missing or has the wrong type, the API refuses to start instead of failing later on a request. Hibernate never changes the schema; the SQL scripts own it.
- **Orders are written only by the stored procedures**, never by JPA `save()`, so the stock check, stock update and order insert always happen in one database transaction.
- **All associations are `LAZY`.** Related data is loaded only when a request needs it, by an entity graph or inside the service transaction; `spring.jpa.open-in-view` is off, so nothing is loaded behind the scenes while JSON is written. Addresses are saved and deleted together with their customer through the cascade. See [docs/jpa-associations.md](docs/jpa-associations.md).

### JPA entities

Each table is a JPA entity class with annotations that map it to the table; each view is a read-only (`@Immutable`) entity.

| Entity | Table / view | Relationships |
|---|---|---|
| `ProductCategory` | `productcategories` | none (standalone) |
| `Supplier`, `SupplierAddress` | `suppliers`, `supplieraddresses` | supplier 1:M addresses |
| `Product` | `products` | M:1 category, M:1 supplier |
| `Customer`, `CustomerAddress` | `customers`, `customeraddresses` | customer 1:M addresses (cascade), customer 1:M orders |
| `Order`, `OrderItem` | `orders`, `orderitems` | order M:1 customer, M:1 address, 1:M items; item M:1 product; composite key `OrderItemId` (`@EmbeddedId` + `@MapsId`) |
| `OrderLog` | `orderlog` | read-only, written by the trigger |
| `CatalogProduct`, `OrderSummary`, `ProductSales`, `MonthlySales` | the four views | read-only |

The simplest one, `ProductCategory`, depends on no other entity:

```java
@Entity                                   // a JPA entity: one object = one row
@Table(name = "productcategories")        // table name differs from the class name
public class ProductCategory {

    @Id                                                   // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // AUTO_INCREMENT in MariaDB
    private Integer id;

    @Column(nullable = false, length = 100)               // NOT NULL VARCHAR(100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    protected ProductCategory() { }                       // JPA needs a no-argument constructor
    // constructor, getters and setters
}
```

It was tested through the [category endpoints](#get-apicategories): `save()` ran `insert into productcategories (description,name) values (?,?)` and returned the new id, the new row came back from `GET`, an update ran `update productcategories set description=?,name=? where id=?`, and a delete removed it (all against the real database). At startup, Hibernate also validates every entity against the schema (`ddl-auto=validate`).

## API specification

The API has five resources. The store front uses products, customers and orders; store management also maintains categories and products, ships orders, and reads reports.

| Resource | Data | GET | POST | PUT | DELETE |
|---|---|---|---|---|---|
| **Categories** `/api/categories` | id, name, description | list all, one | create | update | delete (not if it has products) |
| **Products** `/api/products` | id, name, description, price, stock, in stock, category, supplier | search and filter with paging, one | create | update | delete (not if it's on orders) |
| **Customers** `/api/customers` | id, name, email, phone, addresses | one with addresses, change history, order history | create with addresses, add an address | update | delete with addresses (not if they have orders), remove an address |
| **Orders** `/api/orders` | id, customer, dates, status, shipping address, lines (product, quantity, unit price), total | one with lines, status history | place, ship, cancel | – | – |
| **Reports** `/api/reports` | sales per product, sales per month | top products, monthly sales | – | – | – |

Orders are deliberately never updated or deleted freely: they're created only by the `place_order` transaction, and afterwards they only change status (`NEW` → `SHIPPED` or `NEW` → `CANCELLED`) through their own endpoints. The data behind a sale must stay intact.

Every operation is described in detail below in the [API reference](#api-reference).

## Getting started

### Requirements

| Tool | Version | Check with |
|---|---|---|
| Java (JDK) | 21 or newer | `java -version` |
| MariaDB | 11.x | `mariadb --version` |

Maven doesn't need to be installed: the project includes the Maven wrapper (`mvnw` / `mvnw.cmd`).

### 1. Set up the database

Run these in the project root. They log in as `root`; replace it with your own admin user if needed (`-p` asks for the password). The commands use `-e "source <file>"` rather than `< file` because `<` doesn't work in PowerShell, so they work the same on macOS, Linux and Windows.

**1.1 Create the database and load the data** (takes under a minute):

```sh
mariadb -u root -p -e "CREATE DATABASE webstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mariadb -u root -p webstore -e "source project_database_utf8.sql"
```

The dump contains the tables and data, the indexes, the `orderlog` table and its trigger, and system versioning on `customers`. Re-running it resets the data.

**1.2 Fix the character encoding, install the procedures and create the views:**

```sh
mariadb -u root -p webstore -e "source sql/fix_encoding.sql"
mariadb -u root -p webstore -e "source sql/04_transactions.sql"
mariadb -u root -p webstore -e "source sql/06_views.sql"
```

| Script | Does |
|---|---|
| `fix_encoding.sql` | Repairs Finnish characters that were corrupted in the dump (`Ty├Âkalut` → `Työkalut`, 48 rows). Only touches broken rows, so it's safe to run twice |
| `04_transactions.sql` | Creates the `place_order` and `cancel_order` procedures |
| `06_views.sql` | Creates the four views. The API won't start without them |

**1.3 Create the database users and roles.** Run the script, then give the API user a real password. Don't write passwords into the script: it's in git.

```sh
mariadb -u root -p -e "source sql/08_security.sql"
mariadb -u root -p -e "ALTER USER 'webstore_app'@'localhost' IDENTIFIED BY '<app password>'"
```

This creates four users with their own roles (see [Security](#security)). The API uses `webstore_app`; set the other users' passwords the same way when you need them. The script uses `CREATE USER IF NOT EXISTS`, so re-running it never changes an existing password.

### 2. Configure the API

```sh
cp .env.example .env          # macOS / Linux
copy .env.example .env        # Windows
```

Put the `webstore_app` password from step 1.3 into `.env`:

```properties
DB_URL=jdbc:mariadb://localhost:3306/webstore
DB_USER=webstore_app
DB_PASSWORD=<app password>
```

`.env` is ignored by git, so the password never gets committed. The same values can be given as environment variables instead.

### 3. Run the API

```sh
./mvnw spring-boot:run        # macOS / Linux
.\mvnw.cmd spring-boot:run    # Windows (PowerShell)
```

Or in VS Code: **Terminal → Run Task… → API: run**. The API is ready when the log shows `Started WebstoreApiApplication`, and it listens on **http://localhost:8080**. Stop it with `Ctrl+C`.

To build a standalone jar: `./mvnw package -DskipTests`, then `java -jar target/webstore-api-0.0.1-SNAPSHOT.jar`.

### 4. Try it

Open http://localhost:8080/api/products/456 in a browser (the root address `/` has no page and returns 404), or use curl:

```sh
curl "http://localhost:8080/api/products?q=Smart&sort=price&dir=desc&size=5"
curl "http://localhost:8080/api/customers/24/orders"
```

## API reference

### Conventions

- **Base URL:** `http://localhost:8080/api`
- **Format:** JSON in and out. Requests with a body need `Content-Type: application/json`.
- **Dates:** ISO-8601 local time, e.g. `2025-08-17T07:42:59`.
- **Money:** decimal numbers in euros, e.g. `546.08`. Order totals use the price at the time of the order (`unitPrice`), not the product's current price.
- **Paging:** list endpoints take `page` (from 0) and `size` (1–100, default 20) and return:
  ```json
  { "items": [ ... ], "page": 0, "size": 20, "total": 104 }
  ```

### Errors

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details:

```json
{ "status": 409, "title": "Conflict", "detail": "Insufficient stock", "instance": "/api/orders" }
```

Validation errors also list the failing fields:

```json
{
  "status": 400, "title": "Bad Request", "detail": "Validation failed", "instance": "/api/orders",
  "errors": { "customerId": "must not be null", "items[0].quantity": "must be greater than 0" }
}
```

| Status | When |
|---|---|
| `400 Bad Request` | Invalid input (missing field, negative price, quantity ≤ 0, page size over 100, bad month format), a category or supplier id that doesn't exist, or an address that belongs to another customer |
| `404 Not Found` | The category, product, customer, address or order doesn't exist |
| `409 Conflict` | Not enough stock; the order can't be shipped or cancelled because it isn't `NEW`; the item can't be deleted because other data refers to it (a category with products, a product on orders, a customer or address with orders) |

Business errors come straight from the stored procedures (`SIGNAL SQLSTATE '45000'`), so the API and the database enforce the same rules.

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | [`/api/categories`](#get-apicategories) | All categories |
| GET | [`/api/categories/{id}`](#get-apicategoriesid) | One category |
| POST | [`/api/categories`](#post-apicategories) | Create a category |
| PUT | [`/api/categories/{id}`](#put-apicategoriesid) | Update a category |
| DELETE | [`/api/categories/{id}`](#delete-apicategoriesid) | Delete a category |
| GET | [`/api/products`](#get-apiproducts) | Search and filter products |
| GET | [`/api/products/{id}`](#get-apiproductsid) | One product |
| POST | [`/api/products`](#post-apiproducts) | Create a product |
| PUT | [`/api/products/{id}`](#put-apiproductsid) | Update a product |
| DELETE | [`/api/products/{id}`](#delete-apiproductsid) | Delete a product |
| GET | [`/api/customers/{id}`](#get-apicustomersid) | One customer with addresses |
| POST | [`/api/customers`](#post-apicustomers) | Create a customer with addresses |
| PUT | [`/api/customers/{id}`](#put-apicustomersid) | Update a customer |
| DELETE | [`/api/customers/{id}`](#delete-apicustomersid) | Delete a customer and their addresses |
| POST | [`/api/customers/{id}/addresses`](#post-apicustomersidaddresses) | Add an address |
| DELETE | [`/api/customers/{id}/addresses/{addressId}`](#delete-apicustomersidaddressesaddressid) | Remove an address |
| GET | [`/api/customers/{id}/history`](#get-apicustomersidhistory) | All versions of a customer (temporal) |
| GET | [`/api/customers/{id}/orders`](#get-apicustomersidorders) | A customer's order history |
| GET | [`/api/orders/{id}`](#get-apiordersid) | One order with its lines |
| POST | [`/api/orders`](#post-apiorders) | Place an order |
| POST | [`/api/orders/{id}/ship`](#post-apiordersidship) | Mark an order shipped |
| POST | [`/api/orders/{id}/cancel`](#post-apiordersidcancel) | Cancel an order |
| GET | [`/api/orders/{id}/status-history`](#get-apiordersidstatus-history) | Status changes logged by the trigger |
| GET | [`/api/reports/top-products`](#get-apireportstop-products) | Best-selling products |
| GET | [`/api/reports/monthly-sales`](#get-apireportsmonthly-sales) | Sales per month |

---

#### `GET /api/categories`

All product categories, sorted by name. Not paged: there are only a handful.

```sh
curl "http://localhost:8080/api/categories"
```

```json
[
  { "id": 1, "name": "Elektroniikka", "description": "Sähkölaitteet, komponentit ja elektroniikkatuotteet" },
  { "id": 4, "name": "Elintarvikkeet", "description": "Ruoka-aineet ja elintarvikkeet" },
  { "id": 7, "name": "Huonekalut", "description": "Sisustustuotteet ja huonekalut koteihin ja toimistoihin" }
]
```

(The full response has all 10 categories.)

---

#### `GET /api/categories/{id}`

One category, in the same format as a list item. `404` if it doesn't exist.

---

#### `POST /api/categories`

Creates a category (the entity's `save()`).

| Body field | Type | Required | Rules |
|---|---|---|---|
| `name` | text | yes | Not blank, max 100 characters |
| `description` | text | no | |

```sh
curl -X POST "http://localhost:8080/api/categories" \
  -H "Content-Type: application/json" \
  -d '{"name": "Lemmikit", "description": "Lemmikkieläinten tarvikkeet"}'
```

Response: `201 Created` with a `Location` header:

```json
{ "id": 11, "name": "Lemmikit", "description": "Lemmikkieläinten tarvikkeet" }
```

---

#### `PUT /api/categories/{id}`

Updates a category's name and description. Same body as `POST`. Returns `200` with the updated category; `404` if not found.

---

#### `DELETE /api/categories/{id}`

Deletes a category. Returns `204 No Content`. A category that still has products returns `409` (`Category 11 has products and can't be deleted`); move or delete the products first.

---

#### `GET /api/products`

Searches the product catalog: the store front's product list. Reads the `v_product_catalog` view, which joins products with their category and supplier.

| Query parameter | Type | Default | Description |
|---|---|---|---|
| `q` | text | – | Name starts with this (uses the `idx_name` index) |
| `categoryId` | number | – | Only this category (1–10) |
| `minPrice`, `maxPrice` | number | – | Price range in euros (uses `idx_products_price`) |
| `inStock` | `true`/`false` | `false` | `true` = only products with stock left |
| `sort` | `id`, `name`, `price` | `id` | Sort field |
| `dir` | `asc`, `desc` | `asc` | Sort direction |
| `page`, `size` | number | `0`, `20` | Paging |

```sh
curl "http://localhost:8080/api/products?q=Smart&categoryId=4&inStock=true&sort=price&dir=desc&size=2"
```

```json
{
  "items": [
    {
      "id": 824,
      "name": "Smart Carrier 149",
      "description": "Man standard pass growth class.",
      "price": 888.53,
      "stockQuantity": 329,
      "inStock": true,
      "categoryId": 4,
      "categoryName": "Elintarvikkeet",
      "supplierId": 94,
      "supplierName": "Praha Office"
    },
    {
      "id": 63,
      "name": "Smart Link 447",
      "description": "Affect forward decision student form represent any.",
      "price": 159.01,
      "stockQuantity": 331,
      "inStock": true,
      "categoryId": 4,
      "categoryName": "Elintarvikkeet",
      "supplierId": 75,
      "supplierName": "Brno Machinery"
    }
  ],
  "page": 0,
  "size": 2,
  "total": 3
}
```

---

#### `GET /api/products/{id}`

One product, in the same format as a list item. `404` if it doesn't exist.

```sh
curl "http://localhost:8080/api/products/456"
```

```json
{
  "id": 456,
  "name": "Clever Lock 158",
  "description": "Traditional television usually offer official father occur everybody value can.",
  "price": 8.12,
  "stockQuantity": 219,
  "inStock": true,
  "categoryId": 5,
  "categoryName": "Työkalut",
  "supplierId": 59,
  "supplierName": "Hämeen Paperi"
}
```

---

#### `POST /api/products`

Creates a product. Returns it in the same format as [`GET /api/products/{id}`](#get-apiproductsid), including the category and supplier names.

| Body field | Type | Required | Rules |
|---|---|---|---|
| `name` | text | yes | Not blank, max 255 characters |
| `description` | text | no | |
| `price` | number | yes | ≥ 0, at most 2 decimals |
| `stockQuantity` | number | yes | ≥ 0 |
| `categoryId`, `supplierId` | number | no | Must exist (`400` otherwise) |

```sh
curl -X POST "http://localhost:8080/api/products" \
  -H "Content-Type: application/json" \
  -d '{"name": "Kissan raapimapuu", "description": "Korkea raapimapuu", "price": 49.90, "stockQuantity": 12, "categoryId": 11, "supplierId": 59}'
```

Response: `201 Created` with a `Location` header:

```json
{
  "id": 1001,
  "name": "Kissan raapimapuu",
  "description": "Korkea raapimapuu",
  "price": 49.90,
  "stockQuantity": 12,
  "inStock": true,
  "categoryId": 11,
  "categoryName": "Lemmikit",
  "supplierId": 59,
  "supplierName": "Hämeen Paperi"
}
```

---

#### `PUT /api/products/{id}`

Replaces a product's data. Same body as `POST`. Returns `200` with the updated product; `404` if not found. Orders already placed keep the price they were placed at, because order lines store their own `unitPrice`.

---

#### `DELETE /api/products/{id}`

Deletes a product. Returns `204 No Content`. A product that appears on any order returns `409` (`Product 2 is on orders and can't be deleted`): the order lines refer to it.

---

#### `GET /api/customers/{id}`

A customer with their addresses. The addresses are a lazily loaded 1:M collection, read inside the service transaction. `404` if the customer doesn't exist.

```sh
curl "http://localhost:8080/api/customers/24"
```

```json
{
  "id": 24,
  "firstName": "Christine",
  "lastName": "Davidson",
  "email": "andersonsandra24@example.org",
  "phone": "+1-506-712-4802x6247",
  "addresses": [
    { "id": 24, "streetAddress": "9500 Jessica Park", "postalCode": "94882", "city": "Port Steven", "country": "Yemen" }
  ]
}
```

---

#### `POST /api/customers`

Creates a customer, optionally with addresses. A single `save()` of the customer stores the addresses too, through `cascade = CascadeType.ALL` on `Customer.addresses`.

| Body field | Type | Required | Rules |
|---|---|---|---|
| `firstName`, `lastName` | text | yes | Not blank, max 100 characters |
| `email` | text | yes | Valid email address, max 255 characters |
| `phone` | text | no | Max 30 characters |
| `addresses` | array | no | Each: `streetAddress` (required), `postalCode`, `city` (required), `country` |

```sh
curl -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json" \
  -d '{"firstName": "Maija", "lastName": "Meikäläinen", "email": "maija@example.fi", "phone": "040-1111111",
       "addresses": [{"streetAddress": "Mannerheimintie 1", "postalCode": "00100", "city": "Helsinki", "country": "Suomi"},
                     {"streetAddress": "Hämeenkatu 5", "postalCode": "33100", "city": "Tampere", "country": "Suomi"}]}'
```

Response: `201 Created`, a `Location: /api/customers/100001` header, and the customer in the same format as [`GET /api/customers/{id}`](#get-apicustomersid):

```json
{
  "id": 100001,
  "firstName": "Maija",
  "lastName": "Meikäläinen",
  "email": "maija@example.fi",
  "phone": "040-1111111",
  "addresses": [
    { "id": 100001, "streetAddress": "Mannerheimintie 1", "postalCode": "00100", "city": "Helsinki", "country": "Suomi" },
    { "id": 100002, "streetAddress": "Hämeenkatu 5", "postalCode": "33100", "city": "Tampere", "country": "Suomi" }
  ]
}
```

---

#### `PUT /api/customers/{id}`

Updates a customer's name, email and phone. Same body as [`POST /api/customers`](#post-apicustomers); `addresses` is ignored here (use the address endpoints). Every update creates a new version in the system-versioned table, visible through [`/history`](#get-apicustomersidhistory). Returns `200` with the updated customer; `404` if not found.

```sh
curl -X PUT "http://localhost:8080/api/customers/100001" \
  -H "Content-Type: application/json" \
  -d '{"firstName": "Maija", "lastName": "Meikäläinen", "email": "maija@example.fi", "phone": "050-2222222"}'
```

---

#### `DELETE /api/customers/{id}`

Deletes a customer. Their addresses are deleted with them (cascade). Returns `204 No Content`.

A customer who has orders **can't** be deleted, even cancelled ones: orders are business records and are deliberately not cascaded. That returns `409`:

```json
{ "status": 409, "title": "Conflict", "detail": "Customer 24 has orders and can't be deleted", "instance": "/api/customers/24" }
```

The deleted customer's earlier versions remain available from [`/history`](#get-apicustomersidhistory).

---

#### `POST /api/customers/{id}/addresses`

Adds an address to a customer. The address is added to the customer's list and saved through the cascade when the customer is saved. Body: `streetAddress` (required), `postalCode`, `city` (required), `country`.

```sh
curl -X POST "http://localhost:8080/api/customers/100001/addresses" \
  -H "Content-Type: application/json" \
  -d '{"streetAddress": "Aleksanterinkatu 3", "postalCode": "90100", "city": "Oulu", "country": "Suomi"}'
```

Response: `201 Created` with the new address:

```json
{ "id": 100004, "streetAddress": "Aleksanterinkatu 3", "postalCode": "90100", "city": "Oulu", "country": "Suomi" }
```

---

#### `DELETE /api/customers/{id}/addresses/{addressId}`

Removes an address from a customer. The address is only removed from the customer's list, and `orphanRemoval` deletes the row. Returns `204 No Content`.

| Error | Status |
|---|---|
| The address doesn't belong to this customer | `404` |
| The address is an order's shipping address | `409` |

---

#### `GET /api/customers/{id}/history`

Every version of a customer, oldest first, read from the system-versioned `customers` table with `FOR SYSTEM_TIME ALL`. `validTo` is `null` for the current version. Also works for deleted customers: the last version then has an end time. `404` if the customer never existed.

```sh
curl "http://localhost:8080/api/customers/100001/history"
```

After one phone number change and a delete:

```json
[
  {
    "id": 100001, "firstName": "Maija", "lastName": "Meikäläinen", "email": "maija@example.fi",
    "phone": "040-1111111", "validFrom": "2026-09-23T17:21:01.05689", "validTo": "2026-09-23T17:21:13.833506"
  },
  {
    "id": 100001, "firstName": "Maija", "lastName": "Meikäläinen", "email": "maija@example.fi",
    "phone": "050-2222222", "validFrom": "2026-09-23T17:21:13.833506", "validTo": "2026-09-23T17:21:56.431205"
  }
]
```

---

#### `GET /api/customers/{id}/orders`

A customer's order history ("My orders"), newest first, with the number of lines, items and the total of each order. Reads the `v_order_summary` view. `404` if the customer doesn't exist.

| Query parameter | Type | Default | Description |
|---|---|---|---|
| `status` | `NEW`, `SHIPPED`, `CANCELLED` | – | Only orders with this status (case doesn't matter) |
| `page`, `size` | number | `0`, `20` | Paging |

```sh
curl "http://localhost:8080/api/customers/24/orders?size=2"
```

```json
{
  "items": [
    {
      "orderId": 199589,
      "customerId": 24,
      "customerName": "Christine Davidson",
      "orderDate": "2025-08-17T07:42:59",
      "deliveryDate": "2025-08-19T06:42:54",
      "status": "CANCELLED",
      "itemCount": 2,
      "totalQuantity": 8,
      "totalAmount": 4631.54
    },
    {
      "orderId": 137187,
      "customerId": 24,
      "customerName": "Christine Davidson",
      "orderDate": "2024-09-20T13:31:09",
      "deliveryDate": "2024-09-22T10:27:35",
      "status": "NEW",
      "itemCount": 5,
      "totalQuantity": 10,
      "totalAmount": 3186.51
    }
  ],
  "page": 0,
  "size": 2,
  "total": 4
}
```

---

#### `GET /api/orders/{id}`

One order with its customer, shipping address and order lines. Combines five tables: `orders`, `customers`, `customeraddresses`, `orderitems` and `products`, loaded in a single query. `shippingAddress` is `null` if the order has none. `404` if the order doesn't exist.

```sh
curl "http://localhost:8080/api/orders/58"
```

```json
{
  "id": 58,
  "customerId": 41473,
  "customerName": "Stephanie Mason",
  "orderDate": "2024-10-18T15:10:46",
  "deliveryDate": "2024-10-21T21:39:34",
  "status": "SHIPPED",
  "shippingAddress": {
    "id": 41473,
    "streetAddress": "6885 Mueller Courts Suite 285",
    "postalCode": "78198",
    "city": "East Amanda",
    "country": "Malawi"
  },
  "items": [
    { "productId": 320, "productName": "Shiny Clip 126", "quantity": 5, "unitPrice": 821.59, "lineTotal": 4107.95 },
    { "productId": 377, "productName": "Amazing Kit 664", "quantity": 2, "unitPrice": 748.46, "lineTotal": 1496.92 }
  ],
  "total": 5604.87
}
```

---

#### `POST /api/orders`

Places an order. Runs the `place_order` stored procedure, which in **one transaction** checks the customer, address and stock, creates the order and its lines, and reduces stock. Either all of it happens or none of it does. The price is always taken from the database, so the client can't set it.

| Body field | Type | Required | Description |
|---|---|---|---|
| `customerId` | number | yes | Who is ordering |
| `shippingAddressId` | number | no | Must be one of this customer's addresses |
| `items` | array | yes, at least 1 | `{ "productId": number, "quantity": number > 0 }`. The same product listed twice is merged |

```sh
curl -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"customerId": 12, "shippingAddressId": 12, "items": [{"productId": 456, "quantity": 2}, {"productId": 63, "quantity": 1}]}'
```

Response: `201 Created` with a `Location: /api/orders/200017` header and the new order, in the same format as [`GET /api/orders/{id}`](#get-apiordersid):

```json
{
  "id": 200017,
  "customerId": 12,
  "customerName": "Cesar Travis",
  "orderDate": "2026-09-23T17:04:58",
  "deliveryDate": null,
  "status": "NEW",
  "shippingAddress": {
    "id": 12,
    "streetAddress": "690 Wright Forges Suite 825",
    "postalCode": "02766",
    "city": "Gordonberg",
    "country": "Equatorial Guinea"
  },
  "items": [
    { "productId": 63, "productName": "Smart Link 447", "quantity": 1, "unitPrice": 159.01, "lineTotal": 159.01 },
    { "productId": 456, "productName": "Clever Lock 158", "quantity": 2, "unitPrice": 8.12, "lineTotal": 16.24 }
  ],
  "total": 175.25
}
```

| Error | Status |
|---|---|
| `Insufficient stock` (any product in the order) | `409` |
| `Product not found`, `Customer not found` | `404` |
| `Address does not belong to customer`, validation errors | `400` |

---

#### `POST /api/orders/{id}/ship`

Marks a `NEW` order as `SHIPPED` and sets its delivery date to now. No request body. The order row is locked (`SELECT … FOR UPDATE`) for the transaction, so a simultaneous cancel waits and then sees the new status. The trigger logs the change.

```sh
curl -X POST "http://localhost:8080/api/orders/200019/ship"
```

Response: `200 OK` with the order (same format as [`GET /api/orders/{id}`](#get-apiordersid)), now with `"status": "SHIPPED"` and a `deliveryDate`. Shipping an order that isn't `NEW` returns `409` (`Only NEW orders can be shipped`).

---

#### `POST /api/orders/{id}/cancel`

Cancels an order and returns its items to stock. Runs the `cancel_order` stored procedure in one transaction; the trigger logs the status change to `orderlog`. Only `NEW` orders can be cancelled. No request body.

```sh
curl -X POST "http://localhost:8080/api/orders/200017/cancel"
```

Response: `200 OK` with the order (same format as [`GET /api/orders/{id}`](#get-apiordersid)), now with `"status": "CANCELLED"`.

| Error | Status |
|---|---|
| `Only NEW orders can be cancelled` (e.g. cancelling twice) | `409` |
| `Order not found` | `404` |

---

#### `GET /api/orders/{id}/status-history`

The order's status changes, oldest first, as recorded in `orderlog` by the `orderstatuschange` trigger. Empty for an order whose status never changed. `404` if the order doesn't exist.

```sh
curl "http://localhost:8080/api/orders/200019/status-history"
```

```json
[
  { "logId": 13, "orderId": 200019, "oldStatus": "NEW", "newStatus": "SHIPPED", "timeOfChange": "2026-09-23T17:31:53", "name": "System" }
]
```

---

#### `GET /api/reports/top-products`

Best-selling products, for store management. Reads the `v_product_sales` view; cancelled orders don't count. It adds up all 1.1 million order lines, so it takes 2–3 seconds.

| Query parameter | Type | Default | Description |
|---|---|---|---|
| `by` | `units`, `revenue` | `units` | Rank by units sold or by revenue |
| `limit` | 1–100 | `10` | How many products |

```sh
curl "http://localhost:8080/api/reports/top-products?by=revenue&limit=2"
```

```json
[
  {
    "productId": 959,
    "productName": "Super Carrier 169",
    "categoryName": "Kirjat & toimisto",
    "unitsSold": 2346,
    "orderCount": 785,
    "revenue": 2333542.74
  },
  {
    "productId": 925,
    "productName": "Lucky Handle 876",
    "categoryName": "Elektroniikka",
    "unitsSold": 2340,
    "orderCount": 771,
    "revenue": 2322192.6
  }
]
```

---

#### `GET /api/reports/monthly-sales`

Sales per month: orders, distinct customers, units, revenue and average order value. Reads the `v_monthly_sales` view; cancelled orders don't count.

| Query parameter | Format | Default | Description |
|---|---|---|---|
| `from` | `YYYY-MM` | all | First month (inclusive) |
| `to` | `YYYY-MM` | all | Last month (inclusive) |

```sh
curl "http://localhost:8080/api/reports/monthly-sales?from=2025-01&to=2025-02"
```

```json
[
  { "month": "2025-01", "orderCount": 5586, "customerCount": 5435, "unitsSold": 92034, "revenue": 44781540.26, "avgOrderValue": 8016.75 },
  { "month": "2025-02", "orderCount": 5048, "customerCount": 4913, "unitsSold": 82725, "revenue": 39902425.41, "avgOrderValue": 7904.6 }
]
```

## Database design

### Tables

| Table | Rows | Contents |
|---|---|---|
| `customers` | 100 000 | Name, email, phone. **System-versioned** (history kept) |
| `customeraddresses` | 100 000 | Customer addresses |
| `products` | 1 000 | Name, description, price, stock, category, supplier |
| `productcategories` | 10 | Category names and descriptions |
| `suppliers`, `supplieraddresses` | 100 each | Suppliers and their addresses |
| `orders` | 200 000 | Customer, dates, shipping address, status (`NEW`, `SHIPPED`, `CANCELLED`) |
| `orderitems` | 1 100 000 | Order lines: product, quantity and the unit price at the time of ordering. Primary key `(order_id, product_id)` |
| `orderlog` | grows | Order status changes, written by the trigger |
| `contacts` | 500 000 | Separate contact list, not linked to other tables or used by the API |

### Indexes

Every primary key and foreign key has an index. On top of those, three indexes were added for the store's frequent searches:

| Index | Columns | Serves |
|---|---|---|
| `idx_name` | `products(name)` | Product search by name (`GET /api/products?q=`) |
| `idx_products_price` | `products(price)` | Price filters (`minPrice`, `maxPrice`) |
| `idx_email` | `customers(email)` | Finding a customer by email (for example at login) |

Checked with `EXPLAIN` on the queries the API runs:

| Query | Index used | Rows read |
|---|---|---|
| `v_product_catalog WHERE name LIKE 'Smart%'` | `idx_name` (range) | 20 of 1 000 |
| `v_product_catalog WHERE price BETWEEN 100 AND 110` | `idx_products_price` (range) | 12 of 1 000 |
| `customers WHERE email = …` | `idx_email` | 1 of 100 000 |
| `v_order_summary WHERE customer_id = 24` | `fk_order_customer` | 4 of 200 000 |
| `orderitems WHERE order_id = 58` | `PRIMARY` | 2 of 1.1 million |

Name search deliberately matches only the **start** of the name (`LIKE 'Smart%'`). A search anywhere in the name (`LIKE '%Smart%'`) can't use a B-tree index and would scan every product.

### Views

Defined in [sql/06_views.sql](sql/06_views.sql), with example queries in [sql/06_views_test.sql](sql/06_views_test.sql). All sums use the price at the time of ordering (`orderitems.unit_price`).

| View | Contents | Why a view | Used by |
|---|---|---|---|
| `v_product_catalog` | Products with category and supplier name, and an `in_stock` flag | Saves a 3-table join in every product query. Shows only the supplier's **name**, not their contact person, phone or email | `GET /api/products`, `GET /api/products/{id}` |
| `v_order_summary` | One row per order: customer name, number of lines and items, total | The totals need a join and `SUM`/`COUNT` over order lines; the view does it once | `GET /api/customers/{id}/orders` |
| `v_product_sales` | Units sold, number of orders and revenue per product (cancelled orders excluded) | The reporting user can read sales without access to any table | `GET /api/reports/top-products` |
| `v_monthly_sales` | Orders, customers, units, revenue and average order per month | Same as above | `GET /api/reports/monthly-sales` |

**Performance:** `v_order_summary` includes `customer_id` and `status` in its `GROUP BY` on purpose. That lets MariaDB push a `WHERE customer_id = …` condition *into* the view (condition pushdown), so it sums only that customer's orders instead of all 200 000; `EXPLAIN` confirms it reads 4 rows. Reports over all sales (`v_product_sales`) can't be narrowed this way and take 2–3 seconds.

### Stored procedures and transactions

Defined in [sql/04_transactions.sql](sql/04_transactions.sql). An order touches several tables, and a failure halfway through must not leave half an order behind.

**`place_order(customer_id, address_id, items_json, OUT order_id)`**
1. Checks that the order has items and that all quantities are positive.
2. `START TRANSACTION`.
3. Checks that the customer exists and that the address belongs to them.
4. **Locks** the ordered products' rows (`SELECT … FOR UPDATE`) and checks that they exist and have enough stock.
5. Inserts the order, then its lines with the current product price.
6. Reduces the stock.
7. `COMMIT`.

**`cancel_order(order_id)`**
1. `START TRANSACTION`.
2. **Locks** the order row and checks that it's `NEW`.
3. Sets the status to `CANCELLED`; the trigger logs the change.
4. Returns the items to stock.
5. `COMMIT`.

If anything fails, an `EXIT HANDLER` rolls back **everything** and passes the error on (`RESIGNAL`). Business rule violations are raised with `SIGNAL SQLSTATE '45000'`, and the API turns them into `400`/`404`/`409` responses.

Without these transactions, a failure could leave an order without lines, or stock reduced for an order that was never saved.

### Concurrency and locking

Two customers buy the last item at the same time. Without locking, both read "1 in stock", both succeed, and the stock ends up at −1.

`place_order` locks the product rows with `SELECT … FOR UPDATE` before checking stock. The second buyer's transaction **waits** until the first commits, then reads the updated stock and fails cleanly with `Insufficient stock`. `cancel_order` locks the order row the same way, so two simultaneous cancels can't both return the stock. Shipping does the same from Java: `OrderRepository.findForUpdateById` is annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)`, which Hibernate turns into `SELECT … FOR UPDATE`, so an order can't be shipped and cancelled at the same moment.

Tested with two parallel sessions: the second session waited about 2.5 seconds for the first one's lock, then continued with the correct stock ([sql/04_transactions_test.sql](sql/04_transactions_test.sql), test 6).

**Why row locks and not table locks:** InnoDB's automatic row locking is enough when the check and the change are one statement (`UPDATE … SET stock = stock - 1 WHERE stock >= 1`). An explicit lock is needed here because the procedure **reads, decides, then writes**. Locking only the affected rows means orders for other products don't wait at all; `LOCK TABLES` would block every order in the store.

The API calls the procedures **outside** Spring transactions: the procedures manage their own transaction, and MariaDB can't nest transactions.

### Trigger

`orderstatuschange` runs `AFTER UPDATE ON orders` and writes the old and new status and a timestamp to `orderlog`:

```sql
INSERT INTO orderlog (order_id, old_status, new_status) VALUES (NEW.id, OLD.status, NEW.status)
```

Every ship and cancel through the API therefore leaves an audit trail without any logging code in the API, and [`GET /api/orders/{id}/status-history`](#get-apiordersidstatus-history) returns it. The trigger runs inside the same transaction as the change, so if the change is rolled back, so is the log row. The API user can only **read** `orderlog`; only the trigger writes to it.

### Temporal data

`customers` is a **system-versioned** table (`WITH SYSTEM VERSIONING`). Whenever a customer is changed or deleted, MariaDB keeps the old version with the time period it was valid. Hidden `row_start` / `row_end` columns record that period, so normal queries only see the current data.

The API returns a customer's versions from [`GET /api/customers/{id}/history`](#get-apicustomersidhistory) (a native query, since `FOR SYSTEM_TIME` is MariaDB syntax). Directly in SQL, the history can be queried by time:

```sql
-- All versions of customer 1
SELECT id, phone, row_start, row_end FROM customers FOR SYSTEM_TIME ALL WHERE id = 1;

-- Customer 1 as they were at a given moment
SELECT id, phone FROM customers FOR SYSTEM_TIME AS OF TIMESTAMP '2026-09-23 16:00:00' WHERE id = 1;
```

```
+----+----------------------+----------------------------+----------------------------+
| id | phone                | row_start                  | row_end                    |
+----+----------------------+----------------------------+----------------------------+
|  1 | 001-789-824-7188x591 | 2026-09-23 14:49:26.532202 | 2026-09-23 16:55:10.401763 |
|  1 | 040-1234567          | 2026-09-23 16:55:10.401763 | 2106-02-07 08:28:15.999999 |
+----+----------------------+----------------------------+----------------------------+
```

The history is included in backups (`--dump-history`), and schema changes to this table need an extra setting (see [docs/schema-changes.md](docs/schema-changes.md)).

### Data fix

Some Finnish characters in the source dump were stored corrupted (`Työkalut` as `Ty├Âkalut`): UTF-8 text had been read as the Windows console code page CP850 and saved again. [sql/fix_encoding.sql](sql/fix_encoding.sql) reverses the conversion for the 48 affected rows in categories, suppliers and supplier addresses. It only touches rows with the telltale characters, so correct text is never changed.

## Security

### Database users and roles

Permissions are granted to **roles**, and each user gets one role ([sql/08_security.sql](sql/08_security.sql)). All users can only connect from `localhost`.

| User | Role | May | May not |
|---|---|---|---|
| `webstore_admin` | `webstore_admin` | Everything in the `webstore` database, including schema changes | Touch other databases, create users |
| `webstore_app` | `webstore_api` | Read, insert, update and delete products, categories, suppliers, customers and addresses; read and update orders; read order lines and `orderlog`; read the views; run `place_order` / `cancel_order` | Insert or delete orders directly (only through the procedures, which check stock in a transaction); write `orderlog` (only the trigger does); read `contacts`; change the schema |
| `webstore_report` | `webstore_reporting` | Read `v_product_sales`, `v_monthly_sales`, `v_product_catalog` | Read any table, or any view with personal data (customer names, emails, phones, addresses) |
| `webstore_backup` | `webstore_backup` | Read everything a dump needs (`SELECT`, `SHOW VIEW`, `TRIGGER`, `LOCK TABLES`, `SHOW CREATE ROUTINE`) | Change any data |

- **Procedures and views run with their creator's rights** (`SQL SECURITY DEFINER`). That's why the API only needs `EXECUTE` on the procedures to create orders, and the reporting user can read sales through a view without access to the tables behind it.
- **`REVOKE`:** an earlier setup gave `webstore_app` broad rights on the whole database. The script revokes those, leaving only the role's narrower rights.
- **Tested by logging in as each user.** For example, `webstore_app` gets `ERROR 1142` for `DELETE FROM orders`, `DROP TABLE products` and `SELECT * FROM contacts`, and `webstore_report` gets it for `SELECT email FROM customers`. The whole API was run and tested with only the `webstore_api` role's permissions.

### In the API

- **No credentials in git.** The password comes from `.env` (ignored by git) or environment variables; `application.properties` only has placeholders.
- **SQL injection:** all queries use bound parameters (JPA / Spring Data). Sort fields are checked against a whitelist, because column names can't be bound as parameters.
- **Input validation:** request bodies and query parameters are validated (`@Valid`, `@Min`, `@Max`, `@Pattern`). Invalid input gets a `400` listing the failing fields, never a database error.
- **Prices come from the database, not the client:** `place_order` reads the current price from `products`.
- **Errors don't leak internals:** failures are returned as problem details with a short message, never stack traces or SQL.

## Backups and schema changes

**Backups:** full plan in [docs/backup-plan.md](docs/backup-plan.md).
- [scripts/backup.sh](scripts/backup.sh) takes a nightly compressed dump as the read-only `webstore_backup` user. It doesn't lock tables, so the store keeps running, and it includes the customer change history.
- Backups are kept for 14 days, plus monthly archives, with an encrypted off-site copy (they contain personal data).
- [scripts/restore_test.sh](scripts/restore_test.sh) restores the newest backup into a test database and compares every table, view, procedure and trigger with the live one. A full restore takes about 9 seconds.

**Schema changes:** full plan in [docs/schema-changes.md](docs/schema-changes.md).
- Each change is a versioned migration script with a rollback script.
- Changes are tested on a restored copy first, with a backup right before running them for real.
- Changes are backward-compatible (expand, then contract).
- DDL runs online (`ALGORITHM=INSTANT` / `LOCK=NONE`), and system-versioned tables get the setting they need.
- Only `webstore_admin` can change the schema.

## Testing

**SQL tests**, run directly in the database:

| Test | Checks | Command |
|---|---|---|
| [sql/04_transactions_test.sql](sql/04_transactions_test.sql) | Successful order; rollback when one product is out of stock; invalid product and address; cancelling and cancelling twice; manual `ROLLBACK`; locking between two sessions (instructions) | `mariadb -u root -p --force -t webstore -e "source sql/04_transactions_test.sql"` |
| [sql/06_views_test.sql](sql/06_views_test.sql) | Example queries against every view, and the query plan showing condition pushdown | `mariadb -u root -p -t webstore -e "source sql/06_views_test.sql"` |
| [scripts/restore_test.sh](scripts/restore_test.sh) | The latest backup restores completely | `scripts/restore_test.sh` (macOS / Linux) |

The transaction test *expects* some calls to fail, which is why it runs with `--force`. It leaves one cancelled order behind; stock ends up unchanged.

In VS Code: **Terminal → Run Task… → DB: run transaction tests (04)** or **DB: run view tests (06)**. These tasks log in as your OS user without a password (works with Homebrew MariaDB on macOS); elsewhere use the commands above.

**API:** every endpoint and error case in the [API reference](#api-reference) was called against the full dataset; the examples above are real responses. The files in [http/](http/) run the main flows from VS Code with the **REST Client** extension: [catalog.http](http/catalog.http) (category and product create → update → delete), [customers.http](http/customers.http) (create with addresses → update → add and remove an address → history → delete) and [orders.http](http/orders.http) (place → ship → status history, place → cancel).

## Project structure

```
├── project_database_utf8.sql        Full database dump (schema + data)
├── sql/
│   ├── fix_encoding.sql             Repairs corrupted Finnish characters in the dump
│   ├── 04_transactions.sql          place_order / cancel_order procedures
│   ├── 04_transactions_test.sql     Tests for the procedures
│   ├── 06_views.sql                 Views
│   ├── 06_views_test.sql            Example queries against the views
│   └── 08_security.sql              Database users and roles
├── scripts/
│   ├── backup.sh                    Nightly backup
│   └── restore_test.sh              Restores a backup into a test database and compares
├── docs/
│   ├── backup-plan.md               Backup plan
│   ├── schema-changes.md            Schema change plan
│   └── jpa-associations.md          1:M associations, cascade, LAZY vs EAGER (measured)
├── http/
│   ├── catalog.http                 Category and product requests (VS Code REST Client)
│   ├── customers.http               Customer requests
│   └── orders.http                  Order requests: place, ship, cancel, status history
├── src/main/java/com/webstore/api/
│   ├── product/                     Product, ProductCategory entities; CatalogProduct (view);
│   │                                product and category endpoints
│   ├── order/                       Order, OrderItem, OrderLog entities; OrderSummary (view);
│   │                                order endpoints; OrderProcedures (stored procedure calls)
│   ├── customer/                    Customer, CustomerAddress entities (1:M with cascade);
│   │                                customer endpoints and history
│   ├── supplier/                    Supplier, SupplierAddress entities
│   ├── report/                      ProductSales, MonthlySales (views); report endpoints
│   └── common/                      Error handling, paging
├── src/main/resources/
│   └── application.properties       Database connection, connection pool, JPA settings
├── .env.example                     Template for local database credentials
└── pom.xml                          Maven build (Spring Boot 4.1, Java 21)
```

## Troubleshooting

| Problem | Fix |
|---|---|
| `Access denied for user 'webstore_app'` | The password in `.env` doesn't match the database user's. `08_security.sql` never changes an existing password; set it with `ALTER USER 'webstore_app'@'localhost' IDENTIFIED BY '<password from .env>';` |
| `Schema-validation: missing table [v_...]` at startup | The views are missing. Run `sql/06_views.sql` (step 1.2) |
| `Schema-validation: missing table` / `missing column` (other tables) | The database was loaded from the original course dump rather than `project_database_utf8.sql`. Redo step 1.1 |
| `PROCEDURE webstore.place_order does not exist` | Run `sql/04_transactions.sql` (step 1.2) against the right database |
| `execute command denied` or `SELECT command denied` for `webstore_app` | Run `sql/08_security.sql` (step 1.3). It grants on objects that must already exist, so run it after steps 1.1 and 1.2 |
| `Unknown database 'webstore'` | Step 1.1 wasn't run, or the database has another name. Fix `DB_URL` in `.env` |
| Names show up as `Ty├Âkalut`, `M├ñkel├ñ` | Run `sql/fix_encoding.sql` (step 1.2) |
| `Port 8080 was already in use` | Another copy of the API is running. Stop it, or use another port: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |
| `Whitelabel Error Page` (404) at `http://localhost:8080/` | Normal: there's no page at the root. Use the `/api/...` paths |
| `Access denied for user 'root'@'localhost'` on macOS (Homebrew) | Homebrew MariaDB lets your macOS user in without a password: drop `-u root -p` from the commands |
