# =============================================================
#  network.tf — VCN + 1 Subnet Pública + IGW + Security List
#  ingress 80/443 público; sem porta 22 (acesso via Bastion)
# =============================================================

resource "oci_core_vcn" "main" {
  compartment_id = oci_identity_compartment.app.id
  cidr_block     = var.vcn_cidr
  display_name   = "rede-studio-vcn"
  dns_label      = "redestudio"
}

resource "oci_core_internet_gateway" "main" {
  compartment_id = oci_identity_compartment.app.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "rede-studio-igw"
  enabled        = true
}

resource "oci_core_route_table" "public" {
  compartment_id = oci_identity_compartment.app.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "rede-studio-rt-public"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.main.id
  }
}

resource "oci_core_security_list" "api" {
  compartment_id = oci_identity_compartment.app.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "rede-studio-api-sl"

  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    description = "HTTP da internet"

    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    description = "HTTPS da internet (futuro TLS)"

    tcp_options {
      min = 443
      max = 443
    }
  }

  ingress_security_rules {
    protocol    = "6"
    source      = var.vcn_cidr
    source_type = "CIDR_BLOCK"
    description = "SSH -- somente de dentro da VCN (sessao gerenciada do Bastion, nunca da internet)"

    tcp_options {
      min = 22
      max = 22
    }
  }

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "All outbound traffic"
  }
}

resource "oci_core_security_list" "rabbitmq" {
  compartment_id = oci_identity_compartment.app.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "rede-studio-rabbitmq-sl"

  # AMQP so acessivel de dentro da VCN (instancia api) -- nunca da internet
  ingress_security_rules {
    protocol    = "6"
    source      = var.vcn_cidr
    source_type = "CIDR_BLOCK"
    description = "AMQP -- somente da VCN (instancia api)"

    tcp_options {
      min = 5672
      max = 5672
    }
  }

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "All outbound traffic"
  }
}

resource "oci_core_subnet" "public" {
  compartment_id             = oci_identity_compartment.app.id
  vcn_id                     = oci_core_vcn.main.id
  cidr_block                 = var.public_subnet_cidr
  display_name               = "rede-studio-public"
  dns_label                  = "public"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.api.id]
  prohibit_public_ip_on_vnic = false
}

# Security list é por subnet nesse provider (create_vnic_details não aceita
# security_list_ids) -- por isso o rabbitmq ganha subnet própria, mesma VCN
# e route table (IGW) da subnet public, só troca a security list.
resource "oci_core_subnet" "rabbitmq" {
  compartment_id             = oci_identity_compartment.app.id
  vcn_id                     = oci_core_vcn.main.id
  cidr_block                 = var.rabbitmq_subnet_cidr
  display_name               = "rede-studio-rabbitmq"
  dns_label                  = "rabbitmq"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.rabbitmq.id]
  prohibit_public_ip_on_vnic = false
}
