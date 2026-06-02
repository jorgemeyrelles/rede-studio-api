# =============================================================
#  security_groups.tf — Security Group unico (Bootstrap)
#  ingress 80/443 publico; sem porta 22 (acesso via SSM)
# =============================================================

resource "aws_security_group" "api" {
  name        = "rede-studio-api-sg"
  description = "API EC2: HTTP/HTTPS publico, sem SSH"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP da internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS da internet (futuro TLS)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "rede-studio-api-sg" }
}
