# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Rede Studio API — a Quarkus (Java 21) REST API for managing corporate network infrastructure. Implements JWT-based authentication, admin-only user management, self-service profile (`/api/users/me`), and per-user project/network-state persistence (`/api/projects`) — backed by MongoDB, Redis, and RabbitMQ.

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

`JwtAuthenticationFilter` (a `@Provider` `ContainerRequestFilter` at `Priorities.AUTHENTICATION`) runs before every request. It whitelists public path prefixes (`/api/auth/register`, `/api/auth/login`, `/api/auth/oauth`, `/q/health`, `/q/metrics`, `/q/openapi`, `/q/swagger-ui`) and, for everything else, checks that a well-formed `Authorization: Bearer` header exists — aborting with a standardized `ErrorResponse` 401 if not. It does **not** verify the token itself; that's delegated to SmallRye JWT via `@RolesAllowed`/`@Authenticated` on the endpoint. **This whitelist is separate from `@PermitAll`** — a new public endpoint needs both, or it 401s here before JAX-RS is even reached (hit this while adding the OAuth login endpoint, 2026-09-09).

This split exists because `quarkus.http.auth.proactive` is set to `false` in `application.yml`. With proactive auth on, SmallRye JWT would intercept at the Vert.x layer and return its own 401 shape, bypassing the app's standard error format. Setting it to `false` makes JWT verification lazy (triggered only when a secured endpoint is reached), letting the custom filter own the error response format. Keep this in mind when touching auth config — flipping `proactive` back on will silently change 401 response bodies.

Layering: `controllers` → `services` → `repositories`/`components`. Controllers only handle HTTP concerns (status codes, `@RolesAllowed`, OpenAPI annotations) and delegate all logic to services. `GlobalExceptionHandler` (a single `ExceptionMapper<Exception>`) maps every domain exception (`UserAlreadyExistsException` → 409, `InvalidCredentialsException`/`UnauthorizedException` → 401, `NotFoundException` → 404, `NotAllowedException` → 405, anything else → 500) to a uniform `ErrorResponse` JSON body — no stack traces ever reach clients.

### Auth model

- Passwords are hashed with BCrypt via `PasswordHasher`.
- JWTs are RSA-signed (`JwtTokenBuilder`), carrying `sub`/`upn` = user email, `groups` = roles (used directly by `@RolesAllowed`), and `preferred_username`.
- `login` and `register` intentionally throw the same `InvalidCredentialsException` for "user not found" and "wrong password" to prevent user enumeration.
- Roles are plain strings in a `Set<String>` on `UserEntity` (e.g. `USER`, `ADMIN`) — there's no separate role entity/table.
- The JWT also carries a `uid` claim (the user's Mongo `_id`, hex string) alongside `sub`/`upn` (email). It's generated in Java (`new ObjectId()`) *before* the user document exists in Mongo — see the write pattern below — so that other entities can reference `users._id` directly (`ProjectEntity.ownerId`, etc.) without an email-based lookup. Resolve the caller's identity via `jwt.getClaim("uid")`, not `jwt.getSubject()`, whenever a domain entity's ownership is ID-based.
- **Social login (Google/Microsoft)**: `POST /api/auth/oauth/{provider}` — the frontend obtains an ID token client-side from the provider's own SDK; this API only verifies it (`OAuthTokenVerifier`, via the provider's JWKS — RS256 signature, `iss`, `aud` against `app.oauth.{google,microsoft}-client-id`, `exp`) and issues the same kind of JWT as password login. No client secret lives here — this app never talks to the provider's token endpoint. `UserEntity.oauthProvider`/`oauthProviderId` are set for an OAuth-authenticated account and `null` for a password account (exactly one of `passwordHash` or the OAuth pair is ever populated); a unique index on `(oauth_provider, oauth_provider_id)` is **partial** (`$type: "string"`) specifically because every password-only user also has these two fields present-but-null, and a non-partial unique index would treat all of those nulls as colliding. `AuthService#loginOrRegisterOAuth` auto-links an OAuth identity onto an existing password account with the same email (the provider already proved ownership of that email) rather than erroring or creating a duplicate.

