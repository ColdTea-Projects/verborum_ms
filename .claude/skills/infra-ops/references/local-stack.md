# The Local Stack

## Containers and volumes

The root `docker-compose.yml` brings up RabbitMQ, all three service databases, Keycloak plus its
bootstrap job, Adminer and Mailpit. Named volumes: `rabbitmq_data`, `db_dictionary_data`,
`db_user_data`, `db_market_data`, `keycloak_data`. Every container except `keycloak-bootstrap` and Adminer declares a
health check, so `docker compose ps` reporting healthy is a real signal.

Per-service compose files exist in `ms_dictionary/`, `ms_user/` and `ms_marketplace/` for isolated work — Postgres and
Adminer only, no broker. They clash on host ports with the root file.

## Keycloak — three things that surprise people

**1. The image is custom.** `build: ./keycloak/passwordless-email-code` produces
`verborum-keycloak:local` — stock Keycloak 23 plus the Verborum email-code authenticator SPI.
Themes are bind-mounted from `keycloak/themes`, and `start-dev` disables theme caching, so CSS edits
show on a browser refresh with no rebuild.

**2. The realm import runs only on the first start of an empty volume.** Editing
`keycloak/import/verborum-realm.json` afterwards changes nothing, and console changes are never
written back to the file.

```bash
docker compose down
docker volume rm verborum_ms_keycloak_data
docker compose up -d keycloak
```

**3. `keycloak-bootstrap` applies what the import cannot.** The native import path does not
reliably substitute `${ENV}` inside the realm JSON, so a placeholder is stored as a literal string
and silently breaks login. Identity-provider secrets and real SMTP credentials are therefore
applied *after* the realm is live, by `keycloak/bootstrap/configure.sh` via `kcadm.sh`. It is
idempotent and a no-op locally with an empty `.env`, then exits.

`KC_HOSTNAME_URL` pins the issuer stamped into every token. Unpinned, Keycloak echoes the caller's
Host header, so a phone on a LAN address gets tokens the services reject.

## Running services on the host

```bash
./mvnw -pl ms_dictionary spring-boot:run     # :8085
./mvnw -pl ms_user       spring-boot:run     # :8086
```

`JAVA_HOME` may not be set on the development machine — the JDKs live in `~/.jdks/`, and `mvnw`
fails with "The JAVA_HOME environment variable is not defined correctly" until it is exported per
shell.

Two environment variables worth knowing locally:

- **`KEYCLOAK_ADMIN_CLIENT_SECRET`** — unset, deleting a profile leaves the Keycloak account alive
  and logs a WARN. Set it to the value in the realm import to exercise the real path.
- **`EMAIL_CODE_ENABLED`** — defaults to true; set false for password-only login.

## Testing from a phone or another machine

Set all three to the same LAN origin, then restart Keycloak and the services:

```
KEYCLOAK_HOSTNAME_URL=http://<lan-ip>:8180                                           # keycloak
KEYCLOAK_ISSUER_URI=http://<lan-ip>:8180/realms/verborum                             # each service
KEYCLOAK_JWK_SET_URI=http://<lan-ip>:8180/realms/verborum/protocol/openid-connect/certs
```

Otherwise every request fails with a 401 that looks like a broken token but is a configuration
mismatch.

## Adding infrastructure for a new service

1. **Per-service compose file** — Postgres 14-alpine + Adminer on the host ports assigned in
   `docs/agent/verborum.md`, checked against the port map for a clash.
2. **Root compose** — add the database too, with a named volume and a health check, following
   `db_dictionary` as the template. A service database that only exists in the per-service file is
   invisible when the full stack is running.
3. **`.env.example`** — a commented placeholder for every new environment variable, matching the
   `${VAR:default}` added in `application.properties`.

```yaml
db_market:
  image: postgres:14-alpine
  container_name: verborum-db-market
  ports:
    - "5434:5432"
  environment:
    POSTGRES_USER: coldtea
    POSTGRES_PASSWORD: qwerty
    POSTGRES_DB: vdbmarket
  volumes:
    - db_market_data:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U coldtea -d vdbmarket"]
    interval: 10s
    timeout: 5s
    retries: 5
```
