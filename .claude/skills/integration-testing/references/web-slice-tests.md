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
