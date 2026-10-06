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
    @ValidUUID
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
| `@ValidUUID` | `UUIDValidator` | canonical 8-4-4-4-12 hex (regex — `UUID.fromString` accepts `1-1-1-1-1`); null passes |
| `@SupportedLanguage` | `SupportedLanguageValidator` | reads `supported.languages` (trimmed), uppercases with `Locale.ROOT` before matching; null passes |

**Two rules, both learned the hard way (roadmap P4-09).** Until 2026-09-27 both annotations were
inert in every service and invalid ids and language codes were stored:

1. **The annotation needs `@Constraint(validatedBy = …)` plus `message`, `groups` and `payload`.**
   Without `@Constraint`, Bean Validation treats it as plain metadata and never calls the validator —
   no error, no warning, nothing validated.
2. **The validator returns `false`; it never throws.** An exception thrown inside `isValid` is wrapped
   by the framework in a `ValidationException` that no handler maps, so it surfaces as a 500. The
   message comes from the annotation's `message()` constant, and the handler prefixes the field name.

```java
@Constraint(validatedBy = SupportedLanguageValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface SupportedLanguage {
    String message() default INVALID_LANGUAGE_CODE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// validator
return supportedLanguages.contains(language.toUpperCase(Locale.ROOT));
```

A new constraint is not done until a web-slice test proves an invalid value is a 400 — the
annotation compiling proves nothing.

## GlobalExceptionHandler

`@ControllerAdvice`, `@Slf4j`. Every error funnels through it.

| Exception | Status | Log level | Notes |
|---|---|---|---|
| `HttpMessageNotReadableException` | 400 | ERROR | malformed JSON |
| `MethodArgumentNotValidException` | 400 | ERROR | a single-object `@Valid @RequestBody` — field errors joined as `field: message` |
| `HandlerMethodValidationException` | 400 | **WARN** | a constraint on a controller **parameter** (`@ValidUUID` path variable, `@Min` paging param) **or inside a list body** (`@Valid @RequestBody List<…>`) — `param: message`, nested as `bundles.words[0].word`. Without it these are 500s. Do not add class-level `@Validated`: that switches to AOP validation and an unhandled `ConstraintViolationException` |
| `MissingServletRequestParameterException` | 400 | **WARN** | required query parameter absent (ms_marketplace) |
| `MethodArgumentTypeMismatchException` | 400 | **WARN** | e.g. `page=abc`; message names the parameter only, never echoes the value (ms_marketplace) |
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

## Status codes and limits added by the security audit

| Situation | Status | Where |
|---|---|---|
| A known path called with a method it does not have | **405** `HttpRequestMethodNotSupportedException` | a handler in every `GlobalExceptionHandler`; without it the catch-all answered 500 with a stack trace |
| An id-addressed read of a resource that is absent **or** not readable | **404**, never `200 []` and never 403 | e.g. `GET /words/dictionary/{id}` has its own `getWordsByDictionary`; the batch path (filter, 200) is a different endpoint |
| A collection in the body, or an `ids` parameter, over its cap | **400** naming the field | `@Size(max = …)` on the `@RequestBody List` parameter (Spring MVC method validation, no class-level `@Validated`) and on the list field inside a DTO |
| A create that would exceed a per-owner total | **400** `QuotaExceededException` | checked in the service on new rows only; an edit never trips it |
| Well-formed, but incomplete for what it would do, in a way bean validation cannot express | **400** `InvalidRequestException`, message in `field: reason` form | e.g. `isPublic` optional on an update, required on a create, when POST and PUT share one save |
| A request body over `verborum.request.max-body-bytes`, or a chunked body without `Content-Length` | **413** / **411** | `RequestBodyLimitFilter`, before Jackson reads the body |

**A servlet filter writes its own error envelope.** `response.sendError(...)` forwards to `/error`, which
the security chain guards, so the client gets a misleading **401**. Write the `ErrorResponse` JSON with the
injected `ObjectMapper`, as `RequestBodyLimitFilter` does.

**Optional on update, required on create.** When POST and PUT share one service method and either can
create a row, make the field nullable in the DTO and resolve it in the service: absent on an existing row
means "keep the stored value"; absent on a new row is `InvalidRequestException`. Resolve it **before**
anything else in the method reads it (rules, events).
