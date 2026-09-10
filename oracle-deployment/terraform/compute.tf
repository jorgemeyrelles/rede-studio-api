# =============================================================
#  compute.tf — 2x VM.Standard.E2.1.Micro (x86, Always Free)
#
#  A cota Ampere A1 Flex (2 OCPU/12GB) fica indisponivel ("Out of host
#  capacity") de forma persistente em sa-saopaulo-1. Ate liberar, usamos
#  as 2 VMs x86 fixas do Always Free (1/8 OCPU + 1GB RAM cada) --
#  um so E2.1.Micro nao aguenta nginx + RabbitMQ + JVM juntos, entao os
#  servicos ficam divididos:
#    - api:      Ubuntu 24.04, nginx (80/443 publico) + Java 21 (Quarkus
#                via systemd), IP publico reservado.
#    - rabbitmq: Ubuntu 24.04, RabbitMQ (Docker), so acessivel de dentro
#                da VCN na porta 5672 (ver oci_core_security_list.rabbitmq
#                em network.tf) -- sem IP reservado.
# =============================================================

data "oci_core_images" "ubuntu_x86" {
  compartment_id           = oci_identity_compartment.app.id
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.E2.1.Micro"
  sort_by                  = "TIMECREATED"
  sort_order                = "DESC"
}

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

# --- Instancia RabbitMQ ---

resource "oci_core_instance" "rabbitmq" {
  compartment_id      = oci_identity_compartment.app.id
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  shape                = "VM.Standard.E2.1.Micro"
  display_name         = "rede-studio-rabbitmq"

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu_x86.images[0].id
    boot_volume_size_in_gbs = 50
  }

  create_vnic_details {
    subnet_id         = oci_core_subnet.rabbitmq.id
    assign_public_ip  = true # ephemeral, so pra egress (updates/Vault) -- 5672 fechado pra internet pela security list da subnet
    hostname_label     = "rede-studio-rabbitmq"
  }

  metadata = {
    ssh_authorized_keys = var.ops_ssh_public_key # acesso via Bastion Port Forwarding (bastion.tf) — Managed SSH não funciona nesta shape
    user_data = base64encode(templatefile("${path.module}/templates/cloud-init-rabbitmq.sh.tftpl", {
      region    = var.region
      secret_id = oci_vault_secret.app.id
    }))
  }

  agent_config {
    is_monitoring_disabled = false
    is_management_disabled = false

    # Sem isso, "plugins-config" fica null e o Bastion Managed SSH
    # recusa a sessão ("Bastion plugin must be enabled on the target
    # instance") mesmo com is_management_disabled = false.
    plugins_config {
      name          = "Bastion"
      desired_state = "ENABLED"
    }
  }

  lifecycle {
    replace_triggered_by = [terraform_data.cloud_init_trigger_rabbitmq]
  }

  depends_on = [
    oci_vault_secret.app,
    oci_identity_policy.api,
  ]
}

resource "terraform_data" "cloud_init_trigger_rabbitmq" {
  input = filesha256("${path.module}/templates/cloud-init-rabbitmq.sh.tftpl")
}

# --- Instancia API (nginx + Quarkus) ---

resource "oci_core_instance" "api" {
  compartment_id      = oci_identity_compartment.app.id
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  shape                = "VM.Standard.E2.1.Micro"
  display_name         = "rede-studio-api"

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu_x86.images[0].id
    boot_volume_size_in_gbs = 50
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = false # o IP publico vem so do oci_core_public_ip.api (RESERVED) abaixo
    hostname_label    = "rede-studio-api"
  }

  metadata = {
    ssh_authorized_keys = var.ops_ssh_public_key # acesso via Bastion Port Forwarding (bastion.tf) — Managed SSH não funciona nesta shape
    user_data = base64encode(templatefile("${path.module}/templates/cloud-init-api.sh.tftpl", {
      region                 = var.region
      secret_id              = oci_vault_secret.app.id
      artifacts_bucket       = oci_objectstorage_bucket.artifacts.name
      namespace              = local.object_storage_namespace
      mongodb_database       = var.mongodb_database
      jwt_issuer             = var.jwt_issuer
      jwt_expiration_seconds = var.jwt_expiration_seconds
      cors_origins           = var.cors_origins
      app_port                = var.app_port
      mail_from               = var.email_approved_sender
      rabbitmq_host           = oci_core_instance.rabbitmq.private_ip
      duckdns_hostname        = var.duckdns_hostname
      letsencrypt_email       = var.letsencrypt_email
    }))
  }

  agent_config {
    is_monitoring_disabled = false
    is_management_disabled = false

    # Sem isso, "plugins-config" fica null e o Bastion Managed SSH
    # recusa a sessão ("Bastion plugin must be enabled on the target
    # instance") mesmo com is_management_disabled = false.
    plugins_config {
      name          = "Bastion"
      desired_state = "ENABLED"
    }
  }

  # Recriar a VM se o cloud-init mudar (substituição limpa, mesmo espírito do
  # user_data_replace_on_change usado na AWS)
  lifecycle {
    replace_triggered_by = [terraform_data.cloud_init_trigger]
  }

  depends_on = [
    oci_vault_secret.app,
    oci_objectstorage_bucket.artifacts,
    oci_identity_policy.api,
    oci_core_instance.rabbitmq,
  ]
}

resource "terraform_data" "cloud_init_trigger" {
  input = filesha256("${path.module}/templates/cloud-init-api.sh.tftpl")
}

# --- Reserved Public IP (fixo entre reboots, equivalente ao Elastic IP) ---
# Só na instância api -- rabbitmq usa IP efêmero, não precisa ser fixo.

data "oci_core_vnic_attachments" "api" {
  compartment_id = oci_identity_compartment.app.id
  instance_id    = oci_core_instance.api.id
}

data "oci_core_private_ips" "api" {
  vnic_id = data.oci_core_vnic_attachments.api.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "api" {
  compartment_id = oci_identity_compartment.app.id
  display_name   = "rede-studio-reserved-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.api.private_ips[0].id
}
