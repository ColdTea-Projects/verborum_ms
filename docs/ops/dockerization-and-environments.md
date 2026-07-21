# Verborum — Dockerization & Environments

**Target location:** backend repo `verborum_ms/docs/ops/dockerization-and-environments.md`.
**Related:** Frontend–Backend Integration (`docs/integration/`), Android Development
(`verborum_android/docs/`), backend agent docs (`docs/agent/`).

---

## 1. Purpose

How Verborum is containerized, how the local machine differs from a real server, and what changes
between dev, staging, and production. It also records **which decisions create lock-in and which are
freely reversible** — so work can proceed without over-committing early.

Guiding principle: **local development and production are different problems.** The topology that
makes development fast is not the topology that makes production safe. They share Dockerfiles and
configuration mechanisms, not layout.

---

## 2. Current State

Docker today covers **infrastructure only**. The Spring services run on the host via
`./mvnw spring-boot:run`.

| Running in Docker (root `docker-compose.yml`) | Running on host |
|---|---|
| `rabbitmq` — 5672 / 15672 (management UI) | `ms_dictionary` — 8085 |
| `db_dictionary` — Postgres, `vdbdictionary`, host 5432 | `ms_user` — 8086 (scaffolded) |
| `db_user` — Postgres, `vdbprofile`, host 5433 | |
| `admin` — Adminer, 8080 | |

There are **no Dockerfiles in the repository yet**. Per-service compose files (`ms_dictionary/`,
`ms_user/`) exist for isolated work; they clash on host ports with the root file, so only one may run
at a time.

**This is the correct local setup and should not change.** Services on the host keep rebuilds fast,
allow debugger attachment, and preserve hot reload.

**Known port clash to resolve:** Adminer occupies 8080, which the roadmap also assigns to
`ms_gateway` (P5-01). Move Adminer to 8090 before the gateway arrives.

---

## 3. Three Topologies

| | Topology A — Local Dev | Topology B — Local Full-Stack | Topology C — Server |
|---|---|---|---|
| Purpose | Daily coding | Verify images before deploying | Real deployment |
| Services | Host (`mvnw`) | Containers | Containers |
| Infra | Containers | Containers | Containers |
| Compose | `docker-compose.yml` | `+ docker-compose.services.yml` | `+ docker-compose.prod.yml` |
| Exposed | All ports on localhost | All ports on localhost | 80/443 only |
| Use when | Always, by default | Before a deploy, or debugging container-only issues | Staging & production |

The three share the same Dockerfiles. Only compose layering and environment variables differ.

---

## 4. Container Inventory (target state)

| Container | Image | Public? | Notes |
|---|---|---|---|
| `caddy` (or nginx) | `caddy:2-alpine` | **Yes — 80/443** | TLS termination, sole ingress |
| `ms_gateway` | built | No | Spring Cloud Gateway; routes to services |
| `ms_dictionary` | built | No | 8085 internal |
| `ms_user` | built | No | 8086 internal |
| `ms_marketplace` | built | No | 8087 internal (BE Phase 4) |
| `ms_autofil` | built | No | V2 |
| `keycloak` | `quay.io/keycloak/keycloak:23` | No (via gateway) | Realm `verborum` |
| `postgres ×1–3` | `postgres:14-alpine` | No | §12 |
| `rabbitmq` | `rabbitmq:3-management` | No | Management UI never public |
| `adminer` | `adminer` | **dev only** | Never deployed to staging/prod |

**Rule: only the reverse proxy publishes ports in production.** Everything else communicates over the
internal Docker network by service name.

---

## 5. Dockerfile Strategy

One Dockerfile per service, multi-stage so the runtime image ships a JRE rather than a full JDK plus
Maven (~200 MB instead of ~600 MB):

```dockerfile
# ms_dictionary/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B        # cached layer — dependencies change rarely
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

Notes:
- `-XX:MaxRAMPercentage=75` makes the JVM respect the container memory limit instead of the host's
  total RAM. Without it, containers get OOM-killed under limits.
- Run as a non-root user.
- Tests are skipped in the image build; they belong in CI, not in the deploy path.
- **Refinement when build times bite:** Spring Boot layered jars
  (`java -Djarmode=layertools -jar app.jar extract`) split dependencies from application classes so
  only the thin application layer rebuilds on a code change.
- Add a `.dockerignore` (`target/`, `.git`, `.idea`, `*.md`) to keep build context small.

---

## 6. Compose File Layering

Rather than one file serving every purpose, stack overrides:

```
docker-compose.yml           # infra only — the daily local file (current)
docker-compose.services.yml  # adds the Spring services as containers
docker-compose.prod.yml      # production overrides
```

```bash
# Daily development (unchanged)
docker compose up -d

