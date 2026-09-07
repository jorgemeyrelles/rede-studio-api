# =============================================================
#  iam-s3-read-policy.tf — extraida de iam.tf em 2026-09-05
#
#  Ja destruida junto com o bucket (referenciava aws_s3_bucket.artifacts.arn).
#  Movida pra ca por dependencia, nao por decisao de desmontar IAM -- ver
#  README.md nesta pasta.
# =============================================================

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
