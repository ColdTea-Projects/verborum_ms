# Java & Spring Boot Skills

## Versions
- Java 17
- Spring Boot 3.2.2
- Maven (wrapper included — use `./mvnw`, not system `mvn`)

---

## Spring Boot Project Setup

Each service is a standalone Spring Boot app. Required `pom.xml` dependencies for a new service:

```xml
<!-- Core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
</dependency>

<!-- Database -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.3.8</version>
</dependency>
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
    <version>4.25.1</version>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
</dependency>

<!-- MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
</dependency>

<!-- OpenAPI / Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <version>5.6.2</version>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>2.23.0</version>
</dependency>
```

---

## JPA / Hibernate

### Entity Best Practices

```java
@Entity
@Table(name = "table_name")
@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor
public class MyEntity {

    @Id
    @Column(name = "entity_id", updatable = false, nullable = false)
    private String entityId;   // UUID string — never Long/auto-increment

    @Column(name = "some_field", nullable = false)
    private String someField;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private LocalDateTime creationTimestamp;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private LocalDateTime updateTimestamp;
}
```

### JSON Columns (word_meta, translation_meta)
These columns are `json` type in PostgreSQL, stored as `String` in Java.
When creating new json columns, use:
```java
@Column(name = "my_json_field", columnDefinition = "json")
private String myJsonField;
```

### Cross-Service FK Pattern
Never create a DB-level FK to a table in another service's DB.
Use a plain string column to hold the referenced ID:
```java
@Column(name = "fk_user_id")  // references ms_user, but no DB FK
private String userId;
```

### JPA Relationships
The `@OneToMany` / `@ManyToOne` between Dictionary and Word is intentionally NOT active.
Prefer explicit repository calls over JPA lazy loading for cross-aggregate queries.
This avoids N+1 problems and keeps service boundaries clean.

---

## Spring Data JPA Repositories

Extend `JpaRepository<Entity, IdType>`. Use derived query methods for simple queries:

```java
public interface DictionaryRepository extends JpaRepository<Dictionary, String> {
    List<Dictionary> findByUserId(String userId);
    List<Dictionary> findByFromLang(String fromLang);
    List<Dictionary> findByIsPublicTrue();
}
```

For bulk deletes, use the method naming convention Verborum already uses:
```java
void deleteByDictionaryIdIn(Collection<String> dictionaryIds);
void deleteWordsByDictionaryId(String dictionaryId);
```

Use `saveAndFlush()` instead of `save()` for immediate persistence (already established pattern).
Use `saveAllAndFlush()` for batch saves.

---

## Validation

### Bean Validation on DTOs
```java
@NotBlank(message = MY_CONSTANT)   // for Strings
@NotNull(message = MY_CONSTANT)    // for non-String fields
@NotEmpty(message = MY_CONSTANT)   // for collections
```

Always use `@Valid` in controller method parameters:
```java
public ResponseEntity<Response> create(@Valid @RequestBody MyRequestDTO dto, WebRequest request)
```

### Custom Constraint Annotation
```java
// Step 1: Define the annotation
@Constraint(validatedBy = MyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MyConstraint {
    String message() default "Invalid value";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String fieldName() default "";
}

// Step 2: Implement the validator
@Component
public class MyValidator implements ConstraintValidator<MyConstraint, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        // throw a specific exception — do not return false
        if (invalid) throw new MySpecificException("...");
        return true;
    }
}
```

---

## Actuator
Always include actuator. Expose all endpoints in dev:
```properties
management.endpoints.web.exposure.include=*
management.info.env.enabled=true
info.app.name=Verborum {Service} Micro Service
info.app.version=1.0.0
```

---

## Liquibase — JSON Format

Master changelog:
```json
{
  "databaseChangeLog": [
    { "include": { "file": "db/changelog/2024/02/17-01-changelog.json" } }
  ]
}
```

Individual changeset:
```json
{
  "databaseChangeLog": [
    {
      "changeSet": {
        "id": "{timestamp}-{nn}",
        "author": "{author}",
        "objectQuotingStrategy": "QUOTE_ONLY_RESERVED_WORDS",
        "changes": [
          {
            "createTable": {
              "tableName": "my_table",
              "columns": [
                {
                  "column": {
                    "name": "my_id",
                    "type": "VARCHAR(255)",
                    "constraints": {
                      "nullable": false,
                      "primaryKey": true,
                      "primaryKeyName": "pk_my_table"
                    }
                  }
                }
              ]
            }
          }
        ]
      }
    }
  ]
}
```

**Liquibase type mappings:**
- UUID string → `VARCHAR(255)`
- Boolean → `BOOLEAN`
- Text → `VARCHAR(255)` or `TEXT`
- JSON → `VARCHAR(255)` (Liquibase) but add `columnDefinition = "json"` in Java entity
- Timestamps → `DATETIME`

---

## Docker Compose Template

Each service needs its own `docker-compose.yml`:
```yaml
version: '3.8'

services:
  db:
    image: postgres:14-alpine
    ports:
      - "{port}:5432"
    environment:
      POSTGRES_USER: coldtea
      POSTGRES_PASSWORD: qwerty
      POSTGRES_DB: {dbname}
  admin:
    image: adminer
    restart: always
    depends_on:
      - db
    ports:
      - {adminport}:8080
```

Use different host ports for each service to avoid conflicts:
- ms_dictionary: DB 5432, Adminer 8080
- ms_user: DB 5433, Adminer 8081
- ms_marketplace: DB 5434, Adminer 8082

---

## MapStruct Tips

- Always use `componentModel = "spring"` so MapStruct beans are Spring-managed
- Multi-source mapping uses method parameters — each param becomes a source
- For fields that differ in name between DTO and entity, use `@Mapping(source=, target=)`
- If a field should be ignored: `@Mapping(target = "fieldName", ignore = true)`

```java
@Mapper(componentModel = "spring")
public interface WordMapper {
    // Injecting dictionaryId from a separate param
    @Mapping(source = "dictionaryId", target = "dictionaryId")
    Word toWord(String dictionaryId, WordRequestDTO wordRequestDTO);

    WordResponseDTO toWordResponseDTO(Word word);
}
```

---

## Common Pitfalls to Avoid

- ❌ Don't use `@Autowired` — use `@RequiredArgsConstructor` + `final` fields
- ❌ Don't modify existing Liquibase changesets — add new ones
- ❌ Don't use `Long` IDs — all IDs are UUID strings
- ❌ Don't generate IDs server-side — client provides UUIDs
- ❌ Don't use `save()` for entities that need immediate consistency — use `saveAndFlush()`
- ❌ Don't inline error/response messages as strings — use constants
- ❌ Don't add DB-level FKs for cross-service references
- ❌ Don't activate the commented-out JPA `@OneToMany`/`@ManyToOne` without discussion
