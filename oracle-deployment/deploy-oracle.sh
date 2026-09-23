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
# O id vem direto da resposta deste comando (--query/--raw-output extraem
# so "data.id") -- antes o script relistava todas as sessoes do bastion
# depois de cria-la e filtrava por display-name, o que e fragil: sem
# --all, "oci bastion session list" so devolve a primeira pagina, e com
# varias sessoes recentes acumuladas (TTL de 30min, e tentativas de deploy
# repetidas na depuracao deste script) a sessao recem-criada podia nem
# estar nela -- levando a conectar com o id de uma sessao antiga, cuja
# chave publica registrada nao e a nossa (explica um "Permission denied
# (publickey)" mesmo com IdentitiesOnly=yes correto, ver incidente
# 2026-09-23). Ler o id na propria resposta da criacao elimina essa classe
# de erro por completo -- nunca ha ambiguidade sobre qual sessao usar.
SESSION_ID=$(oci bastion session create-port-forwarding \
  --bastion-id "$BASTION_ID" \
  --target-resource-id "$INSTANCE_ID" \
  --target-port 22 \
  --target-private-ip "$PRIVATE_IP" \
  --display-name "$SESSION_NAME" \
  --session-ttl 1800 \
  --ssh-public-key-file "${SSH_KEY}.pub" \
  --region "$REGION" --auth api_key \
  --wait-for-state SUCCEEDED \
  --query 'data.id' --raw-output)

LOCAL_PORT=$((20000 + RANDOM % 10000))
TUNNEL_LOG="$(mktemp /tmp/bastion-tunnel-XXXXXX.log)"
TUNNEL_PID=""
cleanup() {
  # "if" (nao "[ ... ] &&") de proposito: sob "set -e", um "&&" cujo lado
  # esquerdo falha (TUNNEL_PID ainda vazio) aborta a funcao no meio e pula
  # a exclusao da sessao do bastion abaixo -- "if" e um contexto protegido,
  # nao aciona o -e.
  if [ -n "$TUNNEL_PID" ]; then
    kill "$TUNNEL_PID" 2>/dev/null || true
    wait "$TUNNEL_PID" 2>/dev/null || true
  fi
  oci bastion session delete --session-id "$SESSION_ID" --region "$REGION" --auth api_key --force >/dev/null 2>&1 || true
  rm -f "$TUNNEL_LOG"
}
trap cleanup EXIT

# IdentitiesOnly=yes -- sem isso, o ssh tambem oferece ao bastion qualquer
# chave carregada num ssh-agent do ambiente que rodar este script (ex.: um
# agente de desktop com chaves pessoais). O bastion so autoriza a chave
# registrada nesta sessao especifica e derruba a conexao apos poucas
# tentativas erradas -- as chaves alheias esgotavam essa cota antes da
# certa ser sequer oferecida, causando "Permission denied (publickey)"
# mesmo com a chave certa disponivel (ver incidente 2026-09-22).
#
# Ate 3 tentativas para abrir o tunel: a OCI as vezes reporta a sessao
# como SUCCEEDED antes do proxy do bastion propagar de fato a chave
# registrada (corrida de propagacao do lado da OCI) -- uma unica tentativa
# tratava esse atraso passageiro como falha definitiva.
TUNNEL_UP=0
for attempt in 1 2 3; do
  : >"$TUNNEL_LOG"
  ssh -i "$SSH_KEY" -o IdentitiesOnly=yes -N -L "${LOCAL_PORT}:${PRIVATE_IP}:22" -p 22 \
    -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 \
    "${SESSION_ID}@host.bastion.${REGION}.oci.oraclecloud.com" >"$TUNNEL_LOG" 2>&1 &
  TUNNEL_PID=$!
  sleep 3
  if kill -0 "$TUNNEL_PID" 2>/dev/null; then
    TUNNEL_UP=1
    break
  fi
  wait "$TUNNEL_PID" 2>/dev/null || true
  echo "Tentativa ${attempt}/3 de abrir o tunel do bastion falhou, tentando de novo em 5s..." >&2
  sleep 5
done
if [ "$TUNNEL_UP" != "1" ]; then
  echo "ERRO: tunel SSH do bastion nao estabeleceu conexao apos 3 tentativas." >&2
  cat "$TUNNEL_LOG" >&2
  exit 1
fi

echo "=== 5/5 Reiniciando o servico via SSH ==="
# A VM pode estar recem-recriada por uma mudanca de infra aplicada logo
# antes deste script (ver ci.yml) -- espera o SSH responder em vez de
# assumir que ja esta pronto (cloud-init leva alguns minutos).
SSH_READY=0
for i in $(seq 1 30); do
  if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
    echo "ERRO: tunel SSH do bastion encerrou durante a espera." >&2
    cat "$TUNNEL_LOG" >&2
    exit 1
  fi
  if ssh -i "$SSH_KEY" -o IdentitiesOnly=yes -p "$LOCAL_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
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

ssh -i "$SSH_KEY" -o IdentitiesOnly=yes -p "$LOCAL_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 \
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
