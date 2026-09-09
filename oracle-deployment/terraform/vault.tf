# =============================================================
#  vault.tf — OCI Vault (Always Free: chaves protegidas por software)
#
#  IMPORTANTE: vault_type = "DEFAULT" e protection_mode = "SOFTWARE"
#  são o que mantém isso gratuito. "VIRTUAL_PRIVATE" (vault dedicado)
#  e protection_mode = "HSM" são cobrados — não usar.
#
#  Após o apply, preencha MONGODB_URI (Atlas) e os campos ainda
#  placeholder com:
#    oci vault secret update-secret-content \
#      --secret-id <secret_id do output> \
#      --secret-content-content "$(echo -n '{...json real...}' | base64 -w0)" \
#      --secret-content-content-type BASE64 --auth api_key
# =============================================================

resource "oci_kms_vault" "app" {
  compartment_id = oci_identity_compartment.app.id
  display_name   = "rede-studio-vault"
  vault_type     = "DEFAULT"
}

resource "oci_kms_key" "app" {
  compartment_id       = oci_identity_compartment.app.id
  display_name         = "rede-studio-secret-key"
  management_endpoint  = oci_kms_vault.app.management_endpoint
  protection_mode      = "SOFTWARE"

  key_shape {
    algorithm = "AES"
    length    = 32
  }
}

resource "oci_vault_secret" "app" {
  compartment_id = oci_identity_compartment.app.id
  vault_id       = oci_kms_vault.app.id
  key_id         = oci_kms_key.app.id
  secret_name    = "rede-studio-app"
  description    = "Secrets da API rede-studio (JWT keys, MongoDB URI, admin, RabbitMQ, SMTP)"

  secret_content {
    content_type = "BASE64"
    content = base64encode(jsonencode({
      MONGODB_URI         = "CONFIGURE_MONGODB_ATLAS_URI"
      JWT_PRIVATE_KEY_B64 = "SUBSTITUA_APOS_APPLY"
      JWT_PUBLIC_KEY_B64  = "SUBSTITUA_APOS_APPLY"
      ADMIN_EMAIL         = "SUBSTITUA_APOS_APPLY"
      ADMIN_USERNAME      = "SUBSTITUA_APOS_APPLY"
      ADMIN_PASSWORD      = "SUBSTITUA_APOS_APPLY"
      RABBITMQ_USERNAME   = "redestudio"
      RABBITMQ_PASSWORD   = "SUBSTITUA_APOS_APPLY"
      # Preenchidos automaticamente quando enable_email_delivery = true
      # (ver email.tf) — ate la, placeholder (o mailer so eh usado pelo
      # consumer assincrono de registro, nao bloqueia o resto da API).
      MAIL_SMTP_USERNAME  = var.enable_email_delivery ? oci_identity_smtp_credential.email[0].username : "PENDENTE_DOMINIO"
      MAIL_SMTP_PASSWORD  = var.enable_email_delivery ? oci_identity_smtp_credential.email[0].password : "PENDENTE_DOMINIO"
      # Client IDs do login social (Google/Microsoft) — publicos por
      # natureza (nao sao secret), mas vivem no mesmo bundle por
      # conveniencia, igual o resto da config nao-Terraform-managed.
      GOOGLE_OAUTH_CLIENT_ID    = "SUBSTITUA_APOS_APPLY"
      MICROSOFT_OAUTH_CLIENT_ID = "SUBSTITUA_APOS_APPLY"
    }))
  }

  # As credenciais SMTP são geradas pelo Terraform (email.tf) — os demais
  # campos são placeholders preenchidos manualmente após o apply.
  lifecycle {
    ignore_changes = [secret_content[0].content]
  }
}
