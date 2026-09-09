# =============================================================
#  outputs.tf — Saidas do Terraform (Bootstrap Oracle)
# =============================================================

output "reserved_public_ip" {
  description = "IP publico fixo da VM"
  value       = oci_core_public_ip.api.ip_address
}

output "api_http_url" {
  description = "URL HTTP da API"
  value       = "http://${oci_core_public_ip.api.ip_address}"
}

output "instance_id" {
  description = "OCID da instancia api (use com Bastion)"
  value       = oci_core_instance.api.id
}

output "rabbitmq_instance_id" {
  description = "OCID da instancia rabbitmq (use com Bastion)"
  value       = oci_core_instance.rabbitmq.id
}

output "rabbitmq_private_ip" {
  description = "IP privado do RabbitMQ (so acessivel de dentro da VCN)"
  value       = oci_core_instance.rabbitmq.private_ip
}

output "bastion_id" {
  description = "OCID do Bastion"
  value       = oci_bastion_bastion.main.id
}

output "artifacts_bucket" {
  description = "Bucket de Object Storage para upload do JAR"
  value       = oci_objectstorage_bucket.artifacts.name
}

output "vault_secret_id" {
  description = "OCID do secret no Vault"
  value       = oci_vault_secret.app.id
}

output "next_steps" {
  value = <<-EOT

    ========== PROXIMOS PASSOS ==========

    1. Preencha os campos placeholder do secret (MongoDB Atlas URI,
       chaves JWT, admin) — as credenciais SMTP ja foram preenchidas
       automaticamente pelo Terraform (comando correto eh update-base64,
       nao update-secret-content, e sem --secret-content-content-type):
       oci vault secret update-base64 \
         --secret-id ${oci_vault_secret.app.id} \
         --secret-content-content "$(echo -n '{"MONGODB_URI":"...","JWT_PRIVATE_KEY_B64":"...","JWT_PUBLIC_KEY_B64":"...","ADMIN_EMAIL":"...","ADMIN_USERNAME":"...","ADMIN_PASSWORD":"...","RABBITMQ_USERNAME":"redestudio","RABBITMQ_PASSWORD":"...","MAIL_SMTP_USERNAME":"KEEP","MAIL_SMTP_PASSWORD":"KEEP"}' | base64 -w0)" \
         --force --auth api_key

    2. Aguarde ~3 min para o cloud-init terminar, depois recrie as VMs
       para que releiam o secret atualizado (rabbitmq tambem, pois lê
       RABBITMQ_USERNAME/PASSWORD do mesmo secret):
       terraform apply -replace=oci_core_instance.api -replace=oci_core_instance.rabbitmq

    3. Build + upload do JAR (Fase 3 / oracle-deployment/deploy-oracle.sh)

    4. Validar:
       curl http://${oci_core_public_ip.api.ip_address}/q/health/live

    5. Acesso via Bastion (sem SSH publico):
       oci bastion session create-managed-ssh \
         --bastion-id ${oci_bastion_bastion.main.id} \
         --target-resource-id ${oci_core_instance.api.id} \
         --target-os-username ubuntu \
         --ssh-public-key-file ~/.ssh/id_rsa.pub --auth api_key
  EOT
}
