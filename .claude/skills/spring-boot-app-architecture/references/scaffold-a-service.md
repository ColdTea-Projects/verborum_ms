# Scaffolding a New Service

Mirror `ms_dictionary`, with security from the first commit. Read the real module as you go — this
checklist names the steps, the module is the template.

## 0. Confirm the specs

From `docs/agent/verborum.md`: service name, port, database name, database host port, Adminer port,
base package. If any value is marked TBD, **ask** rather than invent, and cross-check the port map
in `infra-ops` for a clash.

| Service | Port | Database | DB host port |
|---|---|---|---|
| ms_dictionary | 8085 | `vdbdictionary` | 5432 |
| ms_user | 8086 | `vdbprofile` | 5433 |
| ms_marketplace | 8087 | `vdbmarket` | 5434 |
| ms_gateway | 8080 | none (Spring Cloud Gateway) | — |

## 1. Module and pom

`ms_{name}/pom.xml` mirroring ms_dictionary's dependency set, **and register the module in the
aggregator `pom.xml`** — an unregistered module is invisible to a root build. See `maven`.

A gateway module swaps JPA, Liquibase and Postgres for `spring-cloud-starter-gateway`.

## 2. Application class

`src/main/java/de/coldtea/verborum/ms{name}/Ms{Name}Application.java` with
`@SpringBootApplication`.

## 3. Package skeleton

`common/{config,constants,exception,mapper,response,utils}`, plus `common/{event,listener}` if the
service uses RabbitMQ. Domain packages come later.

## 4. Response envelope

`common/response/Response.java` and `ErrorResponse.java` — **with `@Getter`**. Without it Jackson
emits an empty `{}`.

```
Response      { status, message, path, timestamp }
ErrorResponse { status, error, errorDetail, path, timestamp }
```

## 5. SecurityConfig

`common/config/SecurityConfig.java` — CSRF disabled, stateless sessions, `permitAll` on
`/actuator/**` and Swagger, `anyRequest().authenticated()`, and the hand-written realm-role
extraction. Copy it from an existing service; do not re-derive it. See `security`.

## 6. SecurityUtils

`common/utils/SecurityUtils.java` with `getCurrentUserId()` and `requireSelf(...)`.

## 7. GlobalExceptionHandler

Plus the exception types the service needs — at minimum `RecordNotFoundException`,
`ForbiddenOperationException`, `InvalidUUIDException`. See `web-api`.

## 8. application.properties

Port, datasource, Liquibase changelog, Keycloak `issuer-uri` **and** `jwk-set-uri`, actuator
`health,info`, RabbitMQ if used — every value as `${VAR:local-default}`. See `spring-boot`.

## 9. Liquibase master changelog

`src/main/resources/db/changelog/db.changelog-master.json` with an empty `databaseChangeLog: []`.

## 10. Infrastructure

- `ms_{name}/docker-compose.yml` — Postgres 14-alpine + Adminer on the assigned host ports
- Add the database to the **root** compose too, with a named volume and a health check
- Add any new environment variable to `.env.example` with a commented placeholder

See `infra-ops`.

## 11. Per-service CLAUDE.md

Thin, 30–50 lines: purpose, port, database, base package, entities, published and consumed events,
current status, quirks. It inherits every convention from the root file — do not duplicate
conventions there.

## 12. Verify

```bash
./mvnw -pl ms_{name} compile
./mvnw -pl ms_{name} spring-boot:run
curl -i http://localhost:{port}/actuator/health     # 200
curl -i http://localhost:{port}/{anything-else}     # 401
```

## What the scaffold is not

Domain entities and endpoints are **not** part of it. The output is an empty, running, secured
shell; the first entity follows via `persistence`, the first endpoint via `web-api`.
