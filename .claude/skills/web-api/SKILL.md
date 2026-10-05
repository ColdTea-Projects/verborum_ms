---
name: web-api
description: The REST layer of a Verborum service — controller patterns, the Response envelope, DTO validation, exception handling, and status-code semantics. Use when adding or changing any HTTP endpoint, request DTO, validator, or error mapping.
---

# Web API

The HTTP layer. Layering: `spring-boot-app-architecture`. Auth and ownership: `security`.
Data below it: `persistence`. Live contract table: `docs/agent/verborum.md`.

## Quick start

```java
@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    // Mutation → ResponseEntity<Response>, built by ResponseUtils
    @PostMapping("/")
    public ResponseEntity<Response> createDictionary(
            @Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {
        DictionaryResponseDTO saved = dictionaryService.saveDictionary(dictionary, getCurrentUserId());
        return buildResponse(HttpStatus.CREATED, DICTIONARY_SAVED_SUCCESSFULLY,
                saved.getDictionaryId(), request);
    }

    // Read → the data itself, wrapped only in ResponseEntity
    @GetMapping("/dictionary/{dictionaryId}")
    public ResponseEntity<DictionaryResponseDTO> getDictionaryById(@PathVariable String dictionaryId) {
        return new ResponseEntity<>(
                dictionaryService.getDictionaryById(dictionaryId, getCurrentUserId()), HttpStatus.OK);
    }
}
```

## Core rules

- **Mutations return `ResponseEntity<Response>`**; **reads return the DTO directly**, never wrapped.
- **`@Valid` on every `@RequestBody`.** Missing it disables validation silently.
- **`WebRequest request`** on every mutation — the envelope's `path` comes from it.
- **The caller comes from the token**, static-imported as `getCurrentUserId()` and passed into the
  service as an explicit argument. A `userId` path variable kept for client compatibility gets
  `requireSelf(userId)` first.
- **No business logic in the controller** — it calls one service method.

## Status-code semantics

Not style — a security contract, from roadmap P3-05 and P3-08.

| Situation | Status |
|---|---|
| Write naming another user's resource | **403** |
| Read of another user's resource **by id** | **404**, so ids cannot be probed |
| Batch or list endpoint | **filter to the caller**, 200 with what they own |
| Create, update | 201 |
| Delete, read | 200 |
| Delete of something absent | 200, no-op |
| Batch read with no matches | 200 + empty list, never 404 |

A create endpoint a client may retry is find-or-create, backed by a UNIQUE constraint: adding an
existing tag returns the existing row instead of failing.

## Validation and errors

See [references/validation-and-errors.md](references/validation-and-errors.md) for the request-DTO
template, the custom constraint annotations (`@ValidUUID`, `@SupportedLanguage`), and the full
`GlobalExceptionHandler` table. Two rules to carry without opening it:

- Every free-text field has a `@Size(max = …)` and every bounded number a `@Min`/`@Max`, with the
  limits as constants — field limits are enforced server-side, not only in the clients.
- **The catch-all handler must not put `ex.getMessage()` on the wire.** An unhandled exception's
  message is unvetted and leaks internals; a Postgres violation names the table, column and
  constraint.

## Workflow: adding an endpoint

Full checklist in [references/add-an-endpoint.md](references/add-an-endpoint.md) — eleven steps
from DTO through to documentation and review. The steps most often skipped, and most often the
reason a change ships broken, are the last three: tests, the API contract table in
`docs/agent/verborum.md`, and the service's `CLAUDE.md`.

## OpenAPI

`springdoc-openapi-starter-webmvc-ui`. Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`,
both `permitAll` in `SecurityConfig`. Nothing is annotated by hand — the spec comes from the
signatures. Both are switched by `SWAGGER_ENABLED` (SEC-12): on locally, `false` outside local development.

## Pitfalls

- `@RequestBody` without `@Valid`
- Trusting a `userId` from the body or path for ownership
- 403 on an id-addressed read (leaks existence), or 404 on a write
- Wrapping a read in `Response`
- A new exception type with no handler
- Shipping an endpoint that never reaches the contract table
