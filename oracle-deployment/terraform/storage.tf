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
}
