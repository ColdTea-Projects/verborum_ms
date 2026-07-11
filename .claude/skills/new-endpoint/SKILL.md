---
name: new-endpoint
description: Step-by-step procedure for adding a new REST endpoint to a Verborum service. Use when adding any GET/POST/PUT/DELETE endpoint. Ensures every layer and the documentation are updated in the correct order.
---

# Adding a New Endpoint

Follow these steps in order. Do not skip the documentation step at the end.

## 1. DTO
If the endpoint needs a new request or response shape, create it in the domain's `dto/`
package. Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor`. Add Bean Validation
annotations (`@NotBlank`, `@ValidUUID`, `@SupportedLanguage`) with messages from
`DTOMessageConstants`.

## 2. Service interface
Add the method signature to the domain's `{Domain}Service` interface.

## 3. Service implementation
Implement in `{Domain}ServiceImpl`. Rules:
- Constructor-injected `final` dependencies.
- `@Transactional` if it writes.
- Throw `RecordNotFoundException` (or the appropriate type) for missing records.
- Map entities via the MapStruct mapper, never by hand.
- If the operation should emit an event (e.g. visibility change), publish from here —
  see the `new-event` skill.

## 4. Repository
If a new query is needed, add a derived query method to the `{Domain}Repository`
(e.g. `findByX`, `deleteByXIn`). Do not write manual JPQL unless a derived method can't
express it.

## 5. Mapper
If a new mapping is needed, add it to the domain's MapStruct mapper interface.

## 6. Controller
Add the endpoint to `{Domain}Controller`:
- Mutations return `ResponseEntity<Response>` built via `ResponseUtils.buildResponse(...)`.
- Reads return the data (or `ResponseEntity<List<...>>`) directly.
- Use `@Valid @RequestBody` for bodies, `@PathVariable` / `@RequestParam` for params.
- For ownership-sensitive operations, take `userId` from the JWT via
  `SecurityUtils.getCurrentUserId()` — never trust the request body (see verborum-security).

## 7. Constants
Add any new success message to `ResponseMessageConstants`, error message to
`ErrorMessageConstants`, validation message to `DTOMessageConstants`.

## 8. Exception handling
If the endpoint can throw a new exception type, add a handler to `GlobalExceptionHandler`.

## 9. Tests
Add unit tests for the new service method (happy + exception + boundary). See the
`verborum-testing` skill or delegate to `test-writer`.

## 10. Update documentation
Add the endpoint to the API contract table in `docs/agent/verborum.md`. This step is
mandatory — an undocumented endpoint rots the knowledge base.

## 11. Review
Run the `code-reviewer` subagent on the change before committing.
