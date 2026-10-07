#!/usr/bin/env bash
# Backup diário do banco do Vigil, rodado pelo cron da VM (veja "Publicação" no README).
# Guarda os últimos 7 dias em ~/vigil/backups, comprimidos (poucos MB cada).
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p backups
arquivo="backups/vigil-$(date +%F).sql.gz"
# Escreve num temporário e só renomeia no fim: um backup pela metade nunca passa por bom
docker compose exec -T banco pg_dump -U vigil vigil | gzip > "$arquivo.tmp"
mv "$arquivo.tmp" "$arquivo"
ls -1t backups/vigil-*.sql.gz | tail -n +8 | xargs -r rm --
