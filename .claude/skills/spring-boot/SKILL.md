---
name: spring-boot
description: Spring Boot 3.5 framework mechanics for Verborum — bean stereotypes, configuration classes, application.properties conventions with env-var indirection, and actuator exposure. Use when wiring beans, adding configuration, or editing application.properties.
---

# Spring Boot (3.5)

Framework mechanics. Language style: `java`. Layout: `spring-boot-app-architecture`.
HTTP layer: `web-api`. Boot 3.5.16 on Java 17, `jakarta.*` namespace.

## Quick start

```properties
# every deployable value is an env var with a local-dev default
server.port=${SERVER_PORT:8085}
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/vdbdictionary}
management.endpoints.web.exposure.include=health,info
```

That `${VAR:default}` form is what lets the same jar run on a laptop and in a container without a
second properties file. See
[references/application-properties.md](references/application-properties.md) for the full
reference file.

## Beans and injection

- One stereotype per class: `@RestController`, `@Service` (on the `Impl`, never the interface),
  `@Component` for listeners and publishers, `@Configuration` for configuration. Spring Data
  repositories need no annotation.
- **Constructor injection only**, via `@RequiredArgsConstructor` + `private final`. `@Autowired`
  is banned — see `java`.
- MapStruct mappers become beans through `@Mapper(componentModel = "spring")` and are injected
  like anything else, never instantiated.
- `@Value` is fine for a single scalar from properties (`supported.languages`, the Keycloak realm).
  Anything larger deserves a `@ConfigurationProperties` class.

## Configuration classes

Live in `common/config/`, declare `@Bean` methods and nothing else — no business logic, no state.

| Class | Purpose | Skill |
|---|---|---|
| `SecurityConfig` | stateless JWT resource server + realm-role mapping | `security` |
| `RabbitMQConfig` | exchange, queues, bindings, dead-letter setup, JSON converter | `messaging` |

## Two settings that look harmless and are not

**`management.endpoints.web.exposure.include` must stay `health,info`.** It was `*` while
`/actuator/**` was `permitAll`, which served `/actuator/env`, `/actuator/configprops` and a heap
dump to any anonymous caller — including the datasource password. If an endpoint is genuinely
needed, add that one and secure it; never restore `*`.

**`jwk-set-uri` is set alongside `issuer-uri` on purpose.** With only `issuer-uri`, Boot fetches
the OIDC discovery document at **startup**, so the service refuses to boot whenever Keycloak is
down. Setting both makes key fetching lazy. Do not tidy the apparent duplicate away.

## Actuator

`spring-boot-starter-actuator` is in every service, and `/actuator/**` is `permitAll` in
`SecurityConfig`. That is only safe because exposure is limited to `health,info` — the two settings
are a pair, and changing one without the other is the bug above.

## Profiles and devtools

The project does not use Spring profiles for environment differences; env-var indirection covers
it. `spring-boot-devtools` is a normal dependency in both modules for restart-on-recompile locally,
and is inert in a packaged run. Tests rely on slices and full-context tests — see
`integration-testing`.

## Supported languages

`supported.languages` (19 codes) is the single source of truth for language validation, read by
`@SupportedLanguage` / `SupportedLanguageValidator`. It must be identical in every service that
validates languages, and every client's language enum must be a subset of it. The validator
uppercases before matching, so clients may send `de`.

## Pitfalls

- Hardcoding a URL, port, or credential in Java
- A property that differs per environment without a `${VAR:default}`
- Restoring `management.endpoints.web.exposure.include=*`
- Removing `jwk-set-uri`
- Business logic inside a `@Configuration` class