### Write/read data-access pattern — mandatory for new domain entities

Every insert/update/delete of a domain entity (not just user registration, which established this pattern first) must go through **LB → Redis → queue → MongoDB**, never a direct synchronous Mongo write from the request thread:

1. **Write**: the endpoint pre-generates any needed `ObjectId`(s) in Java (no waiting on Mongo to assign one), updates Redis synchronously with the new state (this is what makes a read immediately after a write consistent, without waiting on the queue), publishes a message to a **dedicated queue per operation** (mirror `user.registration`/`UserRegistrationConsumer` — one exchange/queue/consumer method per operation, e.g. `project.create`, `project.rename`, `project.snapshot`, `project.delete` — not one shared queue with an operation-type discriminator), and returns the response to the client immediately, without waiting for the consumer to persist to Mongo.
2. **Read**: check Redis first (cache-aside: `entity:{id}` / `entities:owner:{ownerId}`-style keys, reasonable TTL e.g. 24h); on a miss, query Mongo and repopulate Redis. Always re-verify ownership/ACL against the cached value's own fields before returning — Redis has no access control of its own.
3. Idempotency claims (like `IdempotencyService` for registration) are only worth adding when the write has a natural duplicate-content key (email/username-style). Don't add one just to follow the pattern if there's nothing meaningful to dedupe on.

An OCI Load Balancer (TCP passthrough, 80/443, added 2026-09) now sits in front of `nginx` for a stable public IP and health-check-based alerting, but there's still only one backend VM behind it (no horizontal scaling) and `nginx` still does the actual TLS termination, rate limiting and reverse-proxying to Quarkus — don't assume this pattern changes when reasoning about the write/read pattern above.

Existing synchronous methods on `UserService` (`patchUser`, `changePassword`, `deleteByEmail`) predate this rule and haven't been retrofitted — don't take that as precedent for new code, and don't silently retrofit them as a side effect of an unrelated change; that's its own separate task.

### Startup behavior

`StartupRunner` runs on every boot, before the HTTP server accepts connections:
1. Creates unique indexes on `users.email` and `users.username` (idempotent).
2. Provisions a default admin user from `app.admin.*` config if one doesn't already exist by email (idempotent). In the `master` profile it logs a warning to rotate the password after first login.

### Configuration profiles

`application.yml` defines three layers: base defaults, `%dev` (local Docker Compose — Mongo at `mongo:27017`, JWT keys mounted from `/run/secrets/jwt`, debug logging), and `%master` (AWS + MongoDB Atlas — all secrets required via env vars, JSON logging, Swagger UI disabled). Test overrides live in `src/test/resources/application.properties` (separate test DB name, test JWT keys on the classpath, `retry-writes=false` since Dev Services spins up a standalone Mongo, not a replica set).

### Testing

Integration tests (`AuthControllerTest`, `AuthServiceTest`, `AuthControllerOAuthTest`, `AuthServiceOAuthTest`) are `@QuarkusTest` and exercise the real HTTP stack end-to-end against a real MongoDB via Testcontainers — no mocks (this project has no Mockito dependency). Docker must be running locally to execute them. Tests are ordered with `@TestMethodOrder(OrderAnnotation.class)` where scenarios are dependent (e.g. register before login), and use unique email prefixes per test class to avoid state collisions since the Quarkus test instance (and its DB) is shared across the class.

The OAuth tests avoid real Google/Microsoft network calls via `FakeOAuthTokenVerifier` — a `@Alternative @Priority(1)` CDI bean under `src/test/java` that Quarkus auto-activates in tests, overriding `OAuthTokenVerifier`. This is the project's pattern for swapping out an external-network component in tests without a mocking library; follow it for any future component with the same shape (an external HTTP-dependent verifier/client).

