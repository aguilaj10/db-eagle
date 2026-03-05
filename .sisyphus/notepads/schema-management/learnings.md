
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

## DDLPreviewDialog Implementation
**Date:** 2026-03-05

### Implementation Details
- Created `app/src/main/kotlin/com/dbeagle/ui/dialogs/DDLPreviewDialog.kt`
- Composable dialog for reviewing and executing DDL SQL statements
- Follows AlertDialog pattern from ConnectionDialog and ExportDialog

### Key Features
1. **DDL Display**
   - SelectionContainer allows text selection/copy
   - FontFamily.Monospace for SQL formatting
   - Background color (surfaceVariant) for visual distinction
   - Scrollable text area via verticalScroll(rememberScrollState())

2. **Warning Banner**
   - Conditional display when isDestructive = true
   - Red error container background with warning icon
   - Message: "This operation is destructive and cannot be undone!"
   - Uses MaterialTheme.colorScheme.errorContainer/onErrorContainer

3. **Action Buttons**
   - Execute: Calls onExecute() then onDismiss()
   - Copy: Uses LocalClipboardManager.current.setText(AnnotatedString(ddlSql))
   - Cancel: Calls onDismiss()

### Dialog Signature
```kotlin
@Composable
fun DDLPreviewDialog(
    ddlSql: String,
    isDestructive: Boolean,
    onDismiss: () -> Unit,
    onExecute: () -> Unit,
)
```

### Design Decisions
- Title changes based on isDestructive flag: "Confirm DDL Execution" vs "Review DDL"
- Copy button in confirmButton section alongside Execute (row layout)
- No inline destructive detection - caller determines isDestructive flag
- SelectionContainer allows user to manually select/copy portions of SQL
- Background color on SQL text improves readability

### Verification
- `./gradlew :app:compileKotlin` passes successfully
- One deprecation warning: LocalClipboardManager (use LocalClipboard for suspend functions)
- Warning non-blocking - dialog functional with current API

### Integration Notes
- Caller must detect destructive operations (e.g., check for DROP keyword)
- onExecute callback should handle actual SQL execution
- Dialog does not execute DDL directly - follows separation of concerns

## Task 20: Add Sequences Section to SchemaTree

### Implementation Details
- Added new `SchemaTreeNode.Sequence(id, label, increment)` node type with `Icons.Default.Numbers` icon
- Sequences display format: `sequence_name (increment: N)`
- Added conditional rendering in `buildTree()` that checks `DatabaseCapability.Sequences`
- Sequences section appears ONLY when driver supports them (PostgreSQL yes, SQLite no)
- Section ordering: Tables → Views → Indexes → Sequences

### Key Technical Points
- Used `activeDriver?.getCapabilities()?.contains(DatabaseCapability.Sequences)` for capability check
- SchemaMetadata already had `sequences: List<SequenceMetadata>` field ready to use
- SequenceMetadata contains: name, schema, startValue, increment, minValue, maxValue, cycle, ownedByTable, ownedByColumn
- Added import for `DatabaseCapability` in SchemaBrowserScreen
- Sequences are sorted alphabetically by name

### File Changes
1. `SchemaTree.kt`:
   - Added `Icons.Default.Numbers` import
   - Added `SchemaTreeNode.Sequence` class with increment field
   - Added rendering logic to display increment value alongside sequence name

2. `SchemaBrowserScreen.kt`:
   - Added `DatabaseCapability` import
   - Modified `buildTree()` to conditionally add Sequences section based on capability check
   - Used mutableListOf to dynamically build sections list

### Verification
- Compilation successful: `./gradlew :app:compileKotlin` passed
- No new warnings introduced

## TableEditorDialog Implementation
**Date:** 2026-03-05

### File Structure
- Created `app/src/main/kotlin/com/dbeagle/ui/dialogs/TableEditorDialog.kt`
- Three composable functions: TableEditorDialog (main), ColumnsTab, ColumnRow
- Uses Material3 AlertDialog with fixed size: 800dp width, 600dp height

### Tab Structure
- TabRow with 3 tabs: Columns (implemented), Constraints (placeholder), Indexes (placeholder)
- Uses `mutableIntStateOf(0)` for selected tab tracking
- `when (selectedTab)` pattern for tab content rendering

### State Management
- Table name: `mutableStateOf(existingTable?.name ?: "")`
- Columns: `mutableStateListOf<ColumnDefinition>()` for dynamic add/remove
- Validation error: `mutableStateOf<String?>` for inline validation feedback
- Edit mode detection: `existingTable != null`

### Columns Tab Features
- Table name field: read-only in edit mode, editable in create mode
- LazyColumn for scrollable column list
- Each column row has: name field, type dropdown, nullable checkbox, default value field, delete button
- "Add Column" button adds empty ColumnDefinition with defaults (TEXT, nullable=true)
- Validation error display in red below table name field

### Column Row Implementation
- Uses `remember { mutableStateOf(...) }` for each field to enable inline editing
- ExposedDropdownMenuBox for ColumnType selection
- `ColumnType.entries.forEach` to populate all enum values in dropdown
- `notifyUpdate()` local function to propagate changes to parent on every field change
- Row weight distribution: name (2f), type (1.5f), nullable (1f), default (1.5f), delete button

### Validation Integration
- Uses DDLValidator.validateTableDefinition on Save button click
- ValidationResult.Invalid shows concatenated errors in UI
- ValidationResult.Valid proceeds with onSave callback
- Validation runs synchronously in onClick handler

