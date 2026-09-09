#!/usr/bin/env bash
# ============================================================
#  generate-jwt-keys.sh
#  Generates RSA-2048 keypair for JWT signing.
#  Keys are stored in secrets/ (gitignored) and mounted
#  into the container as read-only volumes at runtime.
#  Run once from the project root:  bash generate-jwt-keys.sh
# ============================================================

set -euo pipefail

SECRETS_DIR="secrets"
PRIVATE_KEY="$SECRETS_DIR/privateKey.pem"
PUBLIC_KEY="$SECRETS_DIR/publicKey.pem"

mkdir -p "$SECRETS_DIR"

echo "[jwt-keygen] Generating RSA-2048 private key..."
openssl genrsa -out "$PRIVATE_KEY" 2048

echo "[jwt-keygen] Extracting public key..."
openssl rsa -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"

chmod 644 "$PRIVATE_KEY"
chmod 644 "$PUBLIC_KEY"

echo "[jwt-keygen] Done."
echo "  Private key : $PRIVATE_KEY"
echo "  Public  key : $PUBLIC_KEY"
echo ""
echo "  Keys are in secrets/ (gitignored)."
echo "  docker-compose.yml mounts secrets/ into the container."
echo "  For AWS/master: store content in AWS Secrets Manager."
