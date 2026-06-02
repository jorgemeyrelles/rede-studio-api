# =============================================================
#  terraform.tfvars — valores reais (Bootstrap)
# =============================================================

aws_region  = "us-east-2"
environment = "prod"

vpc_cidr           = "10.0.0.0/16"
public_subnet_cidr = "10.0.1.0/24"
availability_zone  = "us-east-2a"

ec2_instance_type = "t3.micro"

# bucket globalmente unico
artifacts_bucket_name = "rede-studio-artifacts-970103398279"

mongodb_database = "rede_studio_prod"

jwt_issuer             = "https://api.redestudio.com"
jwt_expiration_seconds = 86400
cors_origins           = "https://redestudio.com,https://www.redestudio.com"
