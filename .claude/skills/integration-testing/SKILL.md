---
name: integration-testing
description: Context-loading tests for Verborum — MockMvc web-layer slices with the real security filter chain, full-context transaction tests, and manual end-to-end verification. Use when testing controllers, security wiring, transaction boundaries, or event delivery.
---

# Integration Testing

Anything that loads a Spring context. Isolated Mockito tests are `unit-testing`.

`spring-boot-starter-test` plus `spring-security-test` for the `jwt()` post-processor.
`spring-test-dbunit` and `dbunit` are declared in both poms but no fixtures use them — do not
introduce them without discussing it.

**Reach for the cheapest tier that can actually see the bug.**

## Quick start — the web-layer slice

The default for controller work. Loads the MVC layer plus the imported configuration, nothing else.

```java
@WebMvcTest(DictionaryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DictionaryControllerWebTest {

    private static final String SUB = "b87fb499-2002-47a7-b88f-8ae517932802";

    @Autowired private MockMvc mockMvc;
    @MockBean private DictionaryService dictionaryService;

    /** The filter chain needs a decoder bean; the jwt() post-processor supplies the token itself. */
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/dictionaries/" + SUB)).andExpect(status().isUnauthorized());
    }
}
```

Three pieces are load-bearing:

- **`@Import(SecurityConfig.class)`** — without it the slice runs with default security and the
  401/403 assertions prove nothing about the real chain.
- **`@Import(GlobalExceptionHandler.class)`** — without it the exception-to-status mapping is
  untested.
- **`@MockBean JwtDecoder`** — the resource-server chain requires the bean to exist, or the context
  fails to start.

The full case list — the token subject reaching the service, ownership statuses, validation 400,
exception mapping, and the catch-all not leaking internals — is in
[references/web-slice-tests.md](references/web-slice-tests.md).

**Every new endpoint should add at least the 401 case and its ownership status here.**

## Listener tests are not integration tests

A `@RabbitListener` class is a component with one dependency, so it is tested with plain Mockito —
no context. Older guidance prescribed a full-context test with mocked beans here; the code has
moved on. See `unit-testing`.

## Full context

Expensive, and it needs the compose stack up because it runs against the real database. Use it only
when a transaction boundary or the whole wiring is the thing under test.

- **`Ms{Name}ApplicationTests`** — `contextLoads()`. Cheap insurance that every bean still resolves.
- **`UserDeletedAfterCommitTest`** — drives a real `TransactionTemplate` and asserts that a
  **rollback publishes nothing** and a commit publishes exactly once. `RabbitTemplate` and
  `KeycloakUserService` are mocked, so nothing leaves the process.

That second one is the test that justifies the after-commit publisher, and it proves the guarantee
**once for both services** — they share the listener implementation, so ms_dictionary does not
repeat it. Details and the cleanup rule are in
[references/context-tests.md](references/context-tests.md).

## Manual end-to-end verification

Automated tests stop at the process boundary. Real broker and real identity-server delivery are
verified by hand — recipes in `infra-ops`: bring up the root compose stack, get a token from the dev
client, exercise the endpoint, then check the Management UI for the message and the dead-letter
queue for failures. Anything verified only by hand should say so in the change notes.

## Running

```bash
./mvnw -pl ms_dictionary test
./mvnw clean verify
```

Full-context tests need Postgres up; the slice tests do not.

## Pitfalls

- A web slice without `@Import(SecurityConfig.class)` — the auth assertions are then fiction
- Forgetting `@MockBean JwtDecoder`
- Loading a full context where a slice or a plain Mockito test would do
- Re-testing service logic through MockMvc
- Leaving rows behind in the development database
- Trusting mocked repositories for flush, clear or cascade behaviour — add a real-database test (references)
- A slice missing an `@Import` for a plain `@Component` the controller uses, or a token without the claims it reads
