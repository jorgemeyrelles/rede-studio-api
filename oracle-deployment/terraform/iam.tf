# =============================================================
#  iam.tf — Dynamic Group (equivalente ao Instance Profile da AWS)
#  Concede à própria VM: leitura do secret no Vault + leitura do
#  bucket de artefatos. Sem credenciais estáticas na instância —
#  autenticação via "instance principal" (assinatura automática).
# =============================================================

resource "oci_identity_dynamic_group" "api" {
  compartment_id = var.tenancy_ocid # dynamic groups só podem ser criados na tenancy raiz
  name           = "rede-studio-api-dynamic-group"
  description    = "Instâncias do compartimento rede-studio-api"
  matching_rule  = "ALL {instance.compartment.id = '${oci_identity_compartment.app.id}'}"
}

resource "oci_identity_policy" "api" {
  compartment_id = oci_identity_compartment.app.id
  name           = "rede-studio-api-policy"
  description    = "Permissões da VM (dynamic group) e da CI (GitHub Actions) neste compartimento"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.api.name} to read secret-family in compartment id ${oci_identity_compartment.app.id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.api.name} to read objects in compartment id ${oci_identity_compartment.app.id} where target.bucket.name = '${oci_objectstorage_bucket.artifacts.name}'",
    # CI/CD (.github/workflows/ci.yml) — usuário dedicado "github-actions-ci"
    # no grupo "ci-deployers" (Identity Domain "Default", por isso a sintaxe
    # 'Default'/'ci-deployers' — grupo de domínio, não IAM group clássico).
    # Precisa de manage (não só use/read) pra recriar a VM via
    # terraform apply -replace e subir o jar no Object Storage.
    "Allow group 'Default'/'ci-deployers' to manage all-resources in compartment rede-studio-api",
  ]
}