### Dialog Actions
- Save button: validates then calls `onSave(TableDefinition)`
- Cancel button: calls `onDismiss()`
- No "Apply" button (single-shot save)

### Type Safety
- ColumnType enum exhaustive in dropdown (uses .entries)
- SnapshotStateList for reactive column list
- Nullable default value handling: `defaultValue.takeIf { it.isNotBlank() }`

### Edit vs Create Mode
- Edit mode: table name read-only, dialog title shows table name
- Create mode: table name editable, dialog title "Create Table"
- Both modes: columns fully editable (add/remove/reorder)

### Verification
- `:app:compileKotlin` passed successfully
- No compilation errors
- Deprecation warning on TabRow (acceptable - framework migration pending)

### Design Patterns
- Followed ConnectionDialog pattern: AlertDialog, ExposedDropdownMenuBox
- State hoisting: parent manages columns list, children notify via callbacks
- Immediate validation feedback via validationError state
- No ViewModel - direct state management in composable (suitable for dialog scope)

## Foreign Key Display in SchemaTree Implementation
**Date:** 2026-03-05

### Implementation Details
- Extended `SchemaTreeNode.Column` with optional `foreignKeyTarget: String?` parameter
- Format: "targetTable.targetColumn" (e.g., "users.id")
- Added FK display with link icon (Icons.Default.Link) and "→ target" text
- Used `TooltipArea` (Compose Desktop) for hover tooltip showing full FK info

### UI Components
- **Icon**: `Icons.Default.Link` in secondary color (14dp size)
- **Text**: "→ targetTable.targetColumn" in secondary color
- **Tooltip**: Surface with shadow showing "Foreign key → targetTable.targetColumn"
- **Placement**: Appears after column type in tree row
- **Delay**: 300ms hover delay before tooltip shows

### Data Flow
1. `SchemaMetadata.foreignKeys` stored in `SessionViewModel.SchemaUiState.schemaMetadata`
2. When table expanded, FK lookup map built: `foreignKeys.filter { it.fromTable == tableName }.associateBy { it.fromColumn }`
3. For each column, check if FK exists and populate `foreignKeyTarget` parameter
4. SchemaTree renders FK indicator if `foreignKeyTarget != null`

### Key Design Decisions
- FK target format: "table.column" (not schema-qualified to reduce noise)
- Used `TooltipArea` from `androidx.compose.foundation` (Compose Desktop specific)
- FK data cached in `columnsCache` so re-expanding table doesn't lose FK info
- Secondary color scheme used to visually distinguish FK from regular columns
- Link icon + arrow text provides both icon and textual representation

### Imports Added
- `androidx.compose.foundation.TooltipArea`
- `androidx.compose.foundation.TooltipPlacement`
- `androidx.compose.foundation.shape.RoundedCornerShape`
- `androidx.compose.material.icons.filled.Link`
- `androidx.compose.material3.Surface`
- `androidx.compose.ui.draw.shadow`
- `androidx.compose.ui.unit.DpOffset`

### SessionViewModel Changes
- Added `schemaMetadata: SchemaMetadata?` field to `SchemaUiState`
- Stored full schema on load to enable FK lookup during column expansion
- No performance concerns: SchemaMetadata already loaded once, just retained

### Verification
- `:app:compileKotlin` passes successfully
- All imports resolved correctly
- No compilation errors or warnings related to FK changes
- Compose Desktop `TooltipArea` available (not Material3 `TooltipBox`)

### Notes
- This implementation only displays FK info, not editing (Task 23)
- FKs already loaded by drivers, no additional queries needed
- Works for both PostgreSQL and SQLite (both populate foreignKeys)

## SequenceEditorDialog Implementation (Task 26)

### Key Design Patterns
- **Dual-mode dialog**: Supports CREATE (null input) and EDIT (pre-filled) modes
- **Read-only for owned sequences**: When `ownedByTable != null`, all fields disabled with explanatory text
- **Real-time validation**: Uses `DDLValidator.validateIdentifier()` with immediate feedback
- **Smart nullability**: `isOwnedSequence` check guarantees `existingSequence` is non-null in that branch

### Form Fields Structure
- Name: Required, validated with DDLValidator
- Start Value: Default 1, enabled only for non-owned sequences
- Increment: Default 1, enabled only for non-owned sequences
- Min Value: Optional, nullable Long
- Max Value: Optional, nullable Long
- Cycle: Boolean checkbox, default false

### Validation Approach
```kotlin
val nameValidation = if (name.isNotBlank()) {
    DDLValidator.validateIdentifier(name)
} else {
    ValidationResult.Invalid(listOf("Name is required"))
}
val isNameValid = nameValidation is ValidationResult.Valid
```

### Smart Contracts
- Dialog title dynamically reflects mode: "Create" / "Edit" / "View (Owned by table.column)"
- Save button enabled only when name validation passes
- onSave emits complete SequenceMetadata (dialog doesn't execute DDL)
- Schema defaults to "public" for new sequences, preserved from existing for edits

### Compose Best Practices
- Use `remember { mutableStateOf() }` for form state
- Row layout for related fields (Start/Increment, Min/Max)
- `supportingText` for validation errors (first error shown)
- `isError` visual feedback when validation fails
- Conditional rendering: Hide Save button for owned sequences

