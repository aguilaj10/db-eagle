
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

## PostgreSQL DDL Dialect Implementation
**Date:** 2026-03-05

### Implementation Details
- Created `data/src/main/kotlin/com/dbeagle/ddl/PostgreSQLDDLDialect.kt`
- Implemented as singleton object (Kotlin `object`)
- All DDLDialect methods implemented for PostgreSQL

### Key Features
- `quoteIdentifier()`: Uses double quotes, escapes embedded quotes as ""
- Feature flags: All true (sequences, ALTER COLUMN, DROP COLUMN, IF EXISTS)
- Type mappings:
  - TEXT → VARCHAR
  - INTEGER → INTEGER
  - BIGINT → BIGINT
  - DECIMAL → NUMERIC
  - BOOLEAN → BOOLEAN
  - DATE → DATE
  - TIMESTAMP → TIMESTAMP WITH TIME ZONE
  - BLOB → BYTEA

### Design Decisions
- No docstrings: DDLDialect interface already has comprehensive documentation
- Self-documenting code: Type mappings clear from when expression
- Quote escaping: Standard PostgreSQL behavior (double the quote)

### Verification
- File created successfully at correct location
- Implements all DDLDialect interface methods
- No LSP/compilation errors in PostgreSQLDDLDialect itself

## SequenceDDLBuilder Implementation
**Date:** 2026-03-05

### Implementation Details
- Created `core/src/main/kotlin/com/dbeagle/ddl/SequenceDDLBuilder.kt`
- Implemented as singleton object (Kotlin `object`)
- Contains `SequenceChanges` data class with nullable fields: increment, minValue, maxValue, restart

### Key Features
- `buildCreateSequence()`: Generates CREATE SEQUENCE DDL with START WITH, INCREMENT BY, MINVALUE, MAXVALUE, CYCLE/NO CYCLE
- `buildAlterSequence()`: Generates ALTER SEQUENCE DDL with optional INCREMENT BY, MINVALUE, MAXVALUE, RESTART WITH clauses
- `buildDropSequence()`: Generates DROP SEQUENCE DDL with optional IF EXISTS clause
- Uses `dialect.quoteIdentifier()` for all identifiers
- Uses `dialect.supportsIfExists()` for conditional IF EXISTS clause

### PostgreSQL DDL Syntax
- CREATE: `CREATE SEQUENCE "name" START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 [CYCLE | NO CYCLE]`
- ALTER: `ALTER SEQUENCE "name" [INCREMENT BY x] [MINVALUE x] [MAXVALUE x] [RESTART WITH x]`
- DROP: `DROP SEQUENCE [IF EXISTS] "name"`

### Design Decisions
- SequenceChanges uses nullable fields to allow partial alterations
- ALTER statement only includes clauses for non-null changes
- DROP defaults to IF EXISTS for idempotent operations
- All identifiers properly quoted using dialect method

### Verification
- `:core:compileKotlin` passes successfully
- No compilation errors
- File created at correct location

## TableDDLBuilder Implementation
**Date:** 2026-03-05

### File Structure
- Created `core/src/main/kotlin/com/dbeagle/ddl/TableDDLBuilder.kt`
- Implemented as singleton object (Kotlin `object` pattern)
- All data classes marked `@Serializable` for cross-module serialization

### Data Classes
- `ColumnDefinition`: name, type (ColumnType), nullable (default true), defaultValue (String?)
- `ForeignKeyDefinition`: name (String?), columns (List<String>), refTable, refColumns (List<String>), onDelete (String?), onUpdate (String?)
- `ConstraintDefinition` sealed class: PrimaryKey(columns), ForeignKey(def), Unique(name, columns)
- `TableDefinition`: name, columns, primaryKey (List<String>?), foreignKeys, uniqueConstraints

### DDL Builder Methods
1. **buildCreateTable**: Generates complete CREATE TABLE with columns, PK, UNIQUE, FK constraints
2. **buildAlterTableAddColumn**: Generates ALTER TABLE ADD COLUMN with type, nullable, default
3. **buildAlterTableDropColumn**: Generates ALTER TABLE DROP COLUMN (throws if unsupported)
4. **buildAlterTableAddConstraint**: Generates ALTER TABLE ADD CONSTRAINT for PK/FK/UNIQUE
5. **buildAlterTableDropConstraint**: Generates ALTER TABLE DROP CONSTRAINT
6. **buildDropTable**: Generates DROP TABLE with optional IF EXISTS and CASCADE

