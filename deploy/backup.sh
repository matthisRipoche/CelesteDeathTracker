#!/usr/bin/env bash
# Sauvegarde PostgreSQL compressée, conservée 14 jours.
# À planifier sur le serveur (voir deploy/README.md) :  0 3 * * * /opt/celeste/backup.sh
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p backups

file="backups/celeste-$(date +%F-%H%M).sql.gz"
docker compose -f docker-compose.prod.yml exec -T db pg_dump -U celeste celeste | gzip > "$file"

# Une sauvegarde vide (échec silencieux de pg_dump) ne doit pas passer pour valide
if [ ! -s "$file" ] || [ "$(stat -c %s "$file")" -lt 200 ]; then
    echo "Sauvegarde suspecte (trop petite) : $file" >&2
    rm -f "$file"
    exit 1
fi

find backups -name 'celeste-*.sql.gz' -mtime +14 -delete
echo "OK : $file ($(du -h "$file" | cut -f1))"
