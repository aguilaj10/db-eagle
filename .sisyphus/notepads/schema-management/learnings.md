
## Primary Key Population Implementation
**Date:** 2026-03-05

### PostgreSQL Driver
- Added `getPrimaryKeyColumns(table: String)` method
- Uses JDBC `metaData.getPrimaryKeys(null, "public", table)` to fetch PK metadata
- Extracts `COLUMN_NAME` from result set
- Returns sorted list of column names (alphabetical for consistency)
- Integrated into `getSchema()` to populate `TableMetadata.primaryKey`

### SQLite Driver
- Added `getPrimaryKeyColumns(table: String)` method
- Uses `PRAGMA table_info('$escaped')` to fetch column metadata
- Filters columns where `pk` > 0 (pk column indicates position in composite PK, 0 = not part of PK)
- Returns list of column names (maintains original PRAGMA order)
- Integrated into `getSchema()` to populate `TableMetadata.primaryKey`

### Key Differences
- PostgreSQL: Uses standard JDBC metadata API
- SQLite: Uses PRAGMA commands (SQLite-specific)
- PostgreSQL: Alphabetically sorted PK columns
- SQLite: PK columns in natural PRAGMA order (by pk number)

### Verification
- Both implementations compile successfully
- All existing tests pass
- No new test failures introduced