# Full local stack, everything containerized
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build

# Server
docker compose -f docker-compose.yml -f docker-compose.services.yml \
  -f docker-compose.prod.yml up -d
```

`docker-compose.services.yml` (excerpt):
```yaml
services:
  ms_dictionary:
    build: ./ms_dictionary
    environment:
      DB_URL: jdbc:postgresql://db_dictionary:5432/vdbdictionary
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_USER: ${RABBITMQ_USER}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
    depends_on:
      db_dictionary: { condition: service_healthy }
      rabbitmq: { condition: service_healthy }
    ports:
      - "8085:8085"   # removed again by the prod override
```

`docker-compose.prod.yml` (excerpt):
```yaml
services:
  ms_dictionary:
    ports: []                    # no public port — reachable only inside the network
    restart: unless-stopped
    deploy:
      resources:
        limits: { memory: 640M }
    environment:
      SPRING_PROFILES_ACTIVE: prod
  admin:
    profiles: ["donotstart"]     # Adminer never runs in production
  db_dictionary:
    ports: []                    # database never published
```

---

## 7. Configuration & Secrets — the only real lock-in

Everything else in this document is reversible. This is not, and it gets more expensive the longer it
waits.

**The problem.** Services would hardcode host and credentials
(`spring.datasource.url=jdbc:postgresql://localhost:5432/vdbdictionary`, `password=qwerty`,
`spring.rabbitmq.host=localhost`). Inside a container, `localhost` means *that container* — the
database will not be found. And committed credentials cannot go to a public server.

**The fix — costs nothing, changes no local behavior**, because the defaults are today's values:
```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/vdbdictionary}
spring.datasource.username=${DB_USER:coldtea}
spring.datasource.password=${DB_PASSWORD:qwerty}
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.username=${RABBITMQ_USER:verborum}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:verborum}
```

> **Status (backend):** RESOLVED. Every host and credential in both services' `application.properties`
> is already `${ENV_VAR:current-value}` — datasource URL/user/password, RabbitMQ host/port/user/
> password, server port, and the Keycloak issuer/JWK-set/realm/auth-server/admin-client-id. Defaults
> are the previous literals, so local dev is unchanged and no env vars are required to run today.

**Secrets handling.**
- A `.env` file at the repo root, **git-ignored**, holds real values; commit a `.env.example` with
  placeholder values as documentation.
- Compose reads `.env` automatically for `${VAR}` interpolation.
- Never bake secrets into images — they persist in image layers.
- Rotate the development `coldtea`/`qwerty` pair before any public deployment; Keycloak client
  secrets and the RabbitMQ password must be unique per environment.

```
# .env.example
DB_USER=coldtea
DB_PASSWORD=change-me
RABBITMQ_USER=verborum
RABBITMQ_PASSWORD=change-me
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=change-me
KEYCLOAK_CLIENT_SECRET=change-me
```

---

## 8. Environments

### 8.1 Definition

| | dev | staging | prod |
|---|---|---|---|
| Runs on | Developer machine | Server (or same server, separate stack) | Server |
| Topology | A or B (§3) | C | C |
| API origin | `http://localhost:808x`, or LAN IP for a physical phone | `https://staging-api.verborum.<domain>` | `https://api.verborum.<domain>` |
| TLS | None (cleartext) | Let's Encrypt | Let's Encrypt |
| Keycloak | `http://localhost:8180`, realm `verborum` | staging realm, own clients & secrets | prod realm, own clients & secrets |
| Database | Throwaway; volumes may be wiped freely | Realistic but disposable seed data | Real data, backed up (§10) |
| Adminer | Yes, 8090 | No | No |
| Published DB / RabbitMQ ports | Yes | No | No |
| Actuator | `include=*` acceptable | `health,info` only | `health,info` only |
| Log level | DEBUG for `de.coldtea` | INFO | INFO, WARN for noisy libs |
| Restart policy | none | unless-stopped | unless-stopped |
| Image source | Built locally | Registry tag `staging` / commit SHA | Registry tag `vX.Y.Z` |

**Actuator exposure is a live security item (roadmap P3-06):**
`management.endpoints.web.exposure.include=*` combined with a `permitAll` matcher would expose
`/actuator/env`, leaking datasource credentials. Restrict to `health,info` outside dev.

### 8.2 Spring profiles
Keep one `application.properties` with safe local defaults, then `application-staging.properties` and
`application-prod.properties` for overrides. Select with `SPRING_PROFILES_ACTIVE`, injected by
compose. Environment variables still win over profile files, so secrets stay outside the repository.

