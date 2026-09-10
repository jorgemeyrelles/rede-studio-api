# =============================================================
#  storage.tf — Bucket privado para artefatos de deploy (JAR Quarkus)
# =============================================================

# Namespace de Object Storage é fixo por tenancy (atribuído uma única vez,
# nunca muda) — mesmo valor já hardcoded em provider.tf (backend "oci" do
# state remoto). Usar um literal em vez de uma data source evita um erro
# real de plan em CI ("Missing required argument: namespace" — a
# expressão da data source falhava ao ser avaliada mesmo com a leitura
# dela aparentemente OK; não vale a pena depender disso pra um valor que
# é imutável de qualquer forma).
locals {
  object_storage_namespace = "gruw8gjug9rm"
}

resource "oci_objectstorage_bucket" "artifacts" {
  compartment_id = oci_identity_compartment.app.id
  namespace      = local.object_storage_namespace
  name           = "rede-studio-artifacts"
  access_type    = "NoPublicAccess"
  versioning     = "Enabled"
}
