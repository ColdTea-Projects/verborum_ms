# Service Boundaries

Each service owns its own database and its own deployable. These rules keep the boundary real.

## No database foreign key across services

A cross-service reference is a plain `String` column holding the other service's id:

```java
@Column(name = "fk_user_id")   // references ms_user, but no DB-level FK
private String userId;
```

The column keeps the `fk_` prefix for readability, but there is no constraint. Referential
integrity across services is the application's job, handled by events.

## A same-service satellite may have a real foreign key

`dictionary_tags → dictionaries` has a real FK with `ON DELETE CASCADE`, and so do `UserStats` and
`VaultEntry` in ms_user. The test is **independent life**:

| Relationship | FK? | Why |
|---|---|---|
| `word → dictionary` | No | words are split-ready — they could become their own service |
| `dictionary_tag → dictionary` | **Yes, cascade** | a tag is a same-service satellite with no independent life |
| `user_stats → user`, `vault_entry → user` | **Yes, cascade** | same reasoning |

Where there is no FK, deletion is handled in the service layer instead: deleting a dictionary
deletes its words explicitly, and its tags fall away through the cascade.

## No JPA association across aggregates

The `@OneToMany` / `@ManyToOne` between `Dictionary` and `Word` is commented out on purpose. Joins
are explicit repository calls. This avoids N+1 loading and keeps the boundary splittable. Do not
activate it without discussion.

## No synchronous service-to-service calls

Cross-service reactions go through RabbitMQ. Two consequences worth stating:

- A consumer must never call back into the publisher to act on an event — the event carries what
  the consumer needs (`messaging`, rule 2).
- A read model stores the fields it filters, sorts or pages on, rather than fetching them per
  request (`messaging`, rule 5). Fetching per request also hits the ownership filter, which returns
  nothing to a service account.

## Identity across services — the rule that bites

`fk_user_id` in ms_dictionary and ms_marketplace holds the **JWT subject**. In ms_user that same
value is the `keycloak_id` column, **not** its `user_id`.

Ownership checks and event consumers must match on the Keycloak id. Cascading on ms_user's
`user_id` matches nothing and reports success — a silent no-op that looks like a clean run. This is
why `user.deleted` carries both ids, with `userId` present only for correlation.

## Where a class belongs

| Kind of class | Package |
|---|---|
| Entity, repository, DTO, controller, service | `{domain}/` |
| MapStruct mapper | `common/mapper/` |
| Event DTO, `OutboundEvent` | `common/event/` |
| `@RabbitListener`, `OutboundEventPublisher` | `common/listener/` |
| Exception type + `GlobalExceptionHandler` | `common/exception/` |
| Custom constraint annotation + its validator | `common/utils/` |
| `SecurityConfig`, `RabbitMQConfig` | `common/config/` |

A new domain aggregate gets its own top-level package with the full
`controller/dto/entity/repository/service` set — not a subpackage of an existing domain.
