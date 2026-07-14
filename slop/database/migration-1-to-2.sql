-- BambuddySpool Room migration from schema version 1 to 2.
-- The canonical executable statement list is BambuddyDatabaseMigrations.V1_TO_V2_STATEMENTS.
-- Room KMP applies the statements inside its migration transaction.

-- Rebuild tables instead of using ALTER TABLE DROP COLUMN, which is unavailable on supported API 23 SQLite.
-- Existing snapshot data is copied except for the removed unused columns and indexes.
-- Register BambuddyDatabaseMigrations.all through RoomDatabase.Builder.addMigrations when constructing the database.
