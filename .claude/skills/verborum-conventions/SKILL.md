---
name: verborum-conventions
description: Verborum's Java and Spring Boot coding conventions. Use whenever writing or modifying any Java code in this project — entities, services, controllers, DTOs, mappers, config. Ensures new code matches the ms_dictionary reference implementation.
---

# Verborum Coding Conventions

Full details live in `docs/agent/clean-code.md` and `docs/agent/java-spring.md`. Read those
for complete patterns and code examples. This skill is the standing summary — apply it to all
Java work.

## Non-negotiable rules

- **Injection:** `@RequiredArgsConstructor` + `private final` fields. Never `@Autowired`.
- **Services:** always an interface + an `impl/` implementation.
- **IDs:** UUID `String`, client-provided, never `Long` or DB-generated.
- **Cross-service refs:** plain String columns, no DB-level foreign keys.
- **Mapping:** DTO ↔ Entity only via MapStruct (`componentModel = "spring"`).
- **Messages:** no inline strings — use `DTOMessageConstants`, `ErrorMessageConstants`,
  `ResponseMessageConstants`.
- **Response objects:** `Response` and `ErrorResponse` MUST have `@Getter` or Jackson
  serializes empty `{}`.
- **Transactions:** `@Transactional` on write methods in the service layer.
- **Exceptions:** caught centrally in `GlobalExceptionHandler`; add a handler for each new type.
- **Package:** `de.coldtea.verborum.ms{service}.{domain}.{layer}`.

## Reference implementation

When unsure, copy the pattern from `ms_dictionary`. It is the canonical example for every
convention above. New services must be structurally identical to it.

## Naming

| Thing | Convention | Example |
|---|---|---|
| Entity | PascalCase singular | `Dictionary` |
| Table | snake_case plural | `dictionaries` |
| Column | snake_case | `fk_user_id` |
| DTO | `{Entity}{Request/Response}DTO` | `DictionaryRequestDTO` |
| Service | `{Entity}Service` + `{Entity}ServiceImpl` | `DictionaryServiceImpl` |
| Controller | `{Entity}Controller` | `DictionaryController` |
