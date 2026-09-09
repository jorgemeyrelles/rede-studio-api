# =============================================================
#  ec2.tf — EC2 t3.micro com Elastic IP (arquitetura Bootstrap)
#
#  Stack na maquina:
#    - Amazon Linux 2023
#    - Java 21 (Corretto)
#    - Nginx (reverse proxy 80 -> 127.0.0.1:8080)
#    - systemd (gerencia o servico rede-studio)
#    - SSM Agent (acesso sem SSH)
# =============================================================

# AMI Amazon Linux 2023 (kernel-6.x, sempre a mais nova)
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

# Elastic IP estatico (fixo entre reboots)
resource "aws_eip" "api" {
  domain = "vpc"
  tags   = { Name = "rede-studio-eip" }
}

resource "aws_instance" "api" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.api.id]
  iam_instance_profile   = aws_iam_instance_profile.api.name

  # Modo "standard" (padrao) em vez de "unlimited": a instancia usa apenas os
  # creditos de CPU do baseline e NAO gera cobranca de surplus. Isso elimina o
  # maior item da fatura (~US$9,75/mes de "CPUCredits:t3"). Fica seguro porque o
  # tuning de JVM no user-data (SerialGC + TieredStopAtLevel=1) mantem o consumo
  # de CPU ocioso dentro do baseline.
  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_tokens                 = "required"
    http_endpoint               = "enabled"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    # Reduzido de 30 -> 10 GB (SO + app ocupam < 5 GB). Economiza ~US$1,6/mes.
    volume_size = 10
    encrypted   = true
  }

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    aws_region            = var.aws_region
    secrets_id            = aws_secretsmanager_secret.app.id
    artifacts_bucket      = aws_s3_bucket.artifacts.bucket
    mongodb_database      = var.mongodb_database
    jwt_issuer            = var.jwt_issuer
    jwt_expiration_seconds = var.jwt_expiration_seconds
    cors_origins          = var.cors_origins
    app_port              = var.app_port
  })

  # Recriar a EC2 se o user-data mudar (substituicao limpa)
  user_data_replace_on_change = true

  tags = { Name = "rede-studio-api" }

  depends_on = [
    aws_secretsmanager_secret_version.app_initial,
    aws_s3_bucket.artifacts
  ]
}

resource "aws_eip_association" "api" {
  instance_id   = aws_instance.api.id
  allocation_id = aws_eip.api.id
}
