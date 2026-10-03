---
name: spring-boot-app-architecture
description: How a Verborum microservice is structured — service topology, module and package layout, layering rules, and service boundaries. Use when creating a service, adding a domain package, or deciding where a class or a responsibility belongs.
---

# Application Architecture

The shape of a service. Language style: `java`. Framework wiring: `spring-boot`. Build and module
registration: `maven`. Project state and domain model: `docs/agent/verborum.md`.

**`ms_dictionary` is the reference implementation.** When a convention is ambiguous, read it there
and copy. New services must be structurally identical to it.

## Topology

```
Client (Android / iOS / web)  ──Bearer JWT──►  API Gateway (planned)
        ├──► ms_user         8086  vdbprofile     profile, vault, stats, Keycloak admin
        ├──► ms_dictionary   8085  vdbdictionary  dictionaries, words, tags
        └──► ms_marketplace  8087  vdbmarket      public listings, ratings, imports

RabbitMQ  — async inter-service events (verborum.events)
Keycloak  — identity; Google and Facebook federate behind it
V2: ms_autofil — community word suggestions, NoSQL
```

State: ms_dictionary complete and secured; ms_user Phase 2 complete and verified over HTTP;
ms_marketplace scaffolded and secured (P4-01), no domain yet; ms_gateway and ms_autofil not built.

## Package layout

Base package `de.coldtea.verborum.ms{name}`. Two halves: `common/` for cross-cutting concerns, and
one package per **domain aggregate**.

```
de.coldtea.verborum.ms{name}
├── Ms{Name}Application.java
├── common/
│   ├── config/       SecurityConfig, RabbitMQConfig
│   ├── constants/    DTOMessageConstants, ErrorMessageConstants, ResponseMessageConstants
│   ├── event/        event DTOs + OutboundEvent
│   ├── exception/    GlobalExceptionHandler + one file per exception type
│   ├── listener/     OutboundEventPublisher + @RabbitListener classes
│   ├── mapper/       MapStruct interfaces only
│   ├── response/     Response, ErrorResponse
│   └── utils/        ResponseUtils, ListUtils, SecurityUtils, validators + annotations
└── {domain}/
    ├── controller/  dto/  entity/  repository/
    └── service/{Domain}Service.java + impl/{Domain}ServiceImpl.java
```

Live examples: ms_dictionary has `dictionary/`, `word/`, `tag/`; ms_user has `user/`, `vault/`,
`userstats/`. Mappers, events and listeners live in `common/`, not in the domain package — that is
the existing split, and new code follows it even though a mapper is domain-specific.

## Layering rules

```
Controller  → auth boundary: caller from the JWT, body validated, envelope built
   ↓
Service     → interface + impl; business logic, transactions, ownership checks, event raising
   ↓
Repository  → Spring Data; derived queries
```

- **Every service is an interface plus an `impl/` class** — no exceptions, even for one method.
- **Controllers hold no business logic** and never see a repository. The only logic permitted
  there is `requireSelf(...)` on a path variable that names a user.
- **A service never touches `RabbitTemplate`** — it raises an `OutboundEvent` (`messaging`).
- **Ownership is an explicit `ownerId` argument, never ambient state** (`security`).

## Service boundaries

See [references/service-boundaries.md](references/service-boundaries.md) for the full rules and the
reasoning behind each. The short version: no database foreign key across services, no JPA
association across aggregates, no synchronous service-to-service call, and the cross-service
identity is the JWT subject — which in ms_user is the `keycloak_id` column, not its `user_id`.

## Workflow: scaffold a new service

Full checklist in [references/scaffold-a-service.md](references/scaffold-a-service.md). It produces
an **empty, running, secured shell** — module and registered pom, application class, package
skeleton, `SecurityConfig`, `SecurityUtils`, `GlobalExceptionHandler`, response envelope,
properties, empty Liquibase master changelog, compose file, and a thin per-service `CLAUDE.md`.
Domain entities and endpoints come afterwards via `persistence` and `web-api`.

## Per-service CLAUDE.md

Facts only, never conventions: purpose, port, database, base package, entities with their columns,
events published and consumed, current status, and the service-specific quirks a reader would
otherwise get wrong. `ms_dictionary/CLAUDE.md` is the template.

## Pitfalls

- A new service without `SecurityConfig` from the first commit
- Business logic in a controller, or a repository injected into one
- A service class without its interface
- A database foreign key to another service's table
- Inventing a port or database name instead of reading `docs/agent/verborum.md`
- Duplicating shared conventions into a per-service `CLAUDE.md`