**Local `mvn test` needs Redis and RabbitMQ reachable at their default ports, not just Docker running**: only MongoDB gets Dev Services auto-provisioning here — Redis/RabbitMQ don't activate it because `application.yml` already gives their host config an explicit default (`localhost:6379`/`localhost:5672`), which Dev Services treats as "already configured" rather than "unset". Without them, any test that registers a user (password or OAuth) fails on `IdempotencyService.claim` with a connection-refused error. Fastest fix without the full `docker/dev/` compose stack: `docker run -d --rm -p 6379:6379 redis:7-alpine` and `docker run -d --rm -p 5672:5672 rabbitmq:3.13-management-alpine` (give RabbitMQ ~15-20s to accept connections). Re-running tests without `redis-cli FLUSHALL` in between reuses the previous run's idempotency claims (10 min TTL) and spuriously fails registration tests with `UserAlreadyExistsException`.

**Gap, deliberate for now**: `ProjectController`/`MeController`/the Redis+RabbitMQ write path have no automated tests yet — deferred by product decision during the front↔back integration sprint, not an oversight. Verified manually end-to-end instead (local Docker stack and production) — see git history around 2026-09-08 for the manual test transcripts. Don't take the untested state as a signal these are less load-bearing; add `@QuarkusTest` coverage here as its own task when picked up, following the `AuthControllerTest` pattern (ownership isolation between two users is the one scenario that most needs a real test, not just manual poking).

### Deployment

Production runs on **Oracle Cloud Infrastructure (Always Free)**, not AWS — see the "Arquitetura Cloud" section in `README.md` for the full picture (diagram, component table). `oracle-deployment/terraform/` holds the Terraform (VCN, an OCI Load Balancer in front — TCP passthrough on 80/443, added 2026-09 — 2x `VM.Standard.E2.1.Micro` — one for the API+nginx+Redis+vmagent, one for RabbitMQ — IAM dynamic group, Vault, Object Storage, Bastion, an OCI Monitoring alarm + Notifications topic that emails on backend-unhealthy). MongoDB stays on Atlas (external, unchanged from the AWS days); metrics ship to Grafana Cloud via `vmagent`.

**CI/CD (`.github/workflows/ci.yml`, GitHub Actions)**: push to `master` (via PR merge) runs `test`, then `deploy`. `deploy` does **not** recreate the VM by default — `oracle-deployment/deploy-oracle.sh` builds, uploads the JAR to Object Storage, and restarts the `rede-studio` systemd service over SSH via a Bastion Port Forwarding session (dedicated CI SSH key: `ci_ssh_public_key` var / `CI_SSH_PRIVATE_KEY` secret, never a personal key). `terraform apply` (no `-replace`, `replace_triggered_by` decides what actually needs recreating) only runs in CI when the push touches `oracle-deployment/terraform/` (checked via `git diff` against the previous commit). This split exists because recreating the VM on every deploy used to force a fresh Certbot issuance each time too — see the cert-persistence Caveat below for the incident that motivated it. Running `deploy-oracle.sh` locally follows the same rule: it never calls Terraform, only build+upload+SSH-restart — a `.tf` change still needs a deliberate `terraform apply` run separately, same as ever.

**Production URL: `https://rede-studio-api.duckdns.org`** (TLS since 2026-09-08 — see below). Plain HTTP on the reserved IP (`163.176.93.103`) now 301-redirects to it. Swagger UI is **not** reachable in production (`swagger-ui.always-include: false` in the `%master` profile of `application.yml`, deliberate) — only via local dev (`http://localhost:8080/q/swagger-ui/`).

