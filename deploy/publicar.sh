#!/usr/bin/env bash
# Publica uma versão nova na VM: monta a imagem aqui, manda pela conexão SSH e reinicia a API.
# A VM não precisa do código nem do Maven, só do Docker. Uso: deploy/publicar.sh
set -euo pipefail

VM="${VM:-ubuntu@147.15.40.173}"
CHAVE_SSH="${CHAVE_SSH:-$HOME/.ssh/oracle_bot}"
PASTA="vigil"
cd "$(dirname "$0")/.."

# O .env (segredos) é criado uma vez na VM e nunca sai de lá; veja "Publicação" no README
if ! ssh -i "$CHAVE_SSH" "$VM" "test -f $PASTA/.env"; then
	echo "Falta o arquivo $PASTA/.env na VM (segredos). Veja a seção Publicação do README." >&2
	exit 1
fi

echo "Montando a imagem..."
docker build -q -t vigil:latest .

echo "Enviando para a VM (comprimida)..."
docker save vigil:latest | gzip | ssh -i "$CHAVE_SSH" "$VM" "gunzip | docker load -q"
ssh -i "$CHAVE_SSH" "$VM" "mkdir -p $PASTA/sites"
scp -q -i "$CHAVE_SSH" deploy/compose.yaml deploy/Caddyfile deploy/backup.sh "$VM:$PASTA/"

echo "Reiniciando..."
ssh -i "$CHAVE_SSH" "$VM" "cd $PASTA && docker compose up -d --remove-orphans && docker image prune -f >/dev/null"

echo "Conferindo a saúde da API..."
for _ in $(seq 1 30); do
	if ssh -i "$CHAVE_SSH" "$VM" "cd $PASTA && docker compose exec -T api wget -qO- localhost:8080/actuator/health" 2>/dev/null | grep -q UP; then
		echo "No ar."
		exit 0
	fi
	sleep 3
done
echo "A API não respondeu. Veja: ssh ... 'cd $PASTA && docker compose logs api --tail 50'" >&2
exit 1
