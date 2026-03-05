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
