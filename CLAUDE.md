# CLAUDE.md

Index for Claude Code. Always loaded — keep it short; detail lives in `.claude/skills/` (loaded on demand). Product/infra overview: `README.md`.

## Project overview

Rede Studio API — a Quarkus (Java 21) REST API for managing corporate network infrastructure. Implements JWT-based authentication, admin-only user management, self-service profile (`/api/users/me`), and per-user project/network-state persistence (`/api/projects`) — backed by MongoDB, Redis, and RabbitMQ.

## Working rules (non-negotiable)

- Answer in Portuguese. **Never commit or push** — the user commits.
- **Approval before editing** code, Terraform, cloud-init or config. `terraform apply`, VM recreation, secret changes and production data loads need their own request. CI runs `terraform apply` when a push touches `oracle-deployment/terraform/` — even a comment in `cloud-init-api.sh.tftpl` recreates the VM.
- Multi-phase task → `/nova-tarefa <onprem|cloud|deploy> <name>` → `.claude/plans/<module>/<name>/00-plano.md` → approval. Finished plans go to `.claude/plans/_arquivo/`.
- Verify with `/verificar <module>` (`.claude/tools/verify.sh`) and `.claude/rules/` (`java.md`, `terraform.md`, `seguranca.md`). Read big files by range. Reports ≤ ~15 lines.
- **No Python, ever** — not in the product, not as a working tool. **Data loads are Java migrations** that run on local boot only when the database/collection is missing or empty (`DevSeedRunner` pattern); production loads only via the load endpoint, with approval. Support files live in `.claude/tools/`; anything created in a global dir or cache is deleted at the end of the task. Context comes from this harness and its commands, not from agent memory or caches. Checklist: `.claude/rules/trabalho.md`.

## Modules → skill

| Module | Skill | Network object (back) | Front (`rede-sp-cwb`) |
|---|---|---|---|
| On-prem | `onprem` | `NetworkStateEntity` → `network_states`; `PUT /api/projects/{id}/snapshot` | `NetworkState`, Studio **GoJS** |
| Cloud | `cloud` | `CloudStateEntity` → `cloud_states`; `PUT /api/projects/{id}/cloud-snapshot`; price DB + IAM catalog | `CloudState`, Studio **React Flow** |
| Deploy / local | `deploy` | `docker/dev/docker-compose.yml`; OCI via `oracle-deployment/` (push to `master` → test → deploy); `aws-deployment/` is legacy | GitHub Pages on push to `master` |

`ProjectEntity` holds exactly one of `networkStateId`/`cloudStateId`; the wrong snapshot route → 409. Cross-cutting skills: `seguranca` (JWT, OAuth, idempotency, secrets/Vault, OCI network: LB, Nginx, Bastion), `arquitetura` (layers, LB → Redis → queue → Mongo write pattern, startup), `testes`, `lint-performance`, `observabilidade`, `sync-check`. The front repo has the same module skills for its side.

## Common commands

```bash
# Verification per module: prints only failures + 1 summary line
.claude/tools/verify.sh <onprem|cloud|deploy|all> [--test]

# Dev mode with live reload (Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run all tests (requires Docker — MongoDB is provided via Quarkus Dev Services/Testcontainers)
./mvnw test

# Run a single test class
./mvnw test -Dtest=AuthControllerTest

# Run a single test method
./mvnw test -Dtest=AuthControllerTest#loginReturnsToken

# Package (produces target/quarkus-app/quarkus-run.jar)
./mvnw package

# Package as über-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Native executable (requires GraalVM, or build in container)
./mvnw package -Dnative
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Generate JWT signing keys before running locally (one-time, output goes to `secrets/`, gitignored):

```bash
bash generate-jwt-keys.sh
```

Generate JWT signing keys before running locally (one-time, output in `secrets/`, gitignored): `bash generate-jwt-keys.sh`. Full local stack: skill `deploy` (`cd docker/dev && docker compose up --build`).

## Architecture (essentials)

`controllers` → `services` → `repositories`/`components`; `JwtAuthenticationFilter` + SmallRye JWT (`proactive=false`); `GlobalExceptionHandler` → uniform `ErrorResponse`. Every domain write goes LB → Redis → dedicated queue → MongoDB (skill `arquitetura`). Profiles: base, `%dev` (local compose), `%master` (production). Details: skills `arquitetura`, `seguranca`, `deploy`.
