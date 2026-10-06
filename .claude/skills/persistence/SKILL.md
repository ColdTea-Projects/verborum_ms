---
name: persistence
description: The data layer of Verborum — PostgreSQL 14, JPA entities, UUID string ids, zone-aware timestamps, JSON columns, Spring Data repositories, and Liquibase JSON migrations. Use when adding or altering an entity, table, column, or query.
---

# Persistence

PostgreSQL 14 + Spring Data JPA (Hibernate 6.6) + Liquibase (Boot-managed, 4.31), migrations in **JSON**.
Layering: `spring-boot-app-architecture`. Hibernate `ddl-auto` is not used — Liquibase owns the
schema.

## Quick start

```java
@Getter @Setter @ToString
@Entity @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "dictionaries")
public class Dictionary {

    @Id
    @Column(name = "dictionary_id", updatable = false, nullable = false)
    private String dictionaryId;            // UUID String, client-provided

    @Column(name = "fk_user_id")            // cross-service ref: plain String, NO database FK
    private String userId;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
```

## Core rules

- **Ids are UUID `String`.** Never `Long`, never auto-increment, never a sequence. Client-provided
  for the primary domain objects (validated with `@ValidUUID`); server-generated with
  `UUID.randomUUID().toString()` only for system-owned satellites like `DictionaryTag`.
- **Timestamps are `OffsetDateTime` over `timestamptz`**, set by Hibernate, server-authoritative —
  ignored on write, present only on reads. Never `LocalDateTime`: it is ambiguous the moment two
  containers run with different default zones.
- **JSON columns need both annotations** — `@JdbcTypeCode(SqlTypes.JSON)` *and*
  `columnDefinition = "json"`. With only the second, Hibernate binds a `varchar` and Postgres
  rejects it.
- **No JPA association across aggregates**, and **no database foreign key across services** — see
  `spring-boot-app-architecture`.
- **`saveAndFlush()` / `saveAllAndFlush()`**, not `save()`.
- **`@Transactional` on every write.** Events are raised inside the transaction and sent after
  commit (`messaging`).

Entity details — id strategies, the JSON-vs-TEXT choice, repository idioms, atomic aggregate updates and
upsert ownership — are in
[references/entity-patterns.md](references/entity-patterns.md).

## Liquibase — the one rule that cannot be broken

**Never modify an existing changeset.** Liquibase checksums them; editing one that has already run
breaks the migration history everywhere it has been applied. Fix forward with a new file.

```
src/main/resources/db/changelog/{YEAR}/{MONTH}/{DD}-{nn}-changelog.json
```

Register it in `db.changelog-master.json`, appended at the end:

```json
{ "include": { "file": "db/changelog/2026/07/23-01-changelog.json" } }
```

Changeset templates, the column type table, and the raw-SQL `ALTER` pattern with `USING` casts are
in [references/liquibase-migrations.md](references/liquibase-migrations.md).

## Workflow: adding an entity

Nine steps in [references/add-an-entity.md](references/add-an-entity.md) — entity, migration,
repository, DTOs, mapper, service, verification in Adminer, documentation, review.

## Pitfalls

- Editing a changeset that has already run
- Forgetting the `include` in the master changelog
- `DATETIME` or `LocalDateTime` for a new timestamp
- `columnDefinition = "json"` without `@JdbcTypeCode(SqlTypes.JSON)`
- `Long` or generated ids for a client-owned object
- A database foreign key crossing a service boundary
- Activating the commented-out Dictionary-to-Word association
- A migration with no `rollback` and no `comment`
- A shared counter or aggregate changed by read-modify-write in Java, or a `@Modifying` UPDATE with `clearAutomatically` but no `flushAutomatically`
- An upsert by client-supplied id that checks the target but not who owns the existing row
