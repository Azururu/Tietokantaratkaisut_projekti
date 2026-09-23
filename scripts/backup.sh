#!/usr/bin/env bash
# Takes a compressed full backup of the webstore database and deletes old ones.
# See docs/backup-plan.md.
#
# Usage:  BACKUP_PASSWORD=... scripts/backup.sh
# Env:    DB_NAME (default webstore), BACKUP_USER (default webstore_backup),
#         BACKUP_DIR (default ./backups), KEEP_DAYS (default 14)
set -euo pipefail

DB_NAME="${DB_NAME:-webstore}"
BACKUP_USER="${BACKUP_USER:-webstore_backup}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"

: "${BACKUP_PASSWORD:?Set BACKUP_PASSWORD to the password of $BACKUP_USER}"

mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/${DB_NAME}_$(date +%Y-%m-%d_%H%M%S).sql.gz"

# --single-transaction  consistent snapshot without locking InnoDB tables,
#                       so the webstore keeps running during the backup
# --routines/--triggers procedures and the orderstatuschange trigger
# --dump-history        also the change history of system-versioned tables
MYSQL_PWD="$BACKUP_PASSWORD" mariadb-dump \
    --user="$BACKUP_USER" \
    --host=127.0.0.1 \
    --single-transaction \
    --routines \
    --triggers \
    --dump-history \
    --databases "$DB_NAME" \
  | gzip > "$file.tmp"

# Only keep the file if the archive is intact; a half-written backup is worse than none.
gzip -t "$file.tmp"
mv "$file.tmp" "$file"
echo "Backup written: $file ($(du -h "$file" | cut -f1))"

find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$KEEP_DAYS" -print -delete
