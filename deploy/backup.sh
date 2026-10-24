#!/bin/sh
# Daily dumps of the database, run by the backup service of the stack (see docker-compose.yml). Checks once an hour
# whether today's dump exists, so a restart neither skips a day nor dumps twice. Keeps the newest KEEP_DAILY daily
# dumps and, as a copy of the first dump of each ISO week, the newest KEEP_WEEKLY weekly ones.
#
# Only the host sees /backups. Getting the dumps off the host is the job of the host's own backup, see
# deploy/README.md.
set -eu

KEEP_DAILY=${KEEP_DAILY:-7}
KEEP_WEEKLY=${KEEP_WEEKLY:-4}
umask 077
mkdir -p /backups/daily /backups/weekly

# Keeps the newest $2 dumps in directory $1. The names sort by time.
prune() {
  ls -1 "$1"/*.dump 2>/dev/null | sort -r | tail -n +"$(($2 + 1))" | xargs -r rm -f
}

while true; do
  day=$(date -u +%Y-%m-%d)
  week=$(date -u +%G-W%V)
  if ! ls /backups/daily/bits-"$day"T*.dump >/dev/null 2>&1; then
    file=/backups/daily/bits-$(date -u +%Y-%m-%dT%H%M%SZ).dump
    # Written under a temporary name, so an interrupted dump never counts as the dump of the day.
    if pg_dump -Fc -f "$file.part"; then
      mv "$file.part" "$file"
      echo "dumped the database to $file"
      if [ ! -e /backups/weekly/bits-"$week".dump ]; then
        cp "$file" /backups/weekly/bits-"$week".dump
      fi
      prune /backups/daily "$KEEP_DAILY"
      prune /backups/weekly "$KEEP_WEEKLY"
    else
      rm -f "$file.part"
      echo "dumping the database failed, retrying in an hour" >&2
    fi
  fi
  sleep 3600
done
