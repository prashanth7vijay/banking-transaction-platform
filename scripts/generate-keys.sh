#!/usr/bin/env bash
# Generates the RSA key pair used to sign (private) and verify (public) RS256 JWTs.
# Run once before the first `docker compose up`. Keys are gitignored - never commit them.
set -euo pipefail

KEYS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/keys"
mkdir -p "$KEYS_DIR"

if [[ -f "$KEYS_DIR/private_key.pem" ]]; then
  echo "Keys already exist at $KEYS_DIR - skipping generation."
  exit 0
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/private_key.pem"
openssl rsa -pubout -in "$KEYS_DIR/private_key.pem" -out "$KEYS_DIR/public_key.pem"

echo "RSA key pair generated at $KEYS_DIR"
