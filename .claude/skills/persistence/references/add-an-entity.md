# Adding an Entity

## 1. Entity class

`{domain}/entity/{Entity}.java`, following the template in the skill's Quick start and the id and
timestamp rules in `entity-patterns.md`.

Decide two things explicitly, and write the reasoning as a comment:

- **Who mints the id** — the client (primary domain object) or the server (system-owned satellite).
- **Whether it gets a real foreign key** — only if it is a same-service satellite with no
  independent life. `DictionaryTag` carries exactly this comment, and it is the model to copy.

## 2. Liquibase migration

New changeset file, registered in the master changelog, with a `comment` and a `rollback`. See
`liquibase-migrations.md`. Include the UNIQUE constraint if the write path should be idempotent,
and an index on any column that will be filtered on.

## 3. Repository

```java
public interface DictionaryTagRepository extends JpaRepository<DictionaryTag, String> {
    List<DictionaryTag> findByDictionaryId(String dictionaryId);
}
```

## 4. DTOs

`{Entity}RequestDTO` and `{Entity}ResponseDTO` in `{domain}/dto/`, with validation annotations and
constant messages including `@Size` limits. See `web-api`.

A server-generated id does not appear on the request DTO — that is the whole point of it being
server-generated.

## 5. Mapper

MapStruct interface in `common/mapper/`, `componentModel = "spring"`:

```java
@Mapper(componentModel = "spring")
public interface DictionaryTagMapper {
    DictionaryTagResponseDTO toDictionaryTagResponseDTO(DictionaryTag dictionaryTag);
}
```

Multi-source mapping when a field comes from outside the DTO:

```java
@Mapping(source = "dictionaryId", target = "dictionaryId")
Word toWord(String dictionaryId, WordRequestDTO wordRequestDTO);
```

## 6. Service

Interface plus `impl/`, taking `ownerId` explicitly where ownership applies. Write methods are
`@Transactional`. Ownership check first: 403 on a write, 404 on a read by id — the two helpers in
`DictionaryTagServiceImpl` (`requireOwnedDictionary` and `requireReadableDictionary`) are the
reference implementation of that split.

## 7. Verify

Start the service — Liquibase applies the changeset at boot. Confirm the table and the column types
in Adminer:

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -c "\d dictionary_tags"
```

## 8. Document

- The table and its columns in the domain model section of `docs/agent/verborum.md`
- The entity, and any quirk worth warning about, in the service's `CLAUDE.md`

## 9. Review

Run the `code-reviewer` agent before committing.
