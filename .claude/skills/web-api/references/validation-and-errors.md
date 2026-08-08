# Validation, the Envelope, and Error Handling

## The response envelope

`Response` for mutations, `ErrorResponse` for every error. Both in `common/response/`, both with
`@Getter` — without it Jackson emits `{}`.

```
Response      { status, message, path, timestamp }
ErrorResponse { status, error, errorDetail, path, timestamp }
```

Built by `ResponseUtils.buildResponse(status, message, id, request)`, which concatenates the
message constant with the affected id and takes `path` from the `WebRequest`.

The envelope's `timestamp` is `OffsetDateTime.now()` — the **server's local offset**. It is a
different field from an entity's `createdAt`/`updatedAt`, which are normalized to UTC. Do not
conflate them.

## Request DTOs

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DictionaryRequestDTO {

    @NotBlank(message = DICTIONARY_DICTIONARY_ID)
    @ValidUUID(fieldName = DICTIONARY_ID)
    private String dictionaryId;

    @NotBlank(message = DICTIONARY_NAME)
    @Size(max = DICTIONARY_NAME_MAX, message = DICTIONARY_NAME_TOO_LONG)
    private String name;

    @NotNull(message = DICTIONARY_IS_PUBLIC)
    private Boolean isPublic;

    @NotBlank(message = DICTIONARY_FROM_LANG)
    @SupportedLanguage
    private String fromLang;
}
```

- `@NotBlank` for Strings, `@NotNull` for boxed types, `@NotEmpty` for collections.
- **Every free-text field carries `@Size(max = …)`**; every bounded number `@Min`/`@Max`. The
  limits are constants in `DTOMessageConstants` because annotation attributes must be compile-time
  constants.
- An optional field stays optional: `level` has range constraints but no `@NotNull`, so clients
  that predate the field keep working.
- Response DTOs carry no validation.

## Custom constraints

Two files each, in `common/utils/`: the annotation and its validator.

| Annotation | Validator | Behaviour |
|---|---|---|
| `@ValidUUID(fieldName = …)` | `UUIDValidator` | throws `InvalidUUIDException` |
| `@SupportedLanguage` | `SupportedLanguageValidator` | reads `supported.languages`, uppercases before matching, throws `InvalidLanguageCodeException` |

**Validators throw a specific exception rather than returning `false`.** That is how the caller
gets a precise message instead of a generic constraint failure.

```java
if (!supportedLanguages.contains(language.toUpperCase(Locale.ROOT))) {
    throw new InvalidLanguageCodeException(INVALID_LANGUAGE_CODE + language);
}
return true;
```

## GlobalExceptionHandler

`@ControllerAdvice`, `@Slf4j`. Every error funnels through it.

| Exception | Status | Log level | Notes |
|---|---|---|---|
| `InvalidUUIDException` | 400 | ERROR | |
| `InvalidLanguageCodeException` | 400 | ERROR | |
| `HttpMessageNotReadableException` | 400 | ERROR | malformed JSON |
| `MethodArgumentNotValidException` | 400 | ERROR | field errors joined as `field: message` |
| `RecordNotFoundException` | 404 | ERROR | |
| `NoResourceFoundException` | 404 | **WARN** | unmapped URL — a client mistake or a scanner |
| `ForbiddenOperationException` | 403 | **WARN** | |
| `Exception` | 500 | ERROR | catch-all |

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
    log.error(Exception.class.getCanonicalName(), ex);
    return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            Exception.class.getSimpleName(), INTERNAL_SERVER_ERROR, request);
}
```

**The catch-all does not put `ex.getMessage()` on the wire.** An unhandled exception is by
definition one nobody vetted the message of, and those messages carry internals — a Postgres
constraint violation names the table, column and constraint; a null-pointer names a field. The full
exception is logged; the caller gets a fixed string. The specific handlers *do* return
`ex.getMessage()`, and that is safe because those messages are the project's own constants.

`NoResourceFoundException` needs its own handler or a plain 404 is reported as a 500 — which is
exactly what happened once actuator exposure was narrowed and `/actuator/env` started returning
"No static resource".

**Adding a new exception type means adding its handler in the same change.**
