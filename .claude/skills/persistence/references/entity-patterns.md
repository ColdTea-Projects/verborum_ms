# Entity and Repository Patterns

## Ids

| Kind | Strategy | Examples |
|---|---|---|
| Primary domain object | UUID `String`, **client-provided**, validated with `@ValidUUID` | `dictionaryId`, `wordId`, `userId` |
| System-owned satellite | UUID `String`, **server-generated** in the service | `DictionaryTag.tagId`, `VaultEntry` |

Offline-first clients mint ids locally and sync later, which is why the primary objects take a
client id. A satellite is different: clients send a string and never track that row's identity.

```java
DictionaryTag.builder()
        .tagId(UUID.randomUUID().toString())
        .dictionaryId(dictionaryId)
        .tag(tag)
        .build();
```

Stored as `VARCHAR(255)`.

## Timestamps

`OffsetDateTime` over `timestamptz`, set by Hibernate. The Java fields are named `createdAt` /
`updatedAt` — which is what the JSON keys are — while the columns keep the older `creation_dt` /
`update_dt` names. Serialized as ISO-8601 normalized to UTC.

```java
@CreationTimestamp
@Column(name = "creation_dt", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@UpdateTimestamp
@Column(name = "update_dt")
private OffsetDateTime updatedAt;
```

Server-authoritative: ignored on write, present only on reads.

## JSON columns versus TEXT

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "word_meta", columnDefinition = "json")
private String wordMeta;
```

`json` in Postgres, `String` in Java, **both annotations required**. The value must be valid JSON
or the insert fails at the database.

Text that merely *contains* JSON but needs no database-level validation is `TEXT` instead — that is
what `word` and `translation` (JSON arrays of per-meaning surface forms) and `tag` use. They were
widened from `VARCHAR(255)` so multi-meaning entries are not truncated.

The backend stores all of these **opaquely**; the shape is the clients' contract, documented in
`Word.java` and `docs/integration/frontend-backend-integration.md` §4.2.

## Relationships

- No `@OneToMany` / `@ManyToOne` across aggregates. The Dictionary-to-Word association is commented
  out deliberately; joins are explicit repository calls.
- No database foreign key to another service's table.
- A same-service satellite may have a real FK with `ON DELETE CASCADE` — `dictionary_tags`,
  `UserStats`, `VaultEntry`. See `spring-boot-app-architecture`.

## Repositories

```java
public interface DictionaryRepository extends JpaRepository<Dictionary, String> {
    List<Dictionary> findByUserId(String userId);
    List<Dictionary> findByIsPublicTrue();
}

public interface DictionaryTagRepository extends JpaRepository<DictionaryTag, String> {
    List<DictionaryTag> findByDictionaryId(String dictionaryId);
    Optional<DictionaryTag> findByDictionaryIdAndTag(String dictionaryId, String tag);
    void deleteByDictionaryIdAndTag(String dictionaryId, String tag);
}
```

- `JpaRepository<Entity, String>` — the id type is always `String`.
- **Derived query methods.** Write JPQL only when a derived name genuinely cannot express it.
- **`saveAndFlush` / `saveAllAndFlush`**, so the write is visible before the method returns.
- Bulk deletes use `deleteByXIn(Collection<String>)`.
- A repository returning nothing returns an empty list, and callers short-circuit on it — that is
  what makes the `user.deleted` cascade a harmless no-op on redelivery.
- Injected into services only, never controllers.

## Idempotent writes

A UNIQUE constraint plus find-or-create, so one code path serves both the HTTP caller and an event
listener:

```java
return dictionaryTagRepository.findByDictionaryIdAndTag(dictionaryId, tag)
        .map(dictionaryTagMapper::toDictionaryTagResponseDTO)
        .orElseGet(() -> createTag(dictionaryId, tag));
```
