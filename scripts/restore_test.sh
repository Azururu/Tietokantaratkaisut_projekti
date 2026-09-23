#!/usr/bin/env bash
# Restores a backup into a temporary database and compares it with the live one.
# Proves that the backup can actually be restored. See docs/backup-plan.md.
#
# Usage:  scripts/restore_test.sh [backup-file]   (default: newest in ./backups)
# Runs as an admin that can create databases (the default mariadb login).
set -euo pipefail

DB_NAME="${DB_NAME:-webstore}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
TEST_DB="${DB_NAME}_restore_test"

file="${1:-$(ls -t "$BACKUP_DIR"/"${DB_NAME}"_*.sql.gz | head -1)}"
echo "Restoring $file into $TEST_DB ..."

mariadb -e "DROP DATABASE IF EXISTS $TEST_DB; CREATE DATABASE $TEST_DB"
# The dump contains 'CREATE DATABASE/USE webstore'; point it at the test database instead.
gunzip -c "$file" \
  | sed -e "s/^CREATE DATABASE .*\`$DB_NAME\`.*;$//" -e "s/^USE \`$DB_NAME\`;$/USE \`$TEST_DB\`;/" \
  | mariadb "$TEST_DB"

counts() {
    mariadb -N "$1" -e "
        SELECT 'customers',  COUNT(*) FROM customers UNION ALL
        SELECT 'customers (incl. history)', COUNT(*) FROM customers FOR SYSTEM_TIME ALL UNION ALL
        SELECT 'products',   COUNT(*) FROM products UNION ALL
        SELECT 'orders',     COUNT(*) FROM orders UNION ALL
        SELECT 'orderitems', COUNT(*) FROM orderitems UNION ALL
        SELECT 'orderlog',   COUNT(*) FROM orderlog UNION ALL
        SELECT 'views',      COUNT(*) FROM information_schema.views    WHERE table_schema = DATABASE() UNION ALL
        SELECT 'procedures', COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE() UNION ALL
        SELECT 'triggers',   COUNT(*) FROM information_schema.triggers WHERE trigger_schema = DATABASE()"
}

echo
printf "%-28s %12s %12s\n" "" "live" "restored"
paste <(counts "$DB_NAME") <(counts "$TEST_DB") | while IFS=$'\t' read -r name live _ restored; do
    mark=$([ "$live" = "$restored" ] && echo "ok" || echo "DIFFERENT")
    printf "%-28s %12s %12s  %s\n" "$name" "$live" "$restored" "$mark"
done

# A smoke test: the restored procedures and views must work, not just exist.
mariadb "$TEST_DB" -e "SELECT COUNT(*) INTO @n FROM v_order_summary WHERE customer_id = 1" \
    && echo "v_order_summary query: ok"

mariadb -e "DROP DATABASE $TEST_DB"
echo "Test database dropped."
