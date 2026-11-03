#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AUTH_DIR="$ROOT/auth-service/src/main/resources/jwt"

if [[ -f "$AUTH_DIR/private.pem" && "${1:-}" != "--force" ]]; then
  echo "As chaves já existem em $AUTH_DIR. Use --force para gerar um par novo."
  exit 0
fi

mkdir -p "$AUTH_DIR"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$AUTH_DIR/private.pem" 2>/dev/null
chmod 600 "$AUTH_DIR/private.pem"
openssl pkey -in "$AUTH_DIR/private.pem" -pubout -out "$AUTH_DIR/public.pem"

for service in order-service scheduling-service; do
  mkdir -p "$ROOT/$service/src/main/resources/jwt"
  cp "$AUTH_DIR/public.pem" "$ROOT/$service/src/main/resources/jwt/public.pem"
done

echo "Chaves geradas: privada no auth-service e pública nos três serviços."
