# =============================================================
#  bastion.tf — OCI Bastion Service (Always Free)
#
#  Sessão SSH gerenciada até a VM — porta 22 nunca fica pública
#  (a Security List em network.tf só libera 80/443). Para conectar:
#
#    oci bastion session create-managed-ssh \
#      --bastion-id <bastion_id do output> \
#      --target-resource-id <instance_id do output> \
#      --target-os-username ubuntu \
#      --ssh-public-key-file ~/.ssh/id_rsa.pub \
#      --auth api_key
# =============================================================

resource "oci_bastion_bastion" "main" {
  bastion_type                 = "STANDARD"
  compartment_id               = oci_identity_compartment.app.id
  target_subnet_id             = oci_core_subnet.public.id
  name                          = "rede-studio-bastion"
  client_cidr_block_allow_list = ["0.0.0.0/0"]
  max_session_ttl_in_seconds   = 1800
}