### Key Design Decisions
- **Identifier quoting**: ALL identifiers (tables, columns, constraints) quoted via `dialect.quoteIdentifier()`
- **Type mapping**: Used `dialect.getTypeName()` for all column types
- **Feature checks**: `buildAlterTableDropColumn` validates `dialect.supportsDropColumn()` before generating SQL
- **IF EXISTS**: `buildDropTable` conditionally adds IF EXISTS based on `dialect.supportsIfExists()`
- **buildString {}**: Used throughout for clean, efficient string concatenation

### SQL Generation Logic
- **CREATE TABLE**: Columns first, then PK, then UNIQUE constraints, then FK constraints
- **ALTER ADD COLUMN**: Column name, type, NOT NULL (if applicable), DEFAULT (if present)
- **ALTER DROP COLUMN**: Simple DROP COLUMN syntax (with feature check)
- **ALTER ADD CONSTRAINT**: Handles PK, FK (with ON DELETE/UPDATE), and UNIQUE with optional constraint names
- **DROP TABLE**: IF EXISTS (conditional), table name, CASCADE (conditional)

### Foreign Key Handling
- FK names are optional (constraint name may be omitted)
- Supports ON DELETE and ON UPDATE actions (optional)
- Multi-column FKs supported via List<String> for columns and refColumns

### Constraint Handling
- Sealed class provides type-safe constraint representation
- Primary keys can be added inline (CREATE TABLE) or via ALTER TABLE
- Unique constraints support optional naming
- All constraint identifiers properly quoted

### Verification
- File compiles successfully: `./gradlew :core:compileKotlin` passes
- No compilation errors or warnings
- All methods generate syntactically valid SQL using dialect capabilities
- UnsupportedOperationException thrown for unsupported operations (e.g., DROP COLUMN on SQLite)

## IndexDDLBuilder Implementation
**Date:** 2026-03-05

### Implementation Details
- Created `core/src/main/kotlin/com/dbeagle/ddl/IndexDDLBuilder.kt`
- Implemented as singleton object (Kotlin `object`)
- Contains `IndexDefinition` data class and DDL generation methods

### IndexDefinition Data Class
- `@Serializable` data class for index metadata
- Fields: name, tableName, columns (List<String>), unique (Boolean = false)
- Simplified from IndexMetadata (no type field - B-tree only)

### DDL Generation Methods
1. **buildCreateIndex(dialect, index)**
   - Generates: `CREATE [UNIQUE] INDEX "idx_name" ON "table" ("col1", "col2")`
   - Uses `dialect.quoteIdentifier()` for all identifiers (index name, table name, columns)
   - Handles composite indexes via columns.joinToString()
   - UNIQUE keyword conditional on index.unique flag

