---
name: infra-ops
description: Running and operating the Verborum stack — Docker Compose infrastructure, the port map, .env and secrets, running services, and inspecting databases and queues. Use when starting the stack, changing compose or environment config, verifying something by hand, or diagnosing a local failure.
---

# Infrastructure and Ops

How the stack runs. Build commands: `maven`. Auth model: `security`. Manual test verification:
`integration-testing`. Long-form: `docs/ops/local-development.md` and
`docs/ops/dockerization-and-environments.md`.

## Quick start

```bash
docker compose up -d          # from the repo root
docker compose ps             # everything should report healthy
./mvnw -pl ms_dictionary spring-boot:run
```

**Docker runs infrastructure only. The Spring services run on the host.** That is deliberate — it
keeps rebuilds fast, allows attaching a debugger, and preserves hot reload. There are no service
Dockerfiles yet; the custom Keycloak image is the exception.

## The rule that bites

**Run the root compose or a per-service compose — never both.** `ms_dictionary/` and `ms_user/`
each have their own compose file (Postgres + Adminer only, no broker) for working on one service in
isolation, and they bind the **same host ports** as the root file. Anything involving events or
auth needs the root file.

## Port map

| Container | Image | Host port | Credentials |
|---|---|---|---|
| `rabbitmq` | `rabbitmq:3-management` | 5672 AMQP, 15672 UI | `verborum` / `verborum` |
| `db_dictionary` | `postgres:14-alpine` | 5432 → `vdbdictionary` | `coldtea` / `qwerty` |
| `db_user`, `db_market` | `postgres:14-alpine` | 5433 → `vdbprofile`, 5434 → `vdbmarket` | `coldtea` / `qwerty` |
| `keycloak` | `verborum-keycloak:local` (built) | 8180 | `admin` / `admin` |
| `keycloak-bootstrap` | Keycloak 23 | — | runs once, then exits |
| `admin` | `adminer` | 8080 | — |
| `mailpit` | `axllent/mailpit:latest` | 1025 SMTP, 8025 UI | — |

On the host: ms_dictionary 8085, ms_user 8086, ms_marketplace 8087 (ms_gateway 8080 when built).
**Known clash:** Adminer holds 8080, which the roadmap also assigns to `ms_gateway` — move Adminer
to 8090 before the gateway arrives. Container layout, the three Keycloak surprises, and what a new
service must add are in [references/local-stack.md](references/local-stack.md).

## Configuration and secrets

Every host, port and credential is `${VAR:local-default}` in properties and `${VAR:-default}` in
compose, so **local development needs no environment variables at all**.

- `.env` at the repo root holds real values and is **git-ignored**; `.env.example` is the committed
  template with placeholders only.
- Never bake a secret into an image, and never put one — or an `${ENV}` placeholder for one — into
  the Keycloak realm JSON.
- The committed defaults are local-dev placeholders and must be rotated before any public
  deployment.

## Getting a token

Every endpoint requires a JWT. The dev-only client skips the browser:

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/dictionaries/$SUB
```

Dev accounts: `testuser`/`testuser` (role `user`), `testadmin`/`testadmin` (`user` + `admin`);
ownership rules need **two** tokens. Extracting the subject, publishing and observing events, and
checking the dead-letter queue are in
[references/verification-recipes.md](references/verification-recipes.md).

## Inspecting state

- **Databases** — Adminer at http://localhost:8080, server `db_dictionary` or `db_user` (the
  **compose service names**, not `localhost`), or via `docker exec … psql`.
- **Queues** — Management UI at http://localhost:15672. A growing `verborum.dead-letter` means a
  consumer is failing, not that delivery is broken.
- **Mail** — Mailpit at http://localhost:8025 captures verification and email-code mail.

**Test-data hygiene:** there is no seed script, so manual verification means creating rows through
the API and deleting them afterwards. `DELETE /users/{userId}` is the cleanest reset — it cascades,
publishes `user.deleted`, and removes the Keycloak account when the admin secret is set.

## Deployment

Not built yet. The plan — three compose topologies, multi-stage Dockerfiles, reverse proxy and TLS,
registry and CI, backups — is in
[references/containerization-plan.md](references/containerization-plan.md). Read it before adding
anything that would contradict it.

## Troubleshooting

Symptom-to-cause table in [references/troubleshooting.md](references/troubleshooting.md). The three
most common: `JAVA_HOME` unset, the compose stack down while a full-context test runs, and both
compose files running at once.