### 8.3 Is staging worth it?
Early on, dev + prod is a defensible simplification. Staging becomes genuinely valuable at the point
**mobile clients ship**, because a released Android build cannot be hotfixed the way a server can: a
bad API change reaches users who may stay on that version for weeks. A staging stack lets a release
candidate of the app run against a production-shaped backend first.

Cheap approach: the same server, a second compose project with its own volumes, subdomain, and
Keycloak realm. It roughly doubles memory needs — see §11.

---

## 9. Networking, Ports & Exposure

Inside Docker, services address each other by compose service name; no ports need publishing for
internal traffic:
```
jdbc:postgresql://db_dictionary:5432/vdbdictionary
spring.rabbitmq.host=rabbitmq
http://keycloak:8080/realms/verborum
http://ms_dictionary:8085
```

**Ports published in production: 80 and 443 only.** Everything else — services, databases, RabbitMQ
management, Keycloak admin — stays on the internal network. Administrative access happens over an SSH
tunnel:
```bash
ssh -L 15672:localhost:15672 user@server   # RabbitMQ UI, locally, no public exposure
```

Local port map (after resolving the Adminer clash):

| Port | Service |
|---|---|
| 8080 | `ms_gateway` (from BE Phase 5) |
| 8085 / 8086 / 8087 | dictionary / user / marketplace |
| 8090 | Adminer (moved from 8080) |
| 8180 | Keycloak |
| 5432 / 5433 / 5434 | Postgres: dictionary / user / market |
| 5672 / 15672 | RabbitMQ AMQP / management |

---

## 10. Data Persistence & Backups

Named volumes already exist for the databases and RabbitMQ; Keycloak needs one too, or realm
configuration is lost on every recreate.
```yaml
volumes:
  db_dictionary_data:
  db_user_data:
  db_market_data:
  rabbitmq_data:
  keycloak_data:
  caddy_data:   # TLS certificates — losing this re-issues certs and can hit rate limits
```

**Backups (production, non-optional).** A nightly `pg_dump` per database, retained off-server:
```bash
docker exec verborum-db-dictionary pg_dump -U "$DB_USER" vdbdictionary \
  | gzip > "backup-dictionary-$(date +%F).sql.gz"
```

Because user dictionaries are the entire product, restore should be rehearsed at least once — an
untested backup is a hypothesis. Note that Android clients hold their own local copy, which softens
(but does not remove) the risk.

---

## 11. Resource Planning

Count JVMs: `ms_gateway`, `ms_dictionary`, `ms_user`, `ms_marketplace` — plus Keycloak, which is also
a JVM and the heaviest single consumer.

| Component | Realistic allocation |
|---|---|
| 4 Spring services | ~400–640 MB each |
| Keycloak | ~512 MB–1 GB |
| Postgres (each) | ~100–200 MB |
| RabbitMQ | ~150–250 MB |
| Caddy | ~30 MB |

**2 GB will not hold this. 4 GB is a workable minimum, 8 GB is comfortable** — and staging on the
same host effectively doubles the requirement. Always set explicit memory limits together with
`-XX:MaxRAMPercentage`, so one runaway service cannot starve the rest.

---

## 12. Database Topology — one Postgres or several?

| | Container per service (current local shape) | Single Postgres, multiple databases |
|---|---|---|
| Isolation | Strongest — separate processes | Logical: separate DBs + separate DB users |
| Memory | ~3× the overhead | One instance |
| Backup/upgrade | Three of everything | One routine |
| Scaling later | Each moves independently | Split when needed |

