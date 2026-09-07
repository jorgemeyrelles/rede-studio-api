# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Rede Studio API — a Quarkus (Java 21) REST API for managing corporate network infrastructure. Currently implements JWT-based authentication and admin-only user management, backed by MongoDB.

## Common commands

```bash
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

### Local dev stack (Docker Compose)

`docker/dev/docker-compose.yml` runs the full stack: MongoDB, the API, Nginx (reverse proxy on :8090), Prometheus (:9090), and Grafana (:3000). Requires `secrets/` keys and `.env.dev` (already in repo).

```bash
cd docker/dev
docker compose up --build
```

## Architecture

### Request flow

`JwtAuthenticationFilter` (a `@Provider` `ContainerRequestFilter` at `Priorities.AUTHENTICATION`) runs before every request. It whitelists public path prefixes (`/api/auth/register`, `/api/auth/login`, `/q/health`, `/q/metrics`, `/q/openapi`, `/q/swagger-ui`) and, for everything else, checks that a well-formed `Authorization: Bearer` header exists — aborting with a standardized `ErrorResponse` 401 if not. It does **not** verify the token itself; that's delegated to SmallRye JWT via `@RolesAllowed`/`@Authenticated` on the endpoint.

This split exists because `quarkus.http.auth.proactive` is set to `false` in `application.yml`. With proactive auth on, SmallRye JWT would intercept at the Vert.x layer and return its own 401 shape, bypassing the app's standard error format. Setting it to `false` makes JWT verification lazy (triggered only when a secured endpoint is reached), letting the custom filter own the error response format. Keep this in mind when touching auth config — flipping `proactive` back on will silently change 401 response bodies.

Layering: `controllers` → `services` → `repositories`/`components`. Controllers only handle HTTP concerns (status codes, `@RolesAllowed`, OpenAPI annotations) and delegate all logic to services. `GlobalExceptionHandler` (a single `ExceptionMapper<Exception>`) maps every domain exception (`UserAlreadyExistsException` → 409, `InvalidCredentialsException`/`UnauthorizedException` → 401, `NotFoundException` → 404, `NotAllowedException` → 405, anything else → 500) to a uniform `ErrorResponse` JSON body — no stack traces ever reach clients.

### Auth model

- Passwords are hashed with BCrypt via `PasswordHasher`.
- JWTs are RSA-signed (`JwtTokenBuilder`), carrying `sub`/`upn` = user email, `groups` = roles (used directly by `@RolesAllowed`), and `preferred_username`.
- `login` and `register` intentionally throw the same `InvalidCredentialsException` for "user not found" and "wrong password" to prevent user enumeration.
- Roles are plain strings in a `Set<String>` on `UserEntity` (e.g. `USER`, `ADMIN`) — there's no separate role entity/table.

### Startup behavior

`StartupRunner` runs on every boot, before the HTTP server accepts connections:
1. Creates unique indexes on `users.email` and `users.username` (idempotent).
2. Provisions a default admin user from `app.admin.*` config if one doesn't already exist by email (idempotent). In the `master` profile it logs a warning to rotate the password after first login.

### Configuration profiles

`application.yml` defines three layers: base defaults, `%dev` (local Docker Compose — Mongo at `mongo:27017`, JWT keys mounted from `/run/secrets/jwt`, debug logging), and `%master` (AWS + MongoDB Atlas — all secrets required via env vars, JSON logging, Swagger UI disabled). Test overrides live in `src/test/resources/application.properties` (separate test DB name, test JWT keys on the classpath, `retry-writes=false` since Dev Services spins up a standalone Mongo, not a replica set).

### Testing

Integration tests (`AuthControllerTest`, `AuthServiceTest`) are `@QuarkusTest` and exercise the real HTTP stack end-to-end against a real MongoDB via Testcontainers — no mocks. Docker must be running locally to execute them. Tests are ordered with `@TestMethodOrder(OrderAnnotation.class)` where scenarios are dependent (e.g. register before login), and use unique email prefixes per test class to avoid state collisions since the Quarkus test instance (and its DB) is shared across the class.

### Deployment

Production runs on **Oracle Cloud Infrastructure (Always Free)**, not AWS — see the "Arquitetura Cloud" section in `README.md` for the full picture (diagram, component table). `oracle-deployment/terraform/` holds the Terraform (VCN, 2x `VM.Standard.E2.1.Micro` — one for the API+nginx+Redis+vmagent, one for RabbitMQ — IAM dynamic group, Vault, Object Storage, Bastion). `oracle-deployment/deploy-oracle.sh` builds, packages, uploads to Object Storage, and recreates the API VM. MongoDB stays on Atlas (external, unchanged from the AWS days); metrics ship to Grafana Cloud via `vmagent`.

`aws-deployment/` holds the original bootstrap Terraform (VPC, EC2, IAM, Secrets Manager, S3, ECR) and `deploy.sh`/`deploy-bootstrap.sh`. As of 2026-09-05 the cost-generating resources there (EC2, Elastic IP, S3, Secrets Manager, ECR) were destroyed after the Oracle migration was validated — only the VPC/networking and IAM roles remain (kept deliberately: free, no reason to remove). Don't assume `aws-deployment/` still describes a running system.

`docker/prod/entrypoint.sh` + `Dockerfile.prod` build the production image — still used for local prod-mode testing, though the Oracle deploy path runs the JAR directly via systemd rather than this container image. Terraform state and secrets are not committed for either deployment — see `.env.master.example` for the required variables (AWS-era; Oracle's live values are in OCI Vault, not local files).
