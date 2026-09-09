#!/bin/bash
# =============================================================
#  mongodb-init.sh.tpl — User data para instância EC2 MongoDB
#  Executado uma única vez no primeiro boot da instância.
#  Variáveis interpoladas pelo Terraform templatefile():
#    ${mongodb_database}
# =============================================================
set -euo pipefail

# --- Montar e formatar o volume EBS de dados ---
while [ ! -b /dev/xvdf ]; do sleep 2; done
if ! blkid /dev/xvdf; then
  mkfs -t xfs /dev/xvdf
fi
mkdir -p /data/mongodb
echo "/dev/xvdf /data/mongodb xfs defaults,nofail 0 2" >> /etc/fstab
mount -a

# --- Instalar MongoDB 7 ---
cat > /etc/yum.repos.d/mongodb-org-7.repo << 'EOF'
[mongodb-org-7.0]
name=MongoDB Repository
baseurl=https://repo.mongodb.org/yum/amazon/2023/mongodb-org/7.0/x86_64/
gpgcheck=1
enabled=1
gpgkey=https://pgp.mongodb.com/server-7.0.asc
EOF

dnf install -y mongodb-org

# --- Configurar mongod: bind em todos os IPs + dbPath no EBS ---
cat > /etc/mongod.conf << 'EOF'
storage:
  dbPath: /data/mongodb
  journal:
    enabled: true

systemLog:
  destination: file
  logAppend: true
  path: /var/log/mongodb/mongod.log

net:
  port: 27017
  bindIp: 0.0.0.0

security:
  authorization: disabled
EOF

chown -R mongod:mongod /data/mongodb
systemctl enable mongod
systemctl start mongod

# Aguardar MongoDB estar pronto
sleep 10

# --- Criar banco e coleção inicial ---
mongosh --quiet --eval "
  use('${mongodb_database}');
  db.createCollection('users');
  print('Database ${mongodb_database} initialized');
"

echo "[mongodb-init] Setup concluído."
