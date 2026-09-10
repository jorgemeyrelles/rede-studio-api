# =============================================================
#  storage.tf — Bucket privado para artefatos de deploy (JAR Quarkus)
# =============================================================

data "oci_objectstorage_namespace" "ns" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "artifacts" {
  compartment_id = oci_identity_compartment.app.id
  namespace      = data.oci_objectstorage_namespace.ns.namespace
  name           = "rede-studio-artifacts"
  access_type    = "NoPublicAccess"
  versioning     = "Enabled"

  # namespace é fixo por tenancy (nunca muda depois de atribuído) — uma
  # vez que o bucket já existe, não faz sentido reavaliar essa data
  # source toda vez (achamos um caso real em CI onde isso quebrava o
  # plan com "Missing required argument: namespace" mesmo com a leitura
  # da data source aparentemente OK — não vale a pena depender disso
  # pra um valor que é imutável de qualquer forma).
  lifecycle {
    ignore_changes = [namespace]
  }
}
