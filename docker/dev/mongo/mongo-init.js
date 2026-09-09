// =============================================================
//  MongoDB init script — executado apenas na primeira inicialização
//  do container (quando o volume ainda não existe).
//
//  Cria a database de desenvolvimento e a collection 'users'.
//  Os índices únicos em email e username são criados pelo
//  StartupRunner da API Quarkus ao subir a aplicação.
// =============================================================

db = db.getSiblingDB("rede_studio_dev");

db.createCollection("users");

print('[mongo-init] Database "rede_studio_dev" inicializada.');
print('[mongo-init] Collection "users" criada.');
