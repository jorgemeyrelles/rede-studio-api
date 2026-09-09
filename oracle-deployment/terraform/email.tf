# =============================================================
#  email.tf — OCI Email Delivery (Always Free: 3.000 e-mails/mês)
#
#  Desligado por padrão (enable_email_delivery = false) — a Oracle exige
#  um dominio verificado por DNS (SPF/DKIM) antes de aprovar um remetente,
#  e o projeto ainda nao tem dominio registrado. Quando tiverem:
#    1. Adicione e verifique o dominio em Email Delivery > Email Domains.
#    2. Defina enable_email_delivery = true e email_approved_sender no
#       terraform.tfvars.
#    3. terraform apply — as credenciais SMTP sao injetadas automaticamente
#       no Vault (ver vault.tf), sem copia manual.
# =============================================================

resource "oci_email_sender" "app" {
  count = var.enable_email_delivery ? 1 : 0

  compartment_id = oci_identity_compartment.app.id
  email_address  = var.email_approved_sender
}

resource "oci_identity_user" "email_sender" {
  count = var.enable_email_delivery ? 1 : 0

  compartment_id = var.tenancy_ocid # usuários IAM só podem ser criados na tenancy raiz
  name           = "rede-studio-email-sender"
  description    = "Usuário de serviço — só existe para segurar a credencial SMTP do OCI Email Delivery"
}

resource "oci_identity_smtp_credential" "email" {
  count = var.enable_email_delivery ? 1 : 0

  user_id     = oci_identity_user.email_sender[0].id
  description = "SMTP credential — rede-studio-api welcome emails"
}
