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
  description    = "Permissões da VM: ler secret do Vault e objetos do bucket de artefatos"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.api.name} to read secret-family in compartment id ${oci_identity_compartment.app.id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.api.name} to read objects in compartment id ${oci_identity_compartment.app.id} where target.bucket.name = '${oci_objectstorage_bucket.artifacts.name}'",
  ]
}
