#!/usr/bin/env bash
# =============================================================
#  deploy.sh — Build, push e redeploy da API rede-studio na AWS
#
#  Pré-requisitos:
#    - aws configure (perfil com permissão em ECR + ECS)
#    - docker em execução
#    - terraform apply já executado (outputs disponíveis)
#    - Estar na raiz do projeto: /eclipse-workspace/rede-studio-api
#
#  Uso:
#    bash aws-deployment/deploy.sh              # usa tag "latest"
#    bash aws-deployment/deploy.sh v1.2.3       # tag personalizada
# =============================================================

set -euo pipefail

# ----------------------------------------------------------
# Configurações — ajuste se necessário
# ----------------------------------------------------------
AWS_REGION="${AWS_REGION:-us-east-2}"
TERRAFORM_DIR="$(dirname "$0")/terraform"
IMAGE_TAG="${1:-latest}"

# ----------------------------------------------------------
# Cores para o terminal
# ----------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERRO]${NC} $*" >&2; exit 1; }
step()    { echo -e "\n${BOLD}=== $* ===${NC}"; }

# ----------------------------------------------------------
# Validações iniciais
# ----------------------------------------------------------
step "Validando ambiente"

[[ -f "pom.xml" ]] || error "Execute a partir da raiz do projeto (onde está o pom.xml)"
command -v docker  &>/dev/null || error "docker não encontrado"
command -v aws     &>/dev/null || error "aws-cli não encontrado"
command -v mvn     &>/dev/null || error "mvn não encontrado"
command -v terraform &>/dev/null || warn "terraform não encontrado — outputs serão lidos via aws-cli"

# Verifica credenciais AWS ativas
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null) \
  || error "Credenciais AWS inválidas. Execute: aws configure"
info "Conta AWS: ${ACCOUNT_ID} | Região: ${AWS_REGION}"

# ----------------------------------------------------------
# Lê outputs do Terraform (ou solicita ao usuário)
# ----------------------------------------------------------
step "Lendo configurações do Terraform"

get_tf_output() {
  local key="$1"
  if command -v terraform &>/dev/null && [[ -d "$TERRAFORM_DIR" ]]; then
    terraform -chdir="$TERRAFORM_DIR" output -raw "$key" 2>/dev/null || true
  fi
}

ECR_URL=$(get_tf_output "ecr_repository_url")
ECS_CLUSTER=$(get_tf_output "ecs_cluster_name")
ECS_SERVICE=$(get_tf_output "ecs_service_name")

# Fallback: constrói a URL manualmente se o output estiver vazio
if [[ -z "$ECR_URL" ]]; then
  ECR_URL="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/rede-studio-api"
  warn "Output terraform não disponível — usando ECR inferido: ${ECR_URL}"
fi
[[ -z "$ECS_CLUSTER" ]] && ECS_CLUSTER="rede-studio-cluster"
[[ -z "$ECS_SERVICE"  ]] && ECS_SERVICE="rede-studio-api"

info "ECR: ${ECR_URL}"
info "Cluster: ${ECS_CLUSTER} | Serviço: ${ECS_SERVICE}"

# ----------------------------------------------------------
# Etapa 1 — Build Maven
# ----------------------------------------------------------
step "1/4 Build Maven (skips tests)"
mvn package -DskipTests --quiet \
  || error "Falha no build Maven. Verifique os logs acima."
success "Build Maven concluído"

# ----------------------------------------------------------
# Etapa 2 — Build Docker
# ----------------------------------------------------------
step "2/4 Build da imagem Docker"
docker build \
  --file Dockerfile.prod \
  --tag "rede-studio-api:${IMAGE_TAG}" \
  --tag "rede-studio-api:latest" \
  . \
  || error "Falha no build Docker"
success "Imagem criada: rede-studio-api:${IMAGE_TAG}"

# ----------------------------------------------------------
# Etapa 3 — Push para ECR
# ----------------------------------------------------------
step "3/4 Push para ECR"

info "Autenticando no ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_URL%%/*}" \
  || error "Falha na autenticação ECR"

# Tag e push
docker tag "rede-studio-api:${IMAGE_TAG}" "${ECR_URL}:${IMAGE_TAG}"
docker tag "rede-studio-api:latest"       "${ECR_URL}:latest"

docker push "${ECR_URL}:${IMAGE_TAG}" || error "Falha no push da tag ${IMAGE_TAG}"
docker push "${ECR_URL}:latest"       || error "Falha no push da tag latest"

success "Push concluído: ${ECR_URL}:${IMAGE_TAG}"

# ----------------------------------------------------------
# Etapa 4 — Force new deployment no ECS
# ----------------------------------------------------------
step "4/4 Redeploy no ECS"

DEPLOY_ID=$(aws ecs update-service \
  --region       "${AWS_REGION}" \
  --cluster      "${ECS_CLUSTER}" \
  --service      "${ECS_SERVICE}" \
  --force-new-deployment \
  --query        'service.deployments[0].id' \
  --output       text) \
  || error "Falha ao acionar redeploy no ECS"

success "Deployment iniciado: ${DEPLOY_ID}"

# ----------------------------------------------------------
# Aguarda estabilização (opcional)
# ----------------------------------------------------------
info "Aguardando estabilização do serviço (timeout: 5 min)..."
if aws ecs wait services-stable \
  --region  "${AWS_REGION}" \
  --cluster "${ECS_CLUSTER}" \
  --services "${ECS_SERVICE}" 2>/dev/null; then
  success "Serviço estabilizado com sucesso!"
else
  warn "Timeout ou falha ao aguardar estabilização."
  warn "Verifique manualmente:"
  warn "  aws ecs describe-services --cluster ${ECS_CLUSTER} --services ${ECS_SERVICE} --region ${AWS_REGION}"
fi

# ----------------------------------------------------------
# Resumo final
# ----------------------------------------------------------
ALB_DNS=$(get_tf_output "alb_dns_name" || true)
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║            Deploy concluído!                 ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════╝${NC}"
echo -e "  Imagem  : ${ECR_URL}:${IMAGE_TAG}"
echo -e "  Cluster : ${ECS_CLUSTER}"
echo -e "  Serviço : ${ECS_SERVICE}"
[[ -n "$ALB_DNS" ]] && echo -e "  API     : ${ALB_DNS}/q/health/live"
echo ""
