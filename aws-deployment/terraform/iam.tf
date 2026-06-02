# =============================================================
#  iam.tf — Role da EC2 (Bootstrap): SSM + Secrets + S3 + Logs
# =============================================================

resource "aws_iam_role" "api" {
  name = "rede-studio-api-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "api_ssm" {
  role       = aws_iam_role.api.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "api_secrets" {
  name = "rede-studio-secrets-read"
  role = aws_iam_role.api.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:rede-studio/*"
    }]
  })
}

resource "aws_iam_role_policy" "api_s3_read" {
  name = "rede-studio-s3-artifacts-read"
  role = aws_iam_role.api.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:ListBucket"]
        Resource = [
          aws_s3_bucket.artifacts.arn,
          "${aws_s3_bucket.artifacts.arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy" "api_cw_logs" {
  name = "rede-studio-cw-logs"
  role = aws_iam_role.api.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:CreateLogGroup",
        "logs:DescribeLogStreams"
      ]
      Resource = "arn:aws:logs:${var.aws_region}:*:log-group:/rede-studio/*"
    }]
  })
}

resource "aws_iam_instance_profile" "api" {
  name = "rede-studio-api-profile"
  role = aws_iam_role.api.name
}
