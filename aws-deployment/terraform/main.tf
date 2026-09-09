# =============================================================
#  main.tf — Provider AWS + Backend S3 para estado Terraform
#
#  Antes do primeiro uso:
#    1. Crie o bucket S3 manualmente (ou via aws-cli):
#       aws s3 mb s3://rede-studio-tfstate --region us-east-1
#    2. Habilite versionamento no bucket:
#       aws s3api put-bucket-versioning \
#         --bucket rede-studio-tfstate \
#         --versioning-configuration Status=Enabled
#    3. Execute: terraform init
# =============================================================

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }

  # Estado remoto — substitua o bucket pelo nome real criado na sua conta
  backend "s3" {
    bucket  = "rede-studio-tfstate"
    key     = "rede-studio-api/terraform.tfstate"
    region  = "us-east-2"
    encrypt = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "rede-studio-api"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
