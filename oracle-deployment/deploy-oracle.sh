#!/bin/bash
# =============================================================
#  deploy-oracle.sh — build + upload Object Storage + reload da API
#
#  Cloud-init ja sabe baixar quarkus-app.tar.gz do bucket e iniciar o
#  systemd (rede-studio.service) no boot -- entao o reload aqui e via
#  "terraform apply -replace=oci_core_instance.api" (recria a VM, ~2min).
#
#  NAO usa Bastion/SSH (sessoes MANAGED_SSH ficaram presas em CREATING
#  por varios minutos no E2.1.Micro -- provavel falta de CPU pro OCA
#  iniciar o plugin logo apos boot; investigar depois. Redeploy via
#  recriacao de VM funciona de forma confiavel e nao depende disso).
#
#  Uso: bash oracle-deployment/deploy-oracle.sh
# =============================================================
set -euo pipefail

REGION="${OCI_REGION:-sa-saopaulo-1}"
BUCKET="${ARTIFACTS_BUCKET:-rede-studio-artifacts}"
TF_DIR="$(dirname "$0")/terraform"

cd "$(dirname "$0")/.."

echo "=== 1/4 Build Quarkus (skip tests) ==="
MVN_CMD=$(command -v mvn || echo "./mvnw")
$MVN_CMD package -DskipTests -Dquarkus.profile=master

echo "=== 2/4 Empacotando quarkus-app/ ==="
rm -f /tmp/quarkus-app.tar.gz
tar -czf /tmp/quarkus-app.tar.gz -C target quarkus-app

ARTIFACT_SIZE=$(du -h /tmp/quarkus-app.tar.gz | awk '{print $1}')
echo "Artefato: /tmp/quarkus-app.tar.gz (${ARTIFACT_SIZE})"

echo "=== 3/4 Upload para Object Storage ==="
NAMESPACE=$(oci os ns get --region "$REGION" --auth api_key --raw-output --query data)
oci os object put --bucket-name "$BUCKET" --namespace "$NAMESPACE" \
  --file /tmp/quarkus-app.tar.gz --name quarkus-app.tar.gz --force \
  --region "$REGION" --auth api_key

echo "=== 4/4 Recriando a VM api para reler o artefato ==="
terraform -chdir="$TF_DIR" apply -replace=oci_core_instance.api -auto-approve

EIP=$(terraform -chdir="$TF_DIR" output -raw reserved_public_ip)
echo ""
echo "==============================================="
echo "Deploy concluido. Aguarde ~3-6min o cloud-init terminar."
echo "Testar: curl http://$EIP/q/health/live"
echo "==============================================="
