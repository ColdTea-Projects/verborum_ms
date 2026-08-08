# application.properties Reference

One file per service, in `src/main/resources`. Every deployable value is `${VAR:local-default}`.
Local development needs no environment variables at all.

## ms_dictionary — the complete file, annotated

```properties
server.port=${SERVER_PORT:8085}
server.servlet.session.timeout=15m

# health + info ONLY. `*` combined with permitAll on /actuator/** served /actuator/env,
# /actuator/configprops and a heapdump to anyone who asked, listing config keys including
# spring.datasource.password. Add endpoints back one at a time, and secure them.
management.endpoints.web.exposure.include=health,info
management.info.env.enabled=true

info.app.name=Verborum Dictionary Micro Service
info.app.version=1.0.0

# PostgreSQL
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/vdbdictionary}
spring.datasource.username=${DB_USER:coldtea}
spring.datasource.password=${DB_PASSWORD:qwerty}
spring.datasource.driver-class-name=org.postgresql.Driver

# Liquibase owns the schema — Hibernate ddl-auto is not used
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.json

# RabbitMQ
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USER:verborum}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:verborum}

# Listener retry before a message is dead-lettered
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.initial-interval=1000
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.retry.multiplier=2.0

# Keycloak resource server. jwk-set-uri is set alongside issuer-uri on purpose: with only
# issuer-uri, Boot fetches the discovery document at STARTUP and the service refuses to
# start when Keycloak is down.
spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/verborum}
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${KEYCLOAK_JWK_SET_URI:http://localhost:8180/realms/verborum/protocol/openid-connect/certs}

supported.languages=EN,DE,FR,ES,IT,PT,NL,TR,AZ,LT,PL,UK,AR,FA,JA,ZH,KO,EL,RU
```

## ms_user adds

```properties
keycloak.realm=${KEYCLOAK_REALM:verborum}
keycloak.auth-server-url=${KEYCLOAK_AUTH_SERVER_URL:http://localhost:8180}
keycloak.admin.client-id=${KEYCLOAK_ADMIN_CLIENT_ID:verborum-backend}
# deliberately blank locally — the Keycloak deletion call is then skipped with a WARN
keycloak.admin.client-secret=${KEYCLOAK_ADMIN_CLIENT_SECRET:}
```

## Rules

- **No secrets in Java code.** Credentials, URLs and keys are properties or environment variables.
- The committed defaults are local-dev placeholders, not real credentials.
- Every new variable also gets a commented placeholder in the repo-root `.env.example`, so the
  deployable surface stays documented — see `infra-ops`.
- Values that differ between a laptop and a container (any host, port or credential) must be
  parameterised. Values that are the same everywhere (the changelog path, the supported-language
  list) stay literal.

## Naming convention for the variables

`SCREAMING_SNAKE_CASE`, grouped by subsystem, and identical across services so one `.env` drives
the whole stack: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_USER`,
`RABBITMQ_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `SERVER_PORT`.
