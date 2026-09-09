# =============================================================
#  provider.tf — Provider OCI + Backend Object Storage para state
#
#  Antes do primeiro uso:
#    1. Crie o bucket de state manualmente (ver README da pasta):
#       oci os bucket create --compartment-id <tenancy_ocid> \
#         --name rede-studio-tfstate --auth api_key
#    2. Habilite versionamento no bucket (recomendado p/ recuperação):
#       oci os bucket update --bucket-name rede-studio-tfstate \
#         --versioning Enabled --auth api_key
#    3. Execute: terraform init
#
#  Autenticação: usa o arquivo ~/.oci/config gerado por `oci setup config`
#  (mesmo princípio do ~/.aws/credentials usado no ambiente AWS).
# =============================================================

terraform {
  required_version = ">= 1.12.0" # backend "oci" nativo exige 1.12+

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }

  backend "oci" {
    bucket    = "rede-studio-tfstate"
    namespace = "gruw8gjug9rm"
    key       = "rede-studio-api/terraform.tfstate"
    region    = "sa-saopaulo-1"
  }
}

provider "oci" {
  region = var.region
}
