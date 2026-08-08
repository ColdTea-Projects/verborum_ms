# Adding an Endpoint

Eleven steps, in order. The last three are the ones most often skipped, and the usual reason a
change ships broken.

## 1. DTO

New request or response shape in `{domain}/dto/`, with validation annotations and constant
messages, including `@Size` and range limits. See `validation-and-errors.md`.

## 2. Service interface

Add the method to `{Domain}Service`, taking `ownerId` as an explicit parameter if the operation is
ownership-sensitive.

```java
DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dto, String ownerId);
List<DictionaryResponseDTO> getDictionariesByIds(List<String> ids, String ownerId);
```

## 3. Service implementation

- `@Transactional` if it writes.
- Ownership check **first** — 403 on a write, 404 on a read by id, filter on a list.
- Map through the MapStruct mapper, never by hand.
- Raise an `OutboundEvent` if the operation should emit one (`messaging`), and only on an actual
  change, not on a plain re-save.

## 4. Repository

Add a derived query method if needed — `findByX`, `deleteByXIn`. Avoid manual JPQL where a derived
name can express the query. See `persistence`.

## 5. Mapper

Extend the domain's MapStruct interface in `common/mapper/`.

## 6. Controller

The pattern from the skill's Quick start: `@Valid @RequestBody`, `WebRequest`, caller from the
token, one service call, `buildResponse` for a mutation or the DTO directly for a read.

## 7. Constants

Success message in `ResponseMessageConstants`, error in `ErrorMessageConstants`, validation message
and any new limit in `DTOMessageConstants`.

## 8. Exception handler

If the endpoint introduces a new exception type, add its handler to `GlobalExceptionHandler` in the
same change.

## 9. Tests

- Service unit tests: happy path, exception path, boundaries, and the ownership branches
  (`unit-testing`).
- A web-slice case for anything a unit test cannot see — the 401, the ownership status, a
  validation 400 (`integration-testing`).

## 10. Documentation

- The row in the API contract table in `docs/agent/verborum.md`.
- The service's `CLAUDE.md`, if the endpoint changes the shape of the service.

Not optional. An undocumented endpoint rots the knowledge base, and the contract table is what the
client teams read.

## 11. Review

Run the `code-reviewer` agent on the change before committing.

## Worked example — the tag endpoints

Tags were added as a **separate controller** (`/dictionaries/{dictionaryId}/tags`) rather than
fields on the dictionary payload, because tagging must not require re-sending — or racing with —
the whole dictionary body. The three endpoints show the standard shapes:

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/dictionaries/{dictionaryId}/tags` | — | `List<DictionaryTagResponseDTO>`, 404 if the dictionary is not yours |
| POST | `/dictionaries/{dictionaryId}/tags` | `{"tag": "travel"}` | `Response` 201, idempotent |
| DELETE | `/dictionaries/{dictionaryId}/tags/{tag}` | — | `Response` 200, no-op if absent |

The tag in the path is normalised the same way as on write, so `DELETE .../tags/Travel` removes
what `POST {"tag":"travel"}` stored.
