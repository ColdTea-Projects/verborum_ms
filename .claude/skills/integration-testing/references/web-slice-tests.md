# Web-Layer Slice Tests

`DictionaryControllerWebTest` and `UserControllerWebTest` are the models. They exist because
`contextLoads` was once the only automated proof the HTTP layer worked at all — everything else had
been checked by hand with curl.

Deliberately thin: **one case per behaviour that a wiring mistake would silently break**, not a
re-test of the service logic.

## Setup

```java
@WebMvcTest(DictionaryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DictionaryControllerWebTest {

    private static final String SUB = "b87fb499-2002-47a7-b88f-8ae517932802";

    @Autowired private MockMvc mockMvc;
    @MockBean private DictionaryService dictionaryService;
    @MockBean private JwtDecoder jwtDecoder;

    private static String body(String userId) {
        return """
                {"dictionaryId":"d1","userId":"%s","name":"Test","isPublic":false,
                 "fromLang":"EN","toLang":"DE"}
                """.formatted(userId);
    }
}
```

Request bodies are text blocks with `.formatted(...)` — no fixture files.

## The case list

```java
// 1. no token at all, on a read and on a write
mockMvc.perform(get("/dictionaries/" + SUB))
       .andExpect(status().isUnauthorized());

mockMvc.perform(post("/dictionaries/")
                .contentType(MediaType.APPLICATION_JSON).content(body(SUB)))
       .andExpect(status().isUnauthorized());

// 2. the token subject reaches the service
when(dictionaryService.getDictionariesByUser(SUB)).thenReturn(List.of(new DictionaryResponseDTO()));
mockMvc.perform(get("/dictionaries/" + SUB).with(jwt().jwt(j -> j.subject(SUB))))
       .andExpect(status().isOk());

// 3. requireSelf runs in the controller, so this never reaches the service —
//    exactly the wiring a service-level test cannot cover
mockMvc.perform(get("/dictionaries/someone-else").with(jwt().jwt(j -> j.subject(SUB))))
       .andExpect(status().isForbidden());

// 4. @Valid actually fires
mockMvc.perform(post("/dictionaries/")
                .with(jwt().jwt(j -> j.subject(SUB)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithBlankName(SUB)))
       .andExpect(status().isBadRequest());

// 5. a service exception maps to its status
when(dictionaryService.saveDictionary(any(), anyString()))
        .thenThrow(new ForbiddenOperationException("nope"));
mockMvc.perform(post("/dictionaries/")
                .with(jwt().jwt(j -> j.subject(SUB)))
                .contentType(MediaType.APPLICATION_JSON).content(body(SUB)))
       .andExpect(status().isForbidden());

when(dictionaryService.getDictionaryById(anyString(), anyString()))
        .thenThrow(new RecordNotFoundException("nope"));
mockMvc.perform(get("/dictionaries/dictionary/d1").with(jwt().jwt(j -> j.subject(SUB))))
       .andExpect(status().isNotFound());

// 6. the catch-all does not leak internals
when(dictionaryService.getDictionariesByUser(SUB))
        .thenThrow(new IllegalStateException("duplicate key value violates constraint \"uq_secret\""));
mockMvc.perform(get("/dictionaries/" + SUB).with(jwt().jwt(j -> j.subject(SUB))))
       .andExpect(status().isInternalServerError())
       .andExpect(jsonPath("$.errorDetail").value("Internal server error"));
```

That last one is worth keeping in every service: it is the regression guard for the rule that an
unhandled exception's message never reaches the wire.

## Roles

When an endpoint needs one:

```java
.with(jwt().jwt(j -> j.subject(SUB))
           .authorities(new SimpleGrantedAuthority("ROLE_admin")))
```

Note that this bypasses `extractRealmRoles` — the extractor itself is unit-tested directly against
synthetic `Jwt` objects in `SecurityConfigTest`, which is the cheaper place to cover nested,
missing and malformed claims.

## What not to put here

- Service logic already covered by a unit test
- Every validation annotation on every field — one case proves `@Valid` is wired
- Happy-path reads for their own sake; the interesting cases are the refusals

## What a slice loads, and the tokens it needs

- **`@WebMvcTest` loads web beans only:** controllers, `@ControllerAdvice`, `Filter` beans. A plain
  `@Component` the controller depends on (e.g. ms_user's `RecentLoginGuard`) is **not** loaded, and the
  context fails to start. Add it to `@Import` next to `SecurityConfig` and `GlobalExceptionHandler`, or
  `@MockBean` it if the test is not about it. A new service the controller calls needs a new `@MockBean`.
- **Servlet filters annotated `@Component` do run in the slice** (e.g. `RequestBodyLimitFilter`), with their
  `@Value` properties read from `application.properties`. That is how a 413 can be tested in a slice.
- **Give the token the claims the endpoint reads.** `jwt().jwt(j -> j.subject(SUB))` has no `email`,
  `email_verified` or `auth_time`:

```java
private static RequestPostProcessor verifiedUser() {           // POST/PUT /users/ (SEC-04)
    return jwt().jwt(j -> j.subject(KEYCLOAK_ID).claim("email", VERIFIED_EMAIL).claim("email_verified", true));
}
// account deletion (SEC-13): a recent and a stale login
jwt().jwt(j -> j.subject(KEYCLOAK_ID).claim("auth_time", Instant.now().minusSeconds(60).getEpochSecond()))
jwt().jwt(j -> j.subject(KEYCLOAK_ID).issuedAt(Instant.now()).claim("auth_time", Instant.now().minusSeconds(3600).getEpochSecond()))
```

- **The slice mocks `JwtDecoder`, so it cannot prove audience or issuer validation.** Verify those live:
  a token from another realm client must get 401.
