# =============================================================
#  ecr.tf — Repositório ECR para a imagem da API
#
#  Após o apply, use o output ecr_repository_url para o push:
#    aws ecr get-login-password --region us-east-1 | \
#      docker login --username AWS --password-stdin <ecr_url>
#    docker build -f Dockerfile.prod -t rede-studio-api:prod .
#    docker tag rede-studio-api:prod <ecr_url>:latest
#    docker push <ecr_url>:latest
# =============================================================

resource "aws_ecr_repository" "api" {
  name                 = "rede-studio-api"
  image_tag_mutability = "MUTABLE"
  force_delete         = true   # permite destroy mesmo com imagens no repositório

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "rede-studio-api-ecr" }
}

# Mantém apenas as últimas 5 imagens para economizar armazenamento
resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Manter apenas as 5 imagens mais recentes"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = { type = "expire" }
      }
    ]
  })
}
