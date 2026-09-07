# =============================================================
#  outputs.tf — Saidas do Terraform (Bootstrap)
# =============================================================

output "elastic_ip" {
  description = "IP publico fixo da EC2"
  value       = aws_eip.api.public_ip
}

output "api_http_url" {
  description = "URL HTTP da API"
  value       = "http://${aws_eip.api.public_ip}"
}

output "ec2_instance_id" {
  description = "ID da instancia EC2 (use com SSM Session Manager)"
  value       = aws_instance.api.id
}

output "ssm_connect_command" {
  description = "Comando para abrir shell na EC2 via SSM"
  value       = "aws ssm start-session --target ${aws_instance.api.id} --region ${var.aws_region}"
}

output "artifacts_bucket" {
  description = "Bucket S3 para upload do JAR"
  value       = aws_s3_bucket.artifacts.bucket
}

output "secrets_manager_arn" {
  description = "ARN do secret"
  value       = aws_secretsmanager_secret.app.arn
}

output "next_steps" {
  value = <<-EOT

    ========== PROXIMOS PASSOS ==========

    1. Aguarde ~3 min para o user-data terminar.

    2. Build + upload do JAR:
       bash aws-deployment/deploy-bootstrap.sh

    3. Validar:
       curl http://${aws_eip.api.public_ip}/q/health/live

    4. Acesso SSM (sem SSH):
       aws ssm start-session --target ${aws_instance.api.id} --region ${var.aws_region}
  EOT
}
