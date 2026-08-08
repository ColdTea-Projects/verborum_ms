# Liquibase Migrations

JSON changelogs, never XML or YAML. **Never modify an existing changeset** — fix forward.

## Location and registration

```
src/main/resources/db/changelog/{YEAR}/{MONTH}/{DD}-{nn}-changelog.json
src/main/resources/db/changelog/db.changelog-master.json
```

Append the include at the end of the master file:

```json
{ "include": { "file": "db/changelog/2026/07/23-01-changelog.json" } }
```

## Changeset conventions

- `id`: `{YYYYMMDD}-{nn}-{slug}`, e.g. `20260723-01-dictionary-tags`
- `author`: the person or agent that wrote it
- `comment`: a real explanation of **why** — these are the migration history's only documentation
- `rollback`: always provide one
- `objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS"` on `createTable` changesets
- One logical change per changeset

## Create table, with FK, unique constraint and index

```json
{
  "databaseChangeLog": [{
    "changeSet": {
      "id": "20260723-01-dictionary-tags",
      "author": "claude",
      "comment": "Why this table exists and why the FK is correct here.",
      "changes": [
        { "createTable": {
            "tableName": "dictionary_tags",
            "columns": [
              { "column": { "name": "tag_id", "type": "VARCHAR(255)",
                  "constraints": { "nullable": false, "primaryKey": true,
                                   "primaryKeyName": "pk_dictionary_tags" } } },
              { "column": { "name": "fk_dictionary_id", "type": "VARCHAR(255)",
                  "constraints": { "nullable": false } } },
              { "column": { "name": "tag", "type": "TEXT",
                  "constraints": { "nullable": false } } },
              { "column": { "name": "creation_dt", "type": "TIMESTAMP WITH TIME ZONE",
                  "constraints": { "nullable": false } } }
            ] } },
        { "addForeignKeyConstraint": {
            "baseTableName": "dictionary_tags", "baseColumnNames": "fk_dictionary_id",
            "referencedTableName": "dictionaries", "referencedColumnNames": "dictionary_id",
            "constraintName": "fk_dictionary_tags_dictionary", "onDelete": "CASCADE" } },
        { "addUniqueConstraint": {
            "tableName": "dictionary_tags", "columnNames": "fk_dictionary_id, tag",
            "constraintName": "uq_dictionary_tags_dictionary_tag" } },
        { "createIndex": {
            "tableName": "dictionary_tags", "indexName": "idx_dictionary_tags_tag",
            "columns": [ { "column": { "name": "tag" } } ] } }
      ],
      "rollback": [ { "dropTable": { "tableName": "dictionary_tags" } } ]
    }
  }]
}
```

## Alter a column — raw SQL with an explicit cast

```json
{ "sql": { "sql": "ALTER TABLE words ALTER COLUMN word_meta TYPE json USING word_meta::json;" } }
```

With the inverse as the rollback. This is how the three type changes in ms_dictionary were done:

| Change | SQL |
|---|---|
| `VARCHAR(255)` → `json` | `... TYPE json USING word_meta::json` |
| `VARCHAR(255)` → `TEXT` | `... TYPE TEXT` |
| `timestamp` → `timestamptz` | `... TYPE timestamptz USING creation_dt AT TIME ZONE 'UTC'` |

The `USING` clause is what makes the cast deterministic. Note the comment on the timestamp one:
existing naked values were interpreted as UTC, which was safe only because the data was throwaway.
State that kind of assumption in the `comment`.

## Column type mapping

| Java | Liquibase / Postgres | Note |
|---|---|---|
| UUID `String` id | `VARCHAR(255)` | primary key |
| short `String` | `VARCHAR(255)` | |
| unbounded text | `TEXT` | `word`, `translation`, `tag` |
| JSON `String` | `json` | pair with `@JdbcTypeCode(SqlTypes.JSON)` |
| `Boolean` | `BOOLEAN` | |
| `Integer` | `INT` | |
| `OffsetDateTime` | `TIMESTAMP WITH TIME ZONE` | **not `DATETIME`** |

The two oldest changesets in ms_dictionary's master file still say `DATETIME`. They predate the
zone-aware change and were migrated later. Do not copy them.

## Verifying

Liquibase applies pending changesets at service startup. Confirm the table and the column types in
Adminer, or:

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -c "\d dictionary_tags"
```

If a changeset fails, fix it in a **new** changeset once the failed one has been recorded.
