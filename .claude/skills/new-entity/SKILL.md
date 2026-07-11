---
name: new-entity
description: Step-by-step procedure for adding a new JPA entity to a Verborum service, including the Liquibase migration. Use when introducing a new domain object (table) like User, UserStats, VaultEntry, DictionaryStats.
---

# Adding a New Entity

Follow in order.

## 1. Entity class
Create in `{domain}/entity/{Entity}.java`:
```java
@Entity
@Table(name = "table_name")
@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor
public class Entity {

    @Id
    @Column(name = "entity_id", updatable = false, nullable = false)
    private String entityId;              // UUID String, client-provided

    @Column(name = "fk_other_id")         // cross-service ref: plain String, no DB FK
    private String otherId;

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
- UUID String IDs only. No `Long`, no auto-increment.
- JSON columns: `@Column(columnDefinition = "json")` with a `String` field.
- No `@OneToMany`/`@ManyToOne` for cross-aggregate links — use repository lookups
  (project convention; the Dictionary↔Word relationship is intentionally not mapped).

## 2. Liquibase migration
Use the `db-migration` skill for the details. In short:
- New changeset file: `db/changelog/{YEAR}/{MONTH}/{date}-{nn}-changelog.json`
- Register it in `db/changelog/db.changelog-master.json`
- Never modify an existing changeset
- Column types: UUID→`VARCHAR(255)`, Boolean→`BOOLEAN`, JSON→`VARCHAR(255)` (Liquibase side),
  timestamps→`DATETIME`

## 3. Repository
Create `{domain}/repository/{Entity}Repository extends JpaRepository<Entity, String>`.
Add derived query methods as needed.

## 4. DTOs
Create `{Entity}RequestDTO` and `{Entity}ResponseDTO` in `{domain}/dto/` with validation
annotations and constant messages.

## 5. Mapper
Create or extend `common/mapper/{Entity}Mapper` (MapStruct, `componentModel = "spring"`):
`toEntity(dto)` and `toResponseDTO(entity)`.

## 6. Verify
Start the service and confirm Liquibase creates the table. Check via Adminer.

## 7. Review
Run `code-reviewer` before committing.
