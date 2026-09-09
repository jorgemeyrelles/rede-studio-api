# =============================================================
#  mongodb.tf — MongoDB Atlas (free tier M0)
#
#  Não há recursos AWS aqui. O MongoDB é gerenciado pelo Atlas.
#  A URI de conexão fica armazenada no Secrets Manager:
#    aws secretsmanager get-secret-value --secret-id rede-studio/app
# =============================================================
