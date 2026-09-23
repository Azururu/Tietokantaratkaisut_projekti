# Schema change plan

How to change the production database's structure (for example, add a column) without breaking the running store.

## Process

| # | Step | Who | Details |
|---|---|---|---|
| 1 | **Write a migration script** | Developer | A numbered SQL file in `sql/` (like `04_…`, `06_…`), versioned in git, plus a **rollback script** that undoes it. In a larger project, a migration tool such as Flyway or Liquibase would track which scripts have run. |
| 2 | **Review** | Other team member | Is the change backward-compatible (see below)? Will it lock a large table? |
| 3 | **Test on a copy** | Developer | Restore the latest backup into a test database (`scripts/restore_test.sh` shows how), run the migration there, measure how long it takes, then start the API against the copy and run the SQL tests. The API validates the schema at startup (`ddl-auto=validate`), so a mismatch between the code and the database is caught immediately. |
| 4 | **Back up** | Admin | `scripts/backup.sh` right before the change. |
| 5 | **Announce** | Admin | Tell the team and, if the store may be affected, customers. Pick a low-traffic time (night). |
| 6 | **Run the migration** | Admin | As `webstore_admin`. The API's own user **can't** change the schema (no `ALTER`/`DROP`, see `sql/08_security.sql`), so a bug or SQL injection in the API can't alter tables either. |
| 7 | **Verify** | Admin + developer | API starts, endpoints respond, error logs are clean. |
| 8 | **Roll back if needed** | Admin | Run the rollback script; as a last resort, restore the backup from step 4. |

## Rules for changes that don't break production

**1. Backward-compatible first (expand, then contract).** The old version of the API must keep working while the change is rolled out.
- Adding a column: make it `NULL` or give it a `DEFAULT`, so existing `INSERT`s still work.
- Renaming or removing a column is done in stages: add the new column → deploy code that uses it → copy the data → only later, in a separate change, drop the old column.
- Never drop or rename something the running code still uses.

**2. Online DDL: never lock a big table by accident.** State the algorithm explicitly. If MariaDB can't do the change without copying or locking the table, it refuses immediately instead of blocking the store for minutes:
```sql
ALTER TABLE orders ADD COLUMN tracking_code VARCHAR(50) NULL, ALGORITHM=INSTANT;
-- or, for changes that can't be instant:
ALTER TABLE orders ADD INDEX idx_status (status), ALGORITHM=INPLACE, LOCK=NONE;
```
Tested on the 200 000-row `orders` table: the `INSTANT` column add completed immediately.

**3. System-versioned tables need an explicit opt-in.** `customers` keeps its change history (`WITH SYSTEM VERSIONING`), and MariaDB refuses to alter it by default:
```
ERROR 4119: Not allowed for system-versioned `webstore`.`customers`.
Change @@system_versioning_alter_history to proceed with ALTER.
```
The migration must set this for its own session, and the history rows are kept:
```sql
SET SESSION system_versioning_alter_history = KEEP;
ALTER TABLE customers ADD COLUMN marketing_consent BOOLEAN NOT NULL DEFAULT FALSE, ALGORITHM=INSTANT;
```

**4. Views and procedures depend on the tables.** After changing a table, check the views (`sql/06_views.sql`) and procedures (`sql/04_transactions.sql`) that use it, and run their tests (`sql/*_test.sql`).

**5. Update grants.** A new table isn't accessible to the API or reporting roles until it's granted in `sql/08_security.sql`.

## Example: adding a shipment tracking code to orders

Migration (`sql/NN_add_tracking_code.sql`):
```sql
ALTER TABLE orders ADD COLUMN tracking_code VARCHAR(50) NULL, ALGORITHM=INSTANT;
```

Rollback (`sql/NN_add_tracking_code_rollback.sql`):
```sql
ALTER TABLE orders DROP COLUMN tracking_code, ALGORITHM=INSTANT;
```

Order of deployment:
1. Run the migration. The running API ignores the new column, so nothing breaks.
2. Deploy the API version whose `Order` entity maps `trackingCode`.
3. If step 2 fails, roll back the code; the extra nullable column does no harm, so the database needn't be touched.
