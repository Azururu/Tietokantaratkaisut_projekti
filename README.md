# Tietokantaratkaisut_projekti

REST API backend for a small webstore, built with Spring Boot on top of the course's MariaDB `webstore` schema. Tables are mapped as JPA entities (Spring Data JPA / Hibernate). Business-critical operations run in the database itself: orders are placed and cancelled through stored procedures with transactions and row locking, and status changes are logged by a trigger.

## Contents

- [Requirements](#requirements)
- [1. Set up the database](#1-set-up-the-database)
- [2. Configure the API](#2-configure-the-api)
- [3. Run the API](#3-run-the-api)
- [4. Try it out](#4-try-it-out)
- [Running the SQL tests](#running-the-sql-tests)
- [Project structure](#project-structure)
- [Troubleshooting](#troubleshooting)

## Requirements

| Tool | Version | Check with |
|---|---|---|
| Java (JDK) | 21 or newer | `java -version` |
| MariaDB | 11.x | `mariadb --version` |

Maven doesn't need to be installed: the project includes the Maven wrapper (`mvnw` / `mvnw.cmd`), which downloads the right version on first run.

## 1. Set up the database

All commands below are run in the project root. They log in as `root`; replace it with your own admin user if needed. `-p` asks for the password.

The commands use `-e "source <file>"` instead of `< file`, because `<` doesn't work in PowerShell. They work the same on macOS, Linux and Windows.

**1.1 Create the database and load the data** (about 200 000 orders, 1.1 million order lines; takes under a minute):

```sh
mariadb -u root -p -e "CREATE DATABASE webstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mariadb -u root -p webstore -e "source project_database_utf8.sql"
```

This dump already contains the indexes, the `orderlog` table with its `orderstatuschange` trigger, and system versioning on `customers`.

> If `webstore` already exists from an earlier load, skip the `CREATE DATABASE` line. The dump drops and recreates every table, so re-running it resets the data.

**1.2 Install the stored procedures** (`place_order`, `cancel_order`):

```sh
mariadb -u root -p webstore -e "source sql/04_transactions.sql"
```

**1.3 Create the application user.** The API never connects as `root`. Open [sql/app_user.sql](sql/app_user.sql), replace `change_me` with a password of your own, then run:

```sh
mariadb -u root -p -e "source sql/app_user.sql"
```

This creates `webstore_app@localhost`, which can read and write data and execute procedures in `webstore`, but can't change the schema or touch other databases.

## 2. Configure the API

Copy the example config and put the password from step 1.3 in it:

```sh
cp .env.example .env          # macOS / Linux
copy .env.example .env        # Windows
```

```properties
DB_URL=jdbc:mariadb://localhost:3306/webstore
DB_USER=webstore_app
DB_PASSWORD=<your password>
```

`.env` is in `.gitignore`, so the password never ends up in git. The same values can also be given as environment variables instead.

## 3. Run the API

```sh
./mvnw spring-boot:run        # macOS / Linux
mvnw.cmd spring-boot:run      # Windows
```

In VS Code you can also use **Terminal → Run Task… → API: run**.

The API starts on **http://localhost:8080**. Stop it with `Ctrl+C`.

To build a standalone jar instead:

```sh
./mvnw package -DskipTests
java -jar target/webstore-api-0.0.1-SNAPSHOT.jar
```

## 4. Try it out

Open http://localhost:8080/api/products/2 in a browser, or use curl:

```sh
# Search products whose name starts with "Smart", most expensive first
curl "http://localhost:8080/api/products?q=Smart&sort=price&dir=desc&size=5"

# Place an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "shippingAddressId": 1, "items": [{"productId": 2, "quantity": 2}]}'

# Cancel it (use the id returned above)
curl -X POST http://localhost:8080/api/orders/<id>/cancel
```

Available endpoints so far:

| Method | Path | Description |
|---|---|---|
| GET | `/api/products` | Search products. Query parameters: `q` (name prefix), `categoryId`, `minPrice`, `maxPrice`, `sort` (`id`, `name`, `price`), `dir` (`asc`, `desc`), `page` (from 0), `size` (1–100, default 20) |
| GET | `/api/products/{id}` | One product with category and supplier name |
| GET | `/api/orders/{id}` | Order with customer, shipping address, order lines and total |
| POST | `/api/orders` | Place an order (runs the `place_order` transaction) |
| POST | `/api/orders/{id}/cancel` | Cancel a `NEW` order and return its items to stock |

Errors are returned as JSON ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details), for example:

```json
{ "status": 409, "title": "Conflict", "detail": "Insufficient stock", "instance": "/api/orders" }
```

## Running the SQL tests

[sql/04_transactions_test.sql](sql/04_transactions_test.sql) tests the transaction procedures directly in the database: a successful order, rollback on insufficient stock, invalid product and address, cancelling, and a manual `ROLLBACK`. Some calls are *expected* to fail, so run it with `--force`:

```sh
mariadb -u root -p --force -t webstore -e "source sql/04_transactions_test.sql"
```

The test leaves one cancelled order behind; stock quantities end up unchanged.

In VS Code: **Terminal → Run Task… → DB: run transaction tests (04)**. The VS Code tasks connect as your OS user without a password, which works with Homebrew MariaDB on macOS; on other setups use the command above.

## Project structure

```
├── project_database_utf8.sql        Full database dump (schema + data)
├── sql/
│   ├── 04_transactions.sql          place_order / cancel_order procedures
│   ├── 04_transactions_test.sql     Tests for the procedures
│   └── app_user.sql                 Least-privilege database user for the API
├── src/main/java/com/webstore/api/
│   ├── customer/                    Customer, CustomerAddress entities
│   ├── supplier/                    Supplier, SupplierAddress entities
│   ├── product/                     Product, ProductCategory entities; product endpoints
│   ├── order/                       Order, OrderItem, OrderLog entities; order endpoints,
│   │                                stored procedure calls (OrderProcedures)
│   └── common/                      Error handling, paging
├── src/main/resources/
│   └── application.properties       DB connection, connection pool and JPA settings
├── .env.example                     Template for local DB credentials
└── pom.xml                          Maven build (Spring Boot 4.1, Java 21)
```

## Troubleshooting

| Problem | Fix |
|---|---|
| `Access denied for user 'webstore_app'` | The password in `.env` doesn't match the database user's password. Note that `app_user.sql` uses `CREATE USER IF NOT EXISTS`, so re-running it does **not** change the password of an existing user. Set it directly: `ALTER USER 'webstore_app'@'localhost' IDENTIFIED BY '<password from .env>';` |
| `Schema-validation: missing table` / `missing column` at startup | Hibernate checks at startup that the entities match the database (`ddl-auto=validate`). The database was probably loaded from the original course dump instead of `project_database_utf8.sql`; redo step 1.1. |
| `PROCEDURE webstore.place_order does not exist` | Step 1.2 wasn't run, or was run against a different database. Reloading the dump (1.1) doesn't remove procedures, but creating a new database does. |
| `Unknown database 'webstore'` | Step 1.1 wasn't run, or the database has a different name. Fix `DB_URL` in `.env`. |
| `Port 8080 was already in use` | Another copy of the API is still running. Stop it, or start on another port with `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`. |
| `Access denied for user 'root'@'localhost'` on macOS (Homebrew) | Homebrew MariaDB lets your macOS user in without a password: drop `-u root -p` from the commands. |
