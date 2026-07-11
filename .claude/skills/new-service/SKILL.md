---
name: new-service
description: Full checklist for scaffolding a new Verborum microservice from scratch. Use when creating ms_user, ms_marketplace, ms_gateway, or any new service module. Produces a running, secured, empty shell that mirrors ms_dictionary.
---

# Scaffolding a New Microservice

Mirror `ms_dictionary` exactly. Security is included from the start — not a later step.
For a fresh copy of the concrete files, read the actual `ms_dictionary/` module as you go.

## 0. Confirm the specs
From `docs/agent/verborum.md`, get: service name, port, DB name, base package, Adminer port.
If any are marked TBD, confirm with the developer before scaffolding.

Suggested assignments (confirm against verborum.md):
- ms_user: port 8086, DB `vdbprofile`, DB port 5433, Adminer 8081
- ms_marketplace: port 8087, DB `vdbmarket`, DB port 5434, Adminer 8082
- ms_gateway: port 8080 (Spring Cloud Gateway, no DB)

## 1. Module + pom.xml
Create `ms_{name}/pom.xml` mirroring ms_dictionary's dependencies. Include:
- web, actuator, devtools, data-jpa, postgresql, liquibase-core
- validation, lombok, mapstruct + mapstruct-processor
- springdoc-openapi
- **spring-boot-starter-security + spring-boot-starter-oauth2-resource-server** (from the start)
- test: spring-boot-starter-test, junit-jupiter-engine, mockito-junit-jupiter
- jacoco plugin
(ms_gateway instead uses spring-cloud-starter-gateway and skips JPA/Liquibase/Postgres.)

## 2. Application class
`src/main/java/de/coldtea/verborum/ms{name}/Ms{Name}Application.java` with `@SpringBootApplication`.

## 3. Package skeleton
```
de.coldtea.verborum.ms{name}
├── common/
│   ├── constants/   (DTOMessageConstants, ErrorMessageConstants, ResponseMessageConstants)
│   ├── config/      (SecurityConfig, and RabbitMQConfig if it uses events)
│   ├── exception/   (GlobalExceptionHandler + exception types)
│   ├── mapper/
│   ├── response/    (Response, ErrorResponse — WITH @Getter)
│   └── utils/       (ResponseUtils, ListUtils, SecurityUtils, validators)
```

## 4. Response classes — with @Getter
Copy `Response.java` and `ErrorResponse.java` from ms_dictionary but ADD `@Getter`
(ms_dictionary's originals are missing it — do not replicate that bug).

## 5. SecurityConfig
`common/config/SecurityConfig.java` — stateless JWT resource server, permit `/actuator/**`,
`/v3/api-docs/**`, `/swagger-ui/**`, everything else `authenticated()`. See verborum-security.

## 6. application.properties
```properties
server.port={port}
spring.datasource.url=jdbc:postgresql://localhost:5432/{dbname}
spring.datasource.username=coldtea
spring.datasource.password=qwerty
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.json
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/verborum
supported.languages=EN,DE,FR,ES,IT,TR,AZ,LT
management.endpoints.web.exposure.include=*
```

## 7. Liquibase master
`src/main/resources/db/changelog/db.changelog-master.json` with an empty
`databaseChangeLog: []` to start. Entities get added via the `new-entity` skill.

## 8. docker-compose.yml
Postgres 14-alpine + Adminer on the assigned ports, DB name as specified.

## 9. Per-service CLAUDE.md
Create `ms_{name}/CLAUDE.md` — thin (30–50 lines): service purpose, port, DB, base package,
its entities, published/consumed events, current status, and any service-specific quirks.
It inherits all conventions from the root; do not duplicate them here.

## 10. Verify
`./mvnw -pl ms_{name} compile` (or from within the module) — confirm it builds. Start it and
confirm unauthenticated requests return 401.

## 11. Hand off
The empty secured shell is done. Domain entities and endpoints come next via the
`new-entity` and `new-endpoint` skills. Tell the developer the next roadmap task.

## Tip
Delegate this whole checklist to the `service-scaffolder` subagent.