For a single-VPS deployment, **one Postgres instance with three databases and a distinct user per
service** preserves the isolation that matters (no service can read another's tables) while cutting
memory and operational work. The purist arrangement pays off when services are scaled or hosted
separately.

This is **a connection-string change in either direction** — genuinely reversible, and worth deciding
by measurement rather than principle.

---

## 13. Reverse Proxy & TLS

Caddy is recommended for automatic Let's Encrypt certificates and minimal configuration:
```
api.verborum.example.com {
    reverse_proxy ms_gateway:8080
}
auth.verborum.example.com {
    reverse_proxy keycloak:8080
}
```

Two DNS names — one for the API gateway, one for Keycloak — because clients perform OAuth redirects
against Keycloak directly (see the Integration document §6).

**CORS is the gateway's job, not the proxy's** (Integration §7). Web clients need allowed origins
configured per environment in Spring Cloud Gateway, and matching *Web Origins* entries on the Keycloak
realm clients. Mobile clients are unaffected — which is why this only becomes urgent when the KMP web
app starts.

TLS is also what unblocks the Android release: the app currently permits cleartext globally, an
exception that must be narrowed to dev builds once an HTTPS origin exists.

---

## 14. Image Registry & Deployment

**Build images in CI, not on the server.** Building on the server requires a JDK, Maven, source code,
and enough RAM to run a Maven build alongside the running stack — all avoidable.

Recommended flow, with GitHub Container Registry (free for public repositories):
```
git push → GitHub Actions: mvn verify → docker build → push ghcr.io/coldtea-projects/ms_dictionary:<sha>
         → server: docker compose pull && docker compose up -d
```

- Tag with the **commit SHA** (or a semver tag for production), never only `latest` — you cannot roll
  back to `latest`.
- Rollback is `docker compose up -d` pinned to the previous tag.
- Liquibase runs on service startup, so schema changes deploy with the service. Rolling *back* a
  schema change is not automatic — additive migrations (new columns/tables, no destructive renames)
  keep rollback safe.
- If manual deployment is preferred initially, build and push from the development machine and keep
  only the `pull && up -d` step on the server. That still avoids compiling in production.

---

## 15. Health Checks & Startup Ordering

Infrastructure containers already define health checks. Services must wait for them, and declare their
own so the proxy and orchestration know when they are ready:
```yaml
ms_dictionary:
  depends_on:
    db_dictionary: { condition: service_healthy }
    rabbitmq: { condition: service_healthy }
  healthcheck:
    test: ["CMD", "wget", "-qO-", "http://localhost:8085/actuator/health"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 45s   # Spring Boot + Liquibase need warm-up time
```

`start_period` matters: without it, slow startup gets misread as failure and the container restarts in
a loop.

---

## 16. Client-Side Environment Configuration

Server environments are only half the picture — each client must be able to target them.

**Android.** `core/build.gradle.kts` currently hardcodes `http://192.168.0.241:8085/` in *both* debug
and release. Target state: build types (or flavors) supplying `ROOT_URL_VERBORUM_API` — debug →
`http://10.0.2.2:8085/` (emulator) or the host LAN IP (physical device); staging → the staging HTTPS
origin; release → the production origin. The cleartext exception in `network_security_config.xml`
should then be narrowed to debug builds only.

**KMP web & iOS.** Same three origins supplied at build time per target. The web client additionally
depends on gateway CORS configuration for its environment's domain, and its token storage approach
(BFF vs. pure SPA — Integration §6.3) may add a small server component of its own to the compose
stack.

---

## 17. Sequencing — What to Do When

Nothing about running services on the host today constrains the server later. Recommended order,
mapped to the backend roadmap:

| When | Action | Why then |
|---|---|---|
| Now (~20 min) | Externalize config to `${VAR:default}`; add `.env.example`; move Adminer to 8090 | Free, no behavior change, removes the only lock-in (config externalization already done) |
| When ms_user has real endpoints | Add Dockerfiles + `docker-compose.services.yml`; run the full stack locally once | Catch container bugs on a laptop, not over SSH |
| BE Phase 3 (Keycloak) | Keycloak into compose with a persistent volume; restrict actuator (P3-06); rotate dev credentials | Keycloak is infrastructure, and realm config must survive restarts |
| BE Phase 5 (gateway) | Reverse proxy, TLS, CORS, registry + CI, first server deploy | The gateway is the single ingress the proxy fronts; Android needs the HTTPS origin |
| Before the Android public release | Staging stack; backup + restore rehearsal | A shipped mobile build cannot be hotfixed |

---

## 18. Operational Basics

- **Logs:** `docker compose logs -f ms_dictionary`. Set log rotation
  (`logging: driver: json-file, options: {max-size: 10m, max-file: 3}`) or disks fill quietly.
- **Health:** `/actuator/health` per service, through the gateway where exposed.
- **Queues:** RabbitMQ management over an SSH tunnel; watch the dead-letter queue — a growing DLQ
  means consumers are failing.
- **Updates:** rebuild images for base-image CVE patches; `docker system prune` reclaims space from
  old layers.
- **Timezone:** run containers in UTC; the API already emits ISO-8601 timestamps.

---

## 19. Open Decisions

1. **Server size and provider** — drives §11 and §12; 4 GB minimum, 8 GB comfortable.
2. **One Postgres or three** (§12) — reversible; measure rather than assume.
3. **CI-built images or manual push** (§14) — either works; avoid building on the server.
4. **Staging from the start, or dev + prod initially** (§8.3) — staging matters most once mobile
   clients ship.
5. **Web token storage: BFF vs. pure SPA** (Integration §6.3) — a BFF adds a container to this stack,
   so it affects the compose layout.
