# Backup plan

## What is backed up

| Item | How |
|---|---|
| Schema, data, views, stored procedures, trigger | `scripts/backup.sh` (logical dump with `mariadb-dump`) |
| Change history of `customers` (system-versioned) | Included via `--dump-history`; without it only current rows would be saved |
| Users, roles and grants | Not in the dump. Kept in git as [`sql/08_security.sql`](../sql/08_security.sql) and re-run after a restore |

## When

| Backup | Schedule | Kept |
|---|---|---|
| Daily full backup | Every night at 03:00, the quietest time for the store | 14 days |
| Monthly archive | Copy of the backup taken on the 1st of each month | 12 months |
| Before every schema change | Run manually as step 4 of the [schema change plan](schema-changes.md) | Until the change is confirmed |

This gives a **recovery point objective (RPO) of 24 hours**: at worst, one day of orders is lost. For a production store, also enable MariaDB's binary log (`log_bin`) so the time between the last backup and a failure can be replayed (point-in-time recovery), bringing the RPO down to minutes.

**Recovery time (RTO):** restoring the full database (1.1 million order lines) took about **9 seconds** in the restore test, plus restarting the API.

## How

`scripts/backup.sh` runs as the `webstore_backup` user, which can only read (see [`sql/08_security.sql`](../sql/08_security.sql)). Even if its password leaks, it can't change or delete data.

- `--single-transaction` takes a consistent snapshot without locking InnoDB tables, so the store keeps running during the backup.
- The output is compressed (about 32 MB) and checked with `gzip -t`. A damaged file is never saved as a backup.
- Backups older than 14 days are deleted automatically.

Scheduled with cron (macOS/Linux):

```cron
0 3 * * * cd /path/to/Tietokantaratkaisut_projekti && scripts/backup.sh >> backups/backup.log 2>&1
```

The password is read from the `BACKUP_PASSWORD` environment variable. On a server, set it in the cron environment or a file readable only by the backup account (`chmod 600`), never in the script or in git. On Windows, the same `mariadb-dump` command can be scheduled with Task Scheduler.

## Where

The **3-2-1 rule**: 3 copies of the data, on 2 different media, 1 of them off-site.

1. The live database.
2. `backups/` on the database server (excluded from git).
3. An off-site copy (another machine or cloud storage), copied after each backup.

The backups contain **personal data** (customer names, emails, phone numbers, addresses), so under GDPR they must be protected like the database itself: the off-site copy is encrypted, only the administrator has access, and old backups are deleted on schedule rather than kept forever.

## Who

The database administrator (the `webstore_admin` account) owns the backups: they check `backup.log` daily, run the monthly restore test, and are the only person with access to the off-site copies.

## Restoring

1. Stop the API so no new orders are written during the restore.
2. Restore the chosen backup (as an admin user):
   ```sh
   gunzip -c backups/webstore_2026-09-23_030000.sql.gz | mariadb
   ```
   The dump recreates the `webstore` database, its tables, views, procedures and trigger.
3. Re-apply users and grants: `mariadb -e "source sql/08_security.sql"`.
4. If binary logging is enabled, replay the changes since the backup with `mariadb-binlog`.
5. Start the API and check it: it validates the schema at startup, then check a few endpoints.

## Testing the restore

A backup that has never been restored is only a hope. `scripts/restore_test.sh` restores the newest backup into a separate `webstore_restore_test` database, compares it with the live database and drops it again. It runs **monthly** and **after every schema change**.

Result of the test run on 2026-09-23:

```
                                     live     restored
customers                          100000       100000  ok
customers (incl. history)          100001       100001  ok
products                             1000         1000  ok
orders                             200013       200013  ok
orderitems                        1099792      1099792  ok
orderlog                                7            7  ok
views                                   4            4  ok
procedures                              2            2  ok
triggers                                1            1  ok
v_order_summary query: ok
```
