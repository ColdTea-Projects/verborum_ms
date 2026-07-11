---
name: db-migration
description: How to write a Liquibase database migration in Verborum. Use when adding or altering any database table or column. Covers the JSON changeset format, file naming, master registration, and type mappings.
---

# Liquibase Migration

Verborum uses Liquibase with **JSON** changelogs (not XML or YAML).

## Rules

- **Never modify an existing changeset.** Always add a new file. Liquibase tracks checksums;
  editing a run changeset breaks the migration history.
- One logical change per changeset.
- Always register new changeset files in the master changelog.

## File location and naming

New changeset:
`src/main/resources/db/changelog/{YEAR}/{MONTH}/{date}-{nn}-changelog.json`
e.g. `db/changelog/2026/07/11-01-changelog.json`

Master file:
`src/main/resources/db/changelog/db.changelog-master.json`

## Register in master

```json
{
  "databaseChangeLog": [
    { "include": { "file": "db/changelog/2024/02/17-01-changelog.json" } },
    { "include": { "file": "db/changelog/2026/07/11-01-changelog.json" } }
  ]
}
```

## Changeset template (create table)

```json
{
  "databaseChangeLog": [
    {
      "changeSet": {
        "id": "2026-07-11-01",
        "author": "coldtea",
        "objectQuotingStrategy": "QUOTE_ONLY_RESERVED_WORDS",
        "changes": [
          {
            "createTable": {
              "tableName": "users",
              "columns": [
                {
                  "column": {
                    "name": "user_id",
                    "type": "VARCHAR(255)",
                    "constraints": {
                      "nullable": false,
                      "primaryKey": true,
                      "primaryKeyName": "pk_users"
                    }
                  }
                },
                {
                  "column": {
                    "name": "email",
                    "type": "VARCHAR(255)",
                    "constraints": { "nullable": false }
                  }
                },
                {
                  "column": { "name": "creation_dt", "type": "DATETIME",
                    "constraints": { "nullable": false } } },
                { "column": { "name": "update_dt", "type": "DATETIME" } }
              ]
            }
          }
        ]
      }
    }
  ]
}
```

## Type mappings (Java entity → Liquibase type)

| Java | Liquibase type | Note |
|---|---|---|
| UUID `String` id | `VARCHAR(255)` | primary key |
| `String` | `VARCHAR(255)` or `TEXT` | |
| `Boolean` | `BOOLEAN` | |
| JSON `String` | `VARCHAR(255)` | add `columnDefinition = "json"` on the entity side |
| `LocalDateTime` | `DATETIME` | pairs with `@CreationTimestamp`/`@UpdateTimestamp` |

## Verify

Start the service; Liquibase applies pending changesets on boot. Confirm the table/column
exists via Adminer. If a changeset fails, fix it in a NEW changeset — do not edit the failed one
once it has been recorded.
