#!/usr/bin/env bash
# Retenta terraform apply ate a Oracle liberar capacidade Ampere A1 Flex
# em sa-saopaulo-1 ("Out of host capacity" e um erro transiente do lado
# deles, nao da nossa config). Ctrl+C para parar.
set -uo pipefail
cd "$(dirname "$0")"

INTERVAL=${1:-300}  # segundos entre tentativas (default 5 min)
ATTEMPT=1

while true; do
  echo "=== Tentativa $ATTEMPT — $(date '+%H:%M:%S') ==="
  terraform plan -out=tfplan >/tmp/tfplan_out.log 2>&1

  if ! grep -q "Plan:" /tmp/tfplan_out.log; then
    echo "terraform plan falhou (nao e erro de capacidade). Log:"
    tail -40 /tmp/tfplan_out.log
    exit 1
  fi

  if grep -q "No changes" /tmp/tfplan_out.log; then
    echo "Nada a aplicar — infra ja completa."
    exit 0
  fi

  terraform apply "tfplan" 2>&1 | tee /tmp/tfapply_out.log

  if grep -q "Apply complete" /tmp/tfapply_out.log; then
    echo "✅ Apply concluido com sucesso na tentativa $ATTEMPT!"
    terraform output
    exit 0
  fi

  if ! grep -qi "Out of host capacity" /tmp/tfapply_out.log; then
    echo "Erro diferente de capacidade — parando pra voce investigar."
    exit 1
  fi

  echo "Sem capacidade ainda. Proxima tentativa em ${INTERVAL}s..."
  ATTEMPT=$((ATTEMPT + 1))
  sleep "$INTERVAL"
done
