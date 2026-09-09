# =============================================================
#  compartment.tf — Compartimento dedicado do projeto
#
#  Agrupa todos os recursos do rede-studio-api isolados do resto da
#  tenancy — facilita auditoria, políticas de IAM com escopo restrito
#  e, no futuro, um `terraform destroy` limpo sem tocar em outros
#  projetos que possam existir na mesma conta.
# =============================================================

resource "oci_identity_compartment" "app" {
  compartment_id = var.tenancy_ocid
  name           = "rede-studio-api"
  description    = "Recursos do rede-studio-api (bootstrap Always Free)"

  enable_delete = true
}
