# Recursos decomissionados (2026-09-05)

Terraform histórico dos recursos AWS que **geravam custo** e foram
destruídos após a migração validada para Oracle Cloud (ver `README.md` na
raiz do projeto, seção "Arquitetura Cloud", e `CLAUDE.md`).

**Estes arquivos NÃO são um Terraform executável.** Não têm backend, e
referenciam recursos (`aws_subnet.public`, `aws_security_group.api`,
`aws_iam_instance_profile.api`, etc.) que vivem no diretório pai
(`../`) — mover isso pra cá quebrou essas referências de propósito, porque
o objetivo é só documentação/histórico, não um módulo reutilizável.

## O que tinha aqui

- `ec2.tf` — instância EC2 (t3.micro) + Elastic IP
- `s3.tf` — bucket de artefatos de build
- `secrets.tf` — AWS Secrets Manager
- `ecr.tf` — repositório ECR (já estava órfão antes da destruição — o
  deploy atual da época usava EC2+S3+SSM, não ECS/ECR)
- `iam-s3-read-policy.tf` — a única policy IAM destruída, porque
  referenciava o ARN do bucket de `s3.tf` (extraída de `iam.tf`, que
  continua ativo no diretório pai com o restante do IAM intacto)

## O que continua rodando (diretório pai, `../`)

VPC, subnet, Internet Gateway, route table, security group, e o IAM
completo exceto a policy acima — tudo gratuito na AWS, mantido por decisão
do usuário (sem motivo pra desmontar algo que não custa nada).

## Se precisar recriar algo daqui

Copie o(s) arquivo(s) de volta pro diretório pai, ajuste as referências
cruzadas se algo mudou, rode `terraform plan` pra conferir antes de
aplicar. Não copie tudo de uma vez sem revisar — o `outputs.tf` original
tinha saídas que dependiam de tudo isso junto.
