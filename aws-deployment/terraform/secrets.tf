# =============================================================
#  secrets.tf — AWS Secrets Manager
#
#  Cria o secret "rede-studio/app" com placeholder vazio.
#  Após o terraform apply, preencha os valores com a URI do Atlas:
#
#    aws secretsmanager put-secret-value \
#      --secret-id rede-studio/app \
#      --secret-string '{
#        "MONGODB_URI":         "mongodb+srv://user:pass@cluster0.xxxxx.mongodb.net/rede_studio_prod",
#        "JWT_PRIVATE_KEY_B64": "$(base64 -w 0 secrets/privateKey.pem)",
#        "JWT_PUBLIC_KEY_B64":  "$(base64 -w 0 secrets/publicKey.pem)",
#        "ADMIN_EMAIL":         "admin@seudominio.com",
#        "ADMIN_USERNAME":      "adminprod",
#        "ADMIN_PASSWORD":      "<senha_forte>"
#      }'
# =============================================================

resource "aws_secretsmanager_secret" "app" {
  name                    = "rede-studio/app"
  description             = "Secrets da API rede-studio (JWT keys, MongoDB URI, admin credentials)"
  recovery_window_in_days = 7

  tags = { Name = "rede-studio-app-secrets" }
}

# Versão inicial com placeholders — substitua com aws-cli após o apply
resource "aws_secretsmanager_secret_version" "app_initial" {
  secret_id = aws_secretsmanager_secret.app.id

  secret_string = jsonencode({
    MONGODB_URI         = "CONFIGURE_MONGODB_ATLAS_URI"
    JWT_PRIVATE_KEY_B64 = "SUBSTITUA_APOS_APPLY"
    JWT_PUBLIC_KEY_B64  = "SUBSTITUA_APOS_APPLY"
    ADMIN_EMAIL         = "SUBSTITUA_APOS_APPLY"
    ADMIN_USERNAME      = "SUBSTITUA_APOS_APPLY"
    ADMIN_PASSWORD      = "SUBSTITUA_APOS_APPLY"
  })

  # Ignora mudanças após o apply inicial para não sobrescrever valores reais
  lifecycle {
    ignore_changes = [secret_string]
  }
}
