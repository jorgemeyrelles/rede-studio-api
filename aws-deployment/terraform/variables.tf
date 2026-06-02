# =============================================================
#  variables.tf — Bootstrap (Nginx + EC2 + EIP)
# =============================================================

variable "aws_region" {
  description = "Regiao AWS"
  type        = string
  default     = "us-east-2"
}

variable "environment" {
  description = "Nome do ambiente"
  type        = string
  default     = "prod"
}

variable "vpc_cidr" {
  description = "CIDR block da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR da subnet publica"
  type        = string
  default     = "10.0.1.0/24"
}

variable "availability_zone" {
  description = "AZ da subnet publica"
  type        = string
  default     = "us-east-2a"
}

variable "ec2_instance_type" {
  description = "Tipo da instancia EC2"
  type        = string
  default     = "t3.micro"
}

variable "artifacts_bucket_name" {
  description = "Nome do bucket S3 para artefatos (globalmente unico)"
  type        = string
}

variable "app_port" {
  description = "Porta HTTP local da API Quarkus"
  type        = number
  default     = 8080
}

variable "mongodb_database" {
  description = "Nome do banco MongoDB (Atlas)"
  type        = string
  default     = "rede_studio_prod"
}

variable "jwt_issuer" {
  description = "Issuer do JWT"
  type        = string
}

variable "jwt_expiration_seconds" {
  description = "Tempo de expiracao do JWT em segundos"
  type        = number
  default     = 86400
}

variable "cors_origins" {
  description = "Origens permitidas para CORS"
  type        = string
  default     = "*"
}
