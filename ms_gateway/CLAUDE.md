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
- **Status:** routes done (P5-02); JWT rules and identity-header stripping done (P5-03), CORS `P5-06`. Starts with no
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

## Routes (`application.properties`, prefix `spring.cloud.gateway.server.webmvc.routes`)
| Path prefix | Upstream variable | Default |
|---|---|---|
| `/dictionaries/**`, `/words/**` | `DICTIONARY_URL` | `http://localhost:8085` |
| `/users/**` | `USER_URL` | `http://localhost:8086` |
| `/marketplace/**` | `MARKETPLACE_URL` | `http://localhost:8087` |
| `/notifications/**` | `NOTIFICATION_URL` | `http://localhost:8088` |

Nothing is rewritten. `Authorization` and `X-Device-Id` pass through. A service's own error (401, 403, 404,
400 …) passes through unchanged; an unknown path is a 404 in the gateway's envelope; an unreachable
service is **502** and one that does not answer within `GATEWAY_READ_TIMEOUT` is **504**, both with a fixed
message (never the host or port).

## Who is the caller (P5-03)
- The gateway validates the JWT with the same properties as the services (issuer, JWK set, audience
  `verborum-api`, expiry) and refuses a bad one with 401 **before any upstream call**.
- `IdentityHeaderFilter` removes every identity-shaped request header (`X-User-*`, `X-Auth-*`,
  `X-Forwarded-User/-Email/-Preferred-Username`, `X-Remote-User`, `X-JWT-*`, `X-Keycloak-*`, …) in any case.
  The gateway adds none. Only `Authorization` (re-validated by each service) and `X-Device-Id` (push
  targeting) cross. If a header ever has to carry identity, that is a decision, not a tweak to this list.
- `JwtValidationTest` runs the **real** decoder against a JWKS the test serves: valid, expired, wrong audience,
  no audience, wrong issuer, foreign key, unsigned, garbage, no token, token in the query string.

## Two things that break writes if "tidied" (found on the first real run, P5-02)
- **`remove-content-length-request-headers-filter.enabled=false`** and **`httpclient5` in the pom.** By
  default the gateway drops `Content-Length` and sends the body chunked; every service's
  `RequestBodyLimitFilter` then answers 411. Reads work, every `POST`/`PUT` fails — and a stub-upstream test
  does not show it. `RoutingTest.requestBody_KeepsItsContentLength_AndIsNotChunked` guards it.
- Query parameters may come out **re-ordered**, and a form-style `+` becomes `%20`; percent-encoded values
  (`%C3%A9`, `%2B`) are kept. MockMvc reads a literal `+` differently from Tomcat, so tests avoid it.

## Tests
`MsGatewayApplicationTests` (full context, no network, `@MockBean JwtDecoder`): health 200, anonymous
401, unknown path 404 in the envelope, 413 over the body cap, `/actuator/env` not exposed.
`RoutingTest`: a JDK `HttpServer` stands in for the services — every prefix routed, query string, headers,
Content-Length, upstream errors pass through, unknown path and anonymous never reach an upstream.
`UpstreamDownTest`: 502 in the envelope.
`SecurityConfigTest`: the realm-role extraction.
