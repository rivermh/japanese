# Database migration operations

Japanese uses Flyway for versioned schema changes. Hibernate validates the migrated schema and does not create or alter runtime tables.

## Runtime modes

- A new, empty database runs `db/migration/mysql/V1__baseline_schema.sql`, then Hibernate validates the result.
- An existing database that was previously managed by `ddl-auto=update` must be backed up, checked, and explicitly adopted once at baseline version 1.
- `FLYWAY_BASELINE_ON_MIGRATE` defaults to `false`. Keep it false after the one-time adoption.
- Tests use the matching version under `db/migration/h2` and run Hibernate validation against H2.

Automatic baseline is deliberately off by default. Baseline records the existing schema as version 1 without replaying V1; it does not prove that the schema matches V1. Enabling it against the wrong non-empty database can mark that database as managed, so confirm the connection target and restore rehearsal first.

## Before any migration

1. Stop application instances that can write to the database.
2. Confirm the database host, port, and schema name from `DB_URL`. Do not rely on a shell's previous environment.
3. Record the current application commit and the current table and row counts for critical tables.
4. Create a consistent backup. Let `mysqldump` prompt for the password; never put it in the command or this repository.

```powershell
mysqldump --host=<host> --port=<port> --user=<user> -p `
  --single-transaction --routines --triggers --events `
  --set-gtid-purged=OFF <database> `
  --result-file="C:\backups\japanese-before-flyway.sql"

Get-Item "C:\backups\japanese-before-flyway.sql"
Get-FileHash "C:\backups\japanese-before-flyway.sql" -Algorithm SHA256
```

Do not continue if the dump is empty or `mysqldump` reports an error.

## Restore rehearsal

Restore into a separate verification database before changing the original database.

```sql
CREATE DATABASE japanese_restore_check
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Open the MySQL client for the disposable database with `mysql --host=<host> --port=<port> --user=<user> -p japanese_restore_check`, then load the dump:

```text
SOURCE C:/backups/japanese-before-flyway.sql;
```

Compare table counts and critical row counts with the source. Start the application against the restored copy only after the counts match.

## Adopt an existing schema

The V1 SQL is for empty databases. It must not be replayed over existing tables.

1. Complete the backup and restore rehearsal above.
2. Compare the existing schema with the V1 Entity baseline, including nullable columns, lengths, unique constraints, indexes, foreign keys, and enum/string columns. Resolve drift deliberately; do not turn Hibernate schema updates back on.
3. Confirm that the existing schema has no `flyway_schema_history` table and that it is the intended Japanese database.
4. Start one application instance once with `FLYWAY_BASELINE_ON_MIGRATE=true`.
5. Flyway records baseline version 1 and skips V1. Hibernate then validates the existing schema. No application traffic should be enabled until validation succeeds.
6. Confirm the baseline row and application data:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

7. Stop the instance, remove `FLYWAY_BASELINE_ON_MIGRATE` (or set it to `false`), and start normally.

If Hibernate validation fails, stop and investigate the reported drift. Do not use `ddl-auto=update` as a repair step. The baseline row does not delete or rewrite application data, but the schema must be reconciled before serving traffic.

## New database

Create an empty MySQL schema with the production character set and grant the application user only the permissions required to migrate and run the application. Start normally with `FLYWAY_BASELINE_ON_MIGRATE=false`. Flyway runs V1, records it in `flyway_schema_history`, and Hibernate validates the result.

## Restore after a failed deployment

Schema migrations are forward-only. If a future migration cannot be corrected safely, stop all writers and restore the verified backup to a clean database or isolated MySQL instance. Recheck the checksum and critical row counts, point the application to the restored database, and verify `flyway_schema_history` before reopening traffic. Never drop or overwrite the only copy of the affected database.
