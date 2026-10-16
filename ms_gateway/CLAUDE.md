# ms_gateway — Service Guide

This file supplements the root `CLAUDE.md` and the `.claude/skills/`. It covers only what is
specific to this service. All conventions are inherited from the root — do not duplicate them.

## What this service does
The single origin for every client: Spring Cloud Gateway **Server MVC** (servlet stack), no database,
no RabbitMQ. It validates the JWT, then routes to ms_dictionary, ms_user, ms_marketplace and
ms_notification. Caddy sits in front of it for TLS and the auth-endpoint rate limit only.
Decisions and tasks: `docs/agent/roadmap.md`, Phase 5 (`P5-01`…`P5-13`).

## Facts
- **Port:** 8080 (Adminer moved to 8090 in `P5-05`)
- **Base package:** `de.coldtea.verborum.msgateway`
- **Status:** scaffolded (P5-01) — secured shell, **no routes yet** (`P5-02`). Starts with no
  infrastructure at all: Keycloak keys are fetched lazily.
- **Spring Cloud train:** `2025.0.3` ("Northfields", the line for Boot 3.5.x; `2025.1.x` is for Boot 4),
  pinned in this module's pom only. Starter: `spring-cloud-starter-gateway-server-webmvc`.

## Rules specific to a gateway
- **Both validate.** The gateway refuses a bad token early; every service still checks issuer and
  audience itself. Nothing trusts a forwarded identity header, and the gateway strips inbound
  `X-User-*` style headers (`P5-03`).
- **No Swagger here** — the gateway has no API of its own, so there is no springdoc and no
  `SWAGGER_ENABLED`.
- `RequestBodyLimitFilter` caps a body at 2 MB (`MAX_REQUEST_BODY_BYTES`), the largest cap any service
  accepts; the services keep their own, smaller caps.
- No domain code: no entities, services or mappers. `GlobalExceptionHandler` covers the gateway's own
  failures in the usual envelope (404 unknown path, 405, catch-all 500 with no internals).
- Upstream URLs will be `${DICTIONARY_URL:http://localhost:8085}`-style variables (`P5-02`), so
  containers use service names later with no code change.

## Tests
`MsGatewayApplicationTests` (full context, no network, `@MockBean JwtDecoder`): health 200, anonymous
401, unknown path 404 in the envelope, 413 over the body cap, `/actuator/env` not exposed.
`SecurityConfigTest`: the realm-role extraction.
