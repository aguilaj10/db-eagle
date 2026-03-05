## PostgreSQL Driver Compilation Errors
**Date:** 2026-03-05
**Context:** Task 16 (Create PostgreSQLDDLDialect)

### Issue
`./gradlew :data:compileKotlin` fails with type mismatch errors in PostgreSQLDriver.kt (lines 364-366):
```
e: file:///home/jonathan/desarrollo/db-eagle/data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt:364:29 Incompatible types 'Int' and 'Short'.
e: file:///home/jonathan/desarrollo/db-eagle/data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt:365:29 Incompatible types 'Int' and 'Short'.
e: file:///home/jonathan/desarrollo/db-eagle/data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt:366:29 Incompatible types 'Int' and 'Short'.
```

### Root Cause
DatabaseMetaData constants (`tableIndexClustered`, `tableIndexHashed`, `tableIndexOther`) are defined as Short in JDBC spec, but being used in `when` expression against Int value.

### Impact
- Pre-existing error, unrelated to PostgreSQLDDLDialect implementation
- Blocks data module compilation
- PostgreSQLDDLDialect itself is correct and complete

### Resolution Required
Cast DatabaseMetaData constants to Int or convert `indexTypeValue.toInt()` to Short before comparison.

## Constraint Name Limitation (2026-03-05)

**Issue**: Dropping foreign keys and unique constraints requires constraint names, but current metadata doesn't always provide them.

**Impact**: 
- FKs without names cannot be dropped
- Unique constraints use fallback naming convention which may not match actual names

**Workaround**: 
- Skip FK drops when name is null
- Use convention-based names for unique constraints

**Root Cause**: 
`ForeignKeyRelationship` model lacks constraint name field, metadata extraction doesn't capture constraint names.

**Proposed Solution**:
1. Enhance `ForeignKeyRelationship` to include optional `constraintName: String?`
2. Update metadata extraction queries to fetch constraint names from `information_schema`
3. Update `IndexMetadata` similarly if needed for unique constraints

**Severity**: Medium - affects edit operations with FK/unique constraint changes
