#!/bin/bash
# =============================================================
#  deploy-oracle.sh — build + upload Object Storage + restart via SSH
#
#  Deploy de CODIGO nao recria a VM -- builda, sobe o artefato novo pro
#  Object Storage e reinicia o systemd (rede-studio.service) via SSH,
#  usando uma sessao Bastion Port Forwarding (o cloud-init ja deixa
#  o serviço configurado, so falta trocar o JAR e reiniciar).
#
#  Isso substitui a versao antiga (terraform apply -replace=oci_core_
#  instance.api a cada deploy): recriar a VM inteira so pra trocar um
#  JAR tambem forcava o Certbot a reemitir o certificado TLS a cada
#  deploy, e isso ja bateu no rate limit do Let's Encrypt (5 emissoes
#  por dominio a cada 168h) em 2026-09. Terraform so deve rodar quando
#  ha mudanca de INFRA de verdade (ver .github/workflows/ci.yml, que
#  roda "terraform apply" -- sem -replace, deixando o proprio Terraform
#  decidir o que precisa mudar -- so quando arquivos de
#  oracle-deployment/terraform/ mudam no push).
#
#  Uso: bash oracle-deployment/deploy-oracle.sh
#  Requer uma chave SSH ja autorizada na VM (ops_ssh_public_key ou
#  ci_ssh_public_key, ver terraform/variables.tf) -- por padrao usa
#  ~/.ssh/id_ed25519_ci_rede_studio, ajustavel via SSH_PRIVATE_KEY_FILE.
# =============================================================
set -euo pipefail

REGION="${OCI_REGION:-sa-saopaulo-1}"
BUCKET="${ARTIFACTS_BUCKET:-rede-studio-artifacts}"
TF_DIR="$(dirname "$0")/terraform"
SSH_KEY="${SSH_PRIVATE_KEY_FILE:-$HOME/.ssh/id_ed25519_ci_rede_studio}"

cd "$(dirname "$0")/.."

echo "=== 1/5 Build Quarkus (skip tests) ==="
MVN_CMD=$(command -v mvn || echo "./mvnw")
$MVN_CMD package -DskipTests -Dquarkus.profile=master

echo "=== 2/5 Empacotando quarkus-app/ ==="
rm -f /tmp/quarkus-app.tar.gz
tar -czf /tmp/quarkus-app.tar.gz -C target quarkus-app

ARTIFACT_SIZE=$(du -h /tmp/quarkus-app.tar.gz | awk '{print $1}')
echo "Artefato: /tmp/quarkus-app.tar.gz (${ARTIFACT_SIZE})"

echo "=== 3/5 Upload para Object Storage ==="
NAMESPACE=$(oci os ns get --region "$REGION" --auth api_key --raw-output --query data)
oci os object put --bucket-name "$BUCKET" --namespace "$NAMESPACE" \
  --file /tmp/quarkus-app.tar.gz --name quarkus-app.tar.gz --force \
  --region "$REGION" --auth api_key

echo "=== 4/5 Abrindo sessao Bastion Port Forwarding ==="
BASTION_ID=$(terraform -chdir="$TF_DIR" output -raw bastion_id)
INSTANCE_ID=$(terraform -chdir="$TF_DIR" output -raw instance_id)
PRIVATE_IP=$(oci compute instance list-vnics --instance-id "$INSTANCE_ID" --region "$REGION" --auth api_key \
  --query 'data[0]."private-ip"' --raw-output)

SESSION_NAME="deploy-$(date +%Y%m%d-%H%M%S)"
oci bastion session create-port-forwarding \
  --bastion-id "$BASTION_ID" \
  --target-resource-id "$INSTANCE_ID" \
  --target-port 22 \
  --target-private-ip "$PRIVATE_IP" \
  --display-name "$SESSION_NAME" \
  --session-ttl 1800 \
  --ssh-public-key-file "${SSH_KEY}.pub" \
  --region "$REGION" --auth api_key \
  --wait-for-state SUCCEEDED >/dev/null

SESSION_ID=$(oci bastion session list --bastion-id "$BASTION_ID" --region "$REGION" --auth api_key \
  --query "data[?\"display-name\"=='$SESSION_NAME'] | [0].id" --raw-output)

LOCAL_PORT=$((20000 + RANDOM % 10000))
ssh -i "$SSH_KEY" -N -L "${LOCAL_PORT}:${PRIVATE_IP}:22" -p 22 \
  -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 \
  "${SESSION_ID}@host.bastion.${REGION}.oci.oraclecloud.com" &
TUNNEL_PID=$!
cleanup() {
  kill "$TUNNEL_PID" 2>/dev/null || true
  wait "$TUNNEL_PID" 2>/dev/null || true
  oci bastion session delete --session-id "$SESSION_ID" --region "$REGION" --auth api_key --force >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=== 5/5 Reiniciando o servico via SSH ==="
# A VM pode estar recem-recriada por uma mudanca de infra aplicada logo
# antes deste script (ver ci.yml) -- espera o SSH responder em vez de
# assumir que ja esta pronto (cloud-init leva alguns minutos).
SSH_READY=0
for i in $(seq 1 30); do
  if ssh -i "$SSH_KEY" -p "$LOCAL_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
      -o ConnectTimeout=8 -o BatchMode=yes ubuntu@127.0.0.1 true 2>/dev/null; then
    SSH_READY=1
    break
  fi
  sleep 10
done
if [ "$SSH_READY" != "1" ]; then
  echo "ERRO: SSH via Bastion nao respondeu apos 5min." >&2
  exit 1
fi

ssh -i "$SSH_KEY" -p "$LOCAL_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 \
  ubuntu@127.0.0.1 "sudo REGION='$REGION' BUCKET='$BUCKET' bash -s" <<'REMOTE'
set -euo pipefail
NAMESPACE=$(oci os ns get --region "$REGION" --auth instance_principal --raw-output --query data)
oci os object get --bucket-name "$BUCKET" --namespace "$NAMESPACE" \
  --name quarkus-app.tar.gz --file /tmp/quarkus-app.tar.gz \
  --region "$REGION" --auth instance_principal
systemctl stop rede-studio
rm -rf /opt/rede-studio/app/quarkus-app
tar -xzf /tmp/quarkus-app.tar.gz -C /opt/rede-studio/app/
chown -R redestudio:redestudio /opt/rede-studio/app
rm -f /tmp/quarkus-app.tar.gz
systemctl start rede-studio
sleep 3
systemctl is-active rede-studio
REMOTE

EIP=$(terraform -chdir="$TF_DIR" output -raw reserved_public_ip)
echo ""
echo "==============================================="
echo "Deploy concluido (servico reiniciado, VM nao foi recriada)."
echo "Testar: curl http://$EIP/q/health/live"
echo "==============================================="
