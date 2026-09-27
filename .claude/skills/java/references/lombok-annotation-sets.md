# Lombok Annotation Sets

Lombok 1.18.30, `provided` scope. The combination is fixed per class kind. Each exists for a
reason, and mixing them causes real bugs.

| Class kind | Annotations | Why |
|---|---|---|
| Entity | `@Getter @Setter @ToString @Entity @Builder @NoArgsConstructor @AllArgsConstructor @Table` | JPA needs the no-args constructor; `@Builder` needs the all-args one |
| DTO | `@Data @NoArgsConstructor @AllArgsConstructor @Builder` | Jackson needs no-args + setters |
| Event DTO | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` | same, over the wire |
| Service / Controller / Component | `@RequiredArgsConstructor` + `private final` fields | constructor injection |
| `Response` / `ErrorResponse` | `@Getter @Builder @AllArgsConstructor @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)` | **`@Getter` is required** |

## The `@Getter` trap

`Response` and `ErrorResponse` are serialized by Jackson through their getters. Without `@Getter`
the response body is an empty `{}` — a 200 that carries nothing, and nothing anywhere reports an
error. Both classes in both services carry it; any new envelope class must too.

## Injection

Field injection is banned project-wide. `@RequiredArgsConstructor` plus `private final` is the only
accepted form — in services, controllers, listeners and configuration classes alike.

```java
// correct
@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryMapper dictionaryMapper;
}

// wrong
@Service
public class DictionaryServiceImpl {
    @Autowired
    private DictionaryRepository dictionaryRepository;
}
```

## `@Slf4j`

Added to any class that logs — listeners, the outbound publisher, `GlobalExceptionHandler`. Log
level carries meaning here: a client mistake (an unknown URL, a refused write) is `WARN`; a genuine
fault is `ERROR`.

## Entity example

```java
@Getter @Setter @ToString
@Entity @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "dictionaries")
public class Dictionary {
    @Id
    @Column(name = "dictionary_id", updatable = false, nullable = false)
    private String dictionaryId;
}
```

## DTO example

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DictionaryRequestDTO {
    @NotBlank(message = DICTIONARY_NAME)
    @Size(max = DICTIONARY_NAME_MAX, message = DICTIONARY_NAME_TOO_LONG)
    private String name;
}
```
