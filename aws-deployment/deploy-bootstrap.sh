#!/bin/bash
# =============================================================
#  deploy-bootstrap.sh — build + upload S3 + reload via SSM
#
#  Substitui o antigo deploy.sh (que usava ECR/ECS).
#  Uso: bash aws-deployment/deploy-bootstrap.sh
# =============================================================
set -euo pipefail

REGION="${AWS_REGION:-us-east-2}"
BUCKET="${ARTIFACTS_BUCKET:-rede-studio-artifacts-970103398279}"
EC2_TAG="${EC2_TAG:-rede-studio-api}"

cd "$(dirname "$0")/.."

echo "=== 1/4 Build Quarkus (skip tests) ==="
if command -v mvn >/dev/null 2>&1; then
  MVN_CMD="mvn"
else
  MVN_CMD="./mvnw"
fi
$MVN_CMD package -DskipTests -Dquarkus.profile=master

echo "=== 2/4 Empacotando quarkus-app/ ==="
rm -f /tmp/quarkus-app.tar.gz
tar -czf /tmp/quarkus-app.tar.gz -C target quarkus-app

ARTIFACT_SIZE=$(du -h /tmp/quarkus-app.tar.gz | awk '{print $1}')
echo "Artefato: /tmp/quarkus-app.tar.gz ($ARTIFACT_SIZE)"

echo "=== 3/4 Upload para s3://$BUCKET/ ==="
aws s3 cp /tmp/quarkus-app.tar.gz "s3://$BUCKET/quarkus-app.tar.gz" --region "$REGION"

echo "=== 4/4 Reload via SSM ==="
INSTANCE_ID=$(aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:Name,Values=$EC2_TAG" "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)

if [ "$INSTANCE_ID" = "None" ] || [ -z "$INSTANCE_ID" ]; then
  echo "ERRO: nenhuma instancia '$EC2_TAG' em execucao encontrada."
  exit 1
fi

echo "Instance: $INSTANCE_ID"

CMD_ID=$(aws ssm send-command --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --comment "rede-studio deploy" \
  --parameters "commands=[
    'aws s3 cp s3://$BUCKET/quarkus-app.tar.gz /tmp/quarkus-app.tar.gz --region $REGION',
    'rm -rf /opt/rede-studio/app/quarkus-app',
    'tar -xzf /tmp/quarkus-app.tar.gz -C /opt/rede-studio/app/',
    'chown -R redestudio:redestudio /opt/rede-studio/app',
    'systemctl restart rede-studio',
    'sleep 5',
    'systemctl is-active rede-studio'
  ]" --query 'Command.CommandId' --output text)

echo "SSM CommandId: $CMD_ID"
echo "Aguardando execucao..."
aws ssm wait command-executed --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" --region "$REGION" || true

aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --region "$REGION" --query '{Status:Status,Out:StandardOutputContent,Err:StandardErrorContent}' \
  --output json

EIP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

echo ""
echo "==============================================="
echo "Deploy concluido."
echo "Testar: curl http://$EIP/q/health/live"
echo "==============================================="