**Bastion Port Forwarding works reliably — use it for shell access.** (Supersedes an earlier, more pessimistic version of this note.) Managed SSH (`oci bastion session create-managed-ssh`) and `oci instance-agent command create` still hang indefinitely (`CREATING`/`ACCEPTED` forever) on this shape — root cause never confirmed, suspected OCA agent resource starvation, rebooting doesn't fix it. But **Port Forwarding sessions don't depend on the OCA agent** and work fine: `oci bastion session create-port-forwarding --bastion-id <id> --target-resource-id <instance_id> --target-port 22 --target-private-ip <private_ip> --ssh-public-key-file ~/.ssh/<key>.pub --session-ttl <≤1800>`, then a local `ssh -N -L <local_port>:<private_ip>:22 <session_id>@host.bastion.<region>.oci.oraclecloud.com` tunnel, then `ssh -p <local_port> ubuntu@127.0.0.1`. `oracle-deployment/deploy-oracle.sh` does exactly this on every deploy. For infra/cloud-init changes specifically, editing the template and running `terraform apply` is still simplest (`terraform_data.cloud_init_trigger`, a `filesha256` of the template, auto-triggers instance replacement via `replace_triggered_by`) — but day-to-day shell access for diagnostics no longer requires that route. The reserved public IP (`oci_core_public_ip.api`) is a separate resource from the instance and survives replacement, re-attaching automatically — DNS (DuckDNS) never needs to change across a redeploy. `oci compute console-history capture`/`get-content` gives *some* visibility (kernel/systemd boot log) but does **not** reliably capture the bootstrap script's own `echo`/diagnostic output — prefer SSH via Bastion, or testing via the actual HTTP(S) endpoint.

**TLS (Certbot), embedded in cloud-init**: nginx runs as a Docker container (not natively on the VM, so the `certbot --nginx` plugin doesn't apply), so certificate issuance is scripted directly into `cloud-init-api.sh.tftpl` step `[6/9]`: restore a previous certificate from Object Storage if one exists (see Caveat below), then `docker run --rm certbot/certbot certonly --webroot` against nginx's already-running `/.well-known/acme-challenge/` location (Certbot no-ops if the restored cert is still valid), then the script rewrites `nginx.conf` with the HTTPS server block + an HTTP→HTTPS redirect and restarts the nginx container. Renewal is a `cron.d` entry (2x/day, `certbot renew --deploy-hook` only restarts nginx and re-backs-up the cert when a renewal actually happens). Controlled by two `tfvars`: `duckdns_hostname`, `letsencrypt_email` — leaving `duckdns_hostname` empty skips TLS entirely and the deploy stays HTTP-only (non-fatal by design, never blocks the app deploy itself).

**Cert persistence (fixed 2026-09-10 — this used to be an open Caveat here)**: `/opt/rede-studio/certbot/etc` lives on the instance's ephemeral boot volume, so every instance replacement used to force a **fresh** Let's Encrypt issuance — and Let's Encrypt only allows 5 per exact domain per 168h. Several VM recreations in one debugging session hit that limit for real and took production HTTPS down for about a day until the window cleared. Fixed by backing up `/opt/rede-studio/certbot/etc` to the `rede-studio-artifacts` Object Storage bucket (object `letsencrypt-backup.tar.gz`) after every successful issuance/renewal, and restoring it on boot *before* calling `certbot certonly` — a restored, still-valid cert makes Certbot skip contacting Let's Encrypt entirely, so routine VM recreations (including CI's infra-change path above) no longer touch the rate limit. Requires the dynamic group's Object Storage grant to be `manage`, not just `read` (`iam.tf`) — a `read`-only grant will fail silently on the backup upload step.

`aws-deployment/` holds the original bootstrap Terraform (VPC, EC2, IAM, Secrets Manager, S3, ECR) and `deploy.sh`/`deploy-bootstrap.sh`. As of 2026-09-05 the cost-generating resources there (EC2, Elastic IP, S3, Secrets Manager, ECR) were destroyed after the Oracle migration was validated — only the VPC/networking and IAM roles remain (kept deliberately: free, no reason to remove). Don't assume `aws-deployment/` still describes a running system.

`docker/prod/entrypoint.sh` + `Dockerfile.prod` build the production image — still used for local prod-mode testing, though the Oracle deploy path runs the JAR directly via systemd rather than this container image. Terraform state and secrets are not committed for either deployment — see `.env.master.example` for the required variables (AWS-era; Oracle's live values are in OCI Vault, not local files).
