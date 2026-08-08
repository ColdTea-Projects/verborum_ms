---
name: java
description: Java 17 language conventions for the Verborum backend — Lombok annotation sets per class kind, constants classes, utility classes, records, and locale/null handling. Use when writing or modifying any .java file in this project.
---

# Java (17)

Language-level style. Framework wiring is in `spring-boot`; layout in
`spring-boot-app-architecture`. Boot 3 means the `jakarta.*` namespace — never `javax.*`.

## Quick start

```java
@Service
@RequiredArgsConstructor                       // constructor injection — never @Autowired
public class DictionaryServiceImpl implements DictionaryService {
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryMapper dictionaryMapper;
}
```

## The five rules that matter most

1. **Never `@Autowired`.** `@RequiredArgsConstructor` + `private final`, everywhere.
2. **The Lombok annotation set is fixed per class kind** — entity, DTO, event, component,
   response envelope. Do not improvise a different combination.
3. **`Response` and `ErrorResponse` must carry `@Getter`.** Jackson serializes them through
   getters; without it the body is an empty `{}` — a 200 that carries nothing, with no error
   anywhere.
4. **No inline message strings.** Every message and every numeric field limit is a
   `public static final` constant in one of the three `*Constants` classes.
5. **Always pass an explicit locale** to case conversion: `tag.trim().toLowerCase(Locale.ROOT)`.
   Default-locale conversion mangles Turkish `I`, and `TR` is a supported language here.

See [references/lombok-annotation-sets.md](references/lombok-annotation-sets.md) for the full
per-class-kind table, and
[references/constants-and-utilities.md](references/constants-and-utilities.md) for the constants
split, utility-class shape, and static-import style.

## Modern Java the codebase uses

- **Pattern matching for `instanceof`** — `if (auth instanceof JwtAuthenticationToken jwtAuth)`
- **`Stream.toList()`**, not `collect(Collectors.toList())`
- **`Optional` chaining over branching** — `findByX(...).map(mapper::toDTO).orElseGet(...)` is the
  find-or-create idiom behind every idempotent write
- **Records** for small internal value carriers, not for DTOs or entities (those need mutability
  for Jackson and JPA):

```java
public record OutboundEvent(String routingKey, Object payload) { }
```

- **Text blocks** for JSON bodies in tests
- **`List.of()` / `Map.of()`** for immutable literals, including empty returns

## Null and defensiveness

- Return an empty collection, never `null`. Batch and list endpoints return `List.of()` when
  nothing matches — never null, never a 404.
- Code on the authentication path must degrade to "no authorities" rather than throw: a malformed
  claim turning into a 500 converts a bad token into a service outage.

## Comments

Match the surrounding density, which is high here and deliberately so. A comment explains **why**,
and especially why an obvious-looking alternative is wrong. Do not narrate what the code says.

## Pitfalls

- `@Autowired` anywhere
- An envelope or response class without `@Getter`
- Inline message strings
- `javax.*` imports
- `toLowerCase()` / `toUpperCase()` without `Locale.ROOT`
- `Long` or auto-increment ids — see `persistence`
