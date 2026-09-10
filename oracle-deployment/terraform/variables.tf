# =============================================================
#  variables.tf — Bootstrap (Bastion + VCN + Ampere A1 + Vault)
# =============================================================

variable "tenancy_ocid" {
  description = "OCID da tenancy (raiz da conta) — usado como compartment_id pai"
  type        = string
}

variable "region" {
  description = "Região OCI (escolha na criação da conta — não muda depois)"
  type        = string
  default     = "sa-saopaulo-1"
}

variable "environment" {
  description = "Nome do ambiente"
  type        = string
  default     = "prod"
}

variable "vcn_cidr" {
  description = "CIDR block da VCN"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR da subnet pública"
  type        = string
  default     = "10.0.1.0/24"
}

variable "rabbitmq_subnet_cidr" {
  description = "CIDR da subnet do RabbitMQ (security list própria, fechada pra internet)"
  type        = string
  default     = "10.0.2.0/24"
}

variable "app_port" {
  description = "Porta HTTP local da API Quarkus"
  type        = number
  default     = 8080
}

variable "mongodb_database" {
  description = "Nome do banco MongoDB (Atlas)"
  type        = string
  default     = "rede_studio_prod"
}

variable "jwt_issuer" {
  description = "Issuer do JWT"
  type        = string
}

variable "jwt_expiration_seconds" {
  description = "Tempo de expiração do JWT em segundos"
  type        = number
  default     = 86400
}

variable "cors_origins" {
  description = "Origens permitidas para CORS"
  type        = string
  default     = "*"
}

variable "enable_email_delivery" {
  description = "Liga o OCI Email Delivery (email.tf) — exige dominio proprio verificado por DNS. Deixe false ate ter um dominio registrado."
  type        = bool
  default     = false
}

variable "email_approved_sender" {
  description = "Endereço de e-mail aprovado como remetente no OCI Email Delivery (só usado quando enable_email_delivery = true)"
  type        = string
  default     = ""
}

variable "duckdns_hostname" {
  description = "Hostname DuckDNS apontando para o IP reservado da API (usado pelo Certbot para emitir o certificado TLS via desafio HTTP-01). Deixe vazio pra pular a emissão de TLS no cloud-init."
  type        = string
  default     = ""
}

variable "letsencrypt_email" {
  description = "E-mail de contato registrado no Let's Encrypt (avisos de expiração de certificado) — só usado quando duckdns_hostname não está vazio."
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "E-mail que recebe o alarme do OCI Monitoring quando o backend do LB fica unhealthy (precisa confirmar a inscrição uma vez, via link que a OCI manda pra esse e-mail)."
  type        = string
  default     = "jotaengpuc@gmail.com"
}

variable "ops_ssh_public_key" {
  description = "Chave(s) pública(s) SSH autorizada(s) a logar como 'ubuntu' via sessão Bastion Port Forwarding — só pra debug manual (ler logs etc.), nunca faz parte do caminho de deploy (isso é via credencial de API da OCI). Uma por linha se for mais de uma. Deixe vazio pra não autorizar nenhuma."
  type        = string
  default     = ""
}