2. **buildDropIndex(dialect, indexName, tableName?, ifExists)**
   - Generates: `DROP INDEX [IF EXISTS] "idx_name"`
   - tableName parameter present but unused (PostgreSQL/SQLite don't need it)
   - IF EXISTS conditional on dialect.supportsIfExists() capability
   - Default ifExists = true for idempotent operations

### Key Design Decisions
- Both PostgreSQL and SQLite use standard DROP INDEX syntax (no table name in statement)
- tableName parameter kept for API consistency and potential future dialects (MySQL)
- Used `buildString {}` for clean, efficient string construction
- All identifiers quoted to handle reserved words and special characters
- Composite indexes supported naturally via List<String> columns

### Verification
- `./gradlew :core:compileKotlin` passed successfully
- File created at correct location
- Follows established DDL builder patterns

## DDLValidator Implementation
**Date:** 2026-03-05

### Implementation Details
- Created `core/src/main/kotlin/com/dbeagle/ddl/DDLValidator.kt`
- Implemented as singleton object (Kotlin `object`)
- Contains `ValidationResult` sealed class with `Valid` object and `Invalid(errors: List<String>)` data class

### ValidationResult Design
- `@Serializable` sealed class for type-safe validation results
- `Valid`: object (singleton) for successful validations
- `Invalid`: data class containing list of error messages for detailed feedback

### Validation Methods
1. **validateIdentifier(name: String)**
   - Checks: not empty/blank, max 128 chars, no SQL injection patterns, valid identifier chars
   - SQL injection patterns rejected: `;`, `--`, `/*`, `*/`, `DROP`, `DELETE`, `INSERT`, `UPDATE`, `SELECT`, `TRUNCATE`, `EXEC`, `EXECUTE`
   - Pattern: `^[A-Za-z_$][A-Za-z0-9_$]*$` (PostgreSQL-compatible: letters, digits, underscore, dollar sign)
   - Returns ValidationResult with specific error messages for each violation

2. **validateTableDefinition(table: TableDefinition)**
   - Validates table name via validateIdentifier
   - Checks at least one column exists
   - Detects duplicate column names (case-sensitive)
   - Validates each column definition
   - Returns consolidated error list if any validation fails

3. **validateColumnDefinition(column: ColumnDefinition)**
   - Validates column name via validateIdentifier
   - Type validation not needed (enforced by ColumnType sealed class)
   - Returns validation result for name only

### Key Design Decisions
- **Max identifier length**: 128 chars (PostgreSQL limit is 63, SQLite is 1000+, chose 128 for safety)
- **Identifier regex**: Includes `$` for PostgreSQL compatibility (common in framework-generated names)
- **SQL injection**: Case-insensitive pattern matching on uppercase names
- **Error messages**: Descriptive and specific, includes problematic identifier in message
- **Validation composition**: validateTableDefinition calls validateIdentifier and validateColumnDefinition
- **No database queries**: Pure structural/syntax validation, no DB access

### Security Considerations
- SQL injection patterns detected before any query construction
- Dangerous keywords rejected even if properly quoted
- Case-insensitive pattern matching prevents bypass via mixed case
- Comprehensive pattern list covers common SQL injection vectors

### Error Reporting
- Multiple errors collected and returned together (not fail-fast)
- Hierarchical errors: table-level errors include indented column-level errors
- Example: "Invalid table name:" followed by "  - Identifier contains invalid characters..."

### Verification
- `:core:compileKotlin` passed successfully
- File created at correct location
- Follows established DDL builder patterns (object singleton, buildString, @Serializable)
- No compilation errors or warnings

## DDLErrorMapper Implementation
**Date:** 2026-03-05

### Implementation Details
- Created `core/src/main/kotlin/com/dbeagle/ddl/DDLErrorMapper.kt`
- Implemented as singleton object (Kotlin `object`)
- Contains `UserFriendlyError` data class and error mapping method

### UserFriendlyError Data Class
- `@Serializable` data class for cross-module serialization
- Fields: title, description, suggestion (all String)
- Represents user-facing error messages for DDL failures

### DDL Error Mapping Method
- `mapError(sqlState: String?, message: String): UserFriendlyError`
- Uses `when` expression for clean SQLSTATE code matching
- Maps PostgreSQL SQLSTATE codes to friendly messages
- Fallback: Returns original message for unknown/SQLite errors

### Mapped PostgreSQL SQLSTATE Codes
- **42P07**: Duplicate Table - table already exists
- **42P01**: Table Not Found - specified table doesn't exist
- **23503**: Foreign Key Error - referenced table/column missing
- **42703**: Column Not Found - column doesn't exist
- **23505**: Duplicate Value - unique constraint violation
- **42601**: SQL Syntax Error - generated SQL has syntax error
- **else**: Database Error - returns original message with logs suggestion

### Key Design Decisions
- SQLSTATE codes are PostgreSQL-specific (5 characters)
- SQLite errors handled by fallback (message-based, no standard SQLSTATE)
- No exception throwing - always returns UserFriendlyError
- No logging - UI layer responsibility
- Original message preserved in fallback for debugging

### Verification
- File created at correct location
- `./gradlew :core:compileKotlin` passes successfully
- No compilation errors
- Follows singleton object pattern like other DDL utilities
