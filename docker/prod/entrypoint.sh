#!/usr/bin/env sh
# =============================================================
#  entrypoint.sh — Decodifica chaves JWT e inicia a API
#
#  Espera as seguintes env vars (injetadas via ECS Task Definition
#  a partir do AWS Secrets Manager):
#    JWT_PRIVATE_KEY_B64  — conteúdo do privateKey.pem em base64
#    JWT_PUBLIC_KEY_B64   — conteúdo do publicKey.pem em base64
#
#  Gera os arquivos PEM em /run/secrets/jwt/ e exporta as
#  variáveis JWT_PRIVATE_KEY_LOCATION e JWT_PUBLIC_KEY_LOCATION
#  que o application.yml (%master) lê via ${...}.
# =============================================================
set -eu

echo "[entrypoint] Iniciando rede-studio-api — perfil: ${QUARKUS_PROFILE:-master}"

# --- Decodificar chaves JWT ---
mkdir -p "${JWT_KEYS_DIR}"

if [ -z "${JWT_PRIVATE_KEY_B64:-}" ]; then
  echo "[entrypoint] ERRO: JWT_PRIVATE_KEY_B64 não definida" >&2
  exit 1
fi

if [ -z "${JWT_PUBLIC_KEY_B64:-}" ]; then
  echo "[entrypoint] ERRO: JWT_PUBLIC_KEY_B64 não definida" >&2
  exit 1
fi

printf '%s' "${JWT_PRIVATE_KEY_B64}" | base64 -d > "${JWT_KEYS_DIR}/privateKey.pem"
printf '%s' "${JWT_PUBLIC_KEY_B64}"  | base64 -d > "${JWT_KEYS_DIR}/publicKey.pem"
chmod 644 "${JWT_KEYS_DIR}/privateKey.pem"
chmod 644 "${JWT_KEYS_DIR}/publicKey.pem"

echo "[entrypoint] Chaves JWT escritas em ${JWT_KEYS_DIR}"

# --- Exportar localização das chaves para o Quarkus ---
export JWT_PRIVATE_KEY_LOCATION="file:${JWT_KEYS_DIR}/privateKey.pem"
export JWT_PUBLIC_KEY_LOCATION="file:${JWT_KEYS_DIR}/publicKey.pem"

# --- Iniciar a JVM diretamente ---
exec java ${JAVA_OPTS:-} -jar /deployments/quarkus-run.jar
