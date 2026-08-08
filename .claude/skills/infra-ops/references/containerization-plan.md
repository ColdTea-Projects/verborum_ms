# Containerization Plan (target state, not yet built)

Recorded so new work does not contradict it. Full version:
`docs/ops/dockerization-and-environments.md`.

Guiding principle: **local development and production are different problems.** The topology that
makes development fast is not the one that makes production safe. They share Dockerfiles and
configuration mechanisms, not layout.

## Three topologies

| | A — Local Dev | B — Local Full-Stack | C — Server |
|---|---|---|---|
| Purpose | Daily coding | Verify images before deploying | Real deployment |
| Services | Host (`mvnw`) | Containers | Containers |
| Compose | `docker-compose.yml` | `+ docker-compose.services.yml` | `+ docker-compose.prod.yml` |
| Exposed | All ports on localhost | All ports on localhost | 80/443 only |

Topology A is current and correct for daily work; it should not change.

## Dockerfile shape

Multi-stage, so the runtime image ships a JRE rather than a full JDK plus Maven:

```dockerfile
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

- `-XX:MaxRAMPercentage=75` makes the JVM respect the container memory limit rather than the host's
  total RAM. Without it, containers get OOM-killed under limits.
- Non-root user.
- Tests skipped in the image build — they belong in CI, not the deploy path.
- Add a `.dockerignore` (`target/`, `.git`, `.idea`, `*.md`) to keep the build context small.

## Networking and exposure

Inside Docker, services address each other by compose service name:

```
jdbc:postgresql://db_dictionary:5432/vdbdictionary
spring.rabbitmq.host=rabbitmq
http://keycloak:8080/realms/verborum
```

**Only the reverse proxy publishes ports in production — 80 and 443.** Services, databases, the
RabbitMQ management UI and the Keycloak admin console stay on the internal network; administrative
access goes over an SSH tunnel. Caddy is the recommended proxy for automatic TLS, with two DNS
names (API gateway and Keycloak), because clients perform OAuth redirects against Keycloak
directly. Adminer never deploys beyond development.

## Health checks and startup ordering

```yaml
ms_dictionary:
  depends_on:
    db_dictionary: { condition: service_healthy }
    rabbitmq: { condition: service_healthy }
  healthcheck:
    test: ["CMD", "wget", "-qO-", "http://localhost:8085/actuator/health"]
    interval: 15s
    start_period: 45s   # Spring Boot + Liquibase need warm-up time
```

`start_period` matters: without it, slow startup is misread as failure and the container
restart-loops.

## Environment differences

| | dev | staging | prod |
|---|---|---|---|
| TLS | none | Let's Encrypt | Let's Encrypt |
| Adminer | yes | no | no |
| Published DB / broker ports | yes | no | no |
| Actuator | `health,info` | `health,info` | `health,info` |
| Restart policy | none | unless-stopped | unless-stopped |
| Image source | built locally | registry tag / commit SHA | registry tag |

Keep one `application.properties` with safe local defaults, adding profile files only if overrides
outgrow environment variables. Environment variables still win over profile files, so secrets stay
outside the repository.

## Registry and deployment

**Build images in CI, not on the server** — building there needs a JDK, Maven, source and enough
RAM to run a build alongside the running stack.

```
git push → CI: mvn verify → docker build → push ghcr.io/…/ms_dictionary:<sha>
         → server: docker compose pull && docker compose up -d
```

Tag with the commit SHA or a semver tag, **never only `latest`** — you cannot roll back to
`latest`. Liquibase runs at service startup, so schema changes deploy with the service; rolling
*back* a schema change is not automatic, which is why migrations should stay additive.

## Data and backups

Named volumes for every database, RabbitMQ, Keycloak and the proxy's certificates. Nightly
`pg_dump` per database in production, retained off-server:

```bash
docker exec verborum-db-dictionary pg_dump -U "$DB_USER" vdbdictionary \
  | gzip > "backup-dictionary-$(date +%F).sql.gz"
```

User dictionaries are the entire product, so the restore should be rehearsed at least once — an
untested backup is a hypothesis.

## Resource planning

Count JVMs: each Spring service ~400–640 MB, Keycloak ~512 MB–1 GB (the heaviest single consumer),
Postgres ~100–200 MB each, RabbitMQ ~150–250 MB, proxy ~30 MB. **2 GB will not hold this; 4 GB is a
workable minimum, 8 GB comfortable.** Always set explicit memory limits together with
`-XX:MaxRAMPercentage`, so one runaway service cannot starve the rest.

## Open decisions

Server size and provider; one Postgres instance with three databases versus three instances
(reversible — a connection-string change either way); CI-built images versus manual push; whether
staging is worth it before mobile clients ship; web token storage.
