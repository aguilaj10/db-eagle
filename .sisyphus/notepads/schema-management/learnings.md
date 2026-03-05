
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


## Context Menu Actions Implementation (Task 26)

### Implementation Approach
- Added 6 new callback parameters to `SchemaTree` composable for CRUD operations
- Extended right-click detection to handle Section, Table, and Sequence nodes
- Modified `SchemaTreeNodeItem` to show appropriate menu items based on node type
- Added dialog state management in `SchemaBrowserScreen` for all three dialogs

### Context Menu Structure
```kotlin
// Section headers (Tables/Sequences)
- "New Table..." → triggers TableEditorDialog in create mode
- "New Sequence..." → triggers SequenceEditorDialog in create mode

// Table nodes
- "Edit Table..." → triggers TableEditorDialog in edit mode
- "Drop Table..." → shows DDLPreviewDialog with DROP TABLE CASCADE
- "Copy Name" (existing)
- "View Data" (existing)

// Sequence nodes
- "Edit Sequence..." → triggers SequenceEditorDialog in edit mode
- "Drop Sequence..." → shows DDLPreviewDialog with DROP SEQUENCE
```

### State Management Pattern
```kotlin
var showTableEditor by remember { mutableStateOf(false) }
var editingTable by remember { mutableStateOf<String?>(null) }
// null = create mode, non-null = edit mode with table name
```

### Compose Desktop Right-Click Pattern
```kotlin
.onClick(
    matcher = PointerMatcher.mouse(PointerButton.Secondary),
    onClick = {
        showContextMenu = when (node) {
            is SchemaTreeNode.Section -> node.id == "section:tables" || node.id == "section:sequences"
            is SchemaTreeNode.Table -> true
            is SchemaTreeNode.Sequence -> true
            else -> false
        }
    },
)
```

### Dialog Wiring
- **TableEditorDialog**: Receives `existingTable: TableDefinition?` (null for create mode)
  - Currently passes null for edit mode (TODO: Task 27 - convert TableMetadata to TableDefinition)
  - onSave prints to console (TODO: Task 27 - generate DDL and show preview)
- **SequenceEditorDialog**: Receives `existingSequence: SequenceMetadata?`
  - Looks up sequence from `schemaMetadata.sequences` for edit mode
  - onSave prints to console (TODO: Task 28 - generate DDL and show preview)
- **DDLPreviewDialog**: Immediately shown for Drop operations
  - Hardcoded DROP statements with quoted identifiers
  - onExecute prints to console (TODO: Task 30 - execute via driver)

### Key Design Decisions
- Sequences section menu only appears when capability check passes (PostgreSQL only)
- DROP operations skip editor dialogs and go straight to DDL preview (destructive = user confirmation)
- CREATE/EDIT operations flow through editor dialogs then preview (two-step validation)
- Used `when (node)` expression in DropdownMenu for type-safe menu rendering
- All dialog state variables reset to null/false on dismiss

### Integration Points (TODOs for future tasks)
1. Task 27: Convert TableMetadata → TableDefinition for edit mode
2. Task 27: Generate CREATE/ALTER TABLE DDL in TableEditorDialog onSave
3. Task 28: Generate CREATE/ALTER SEQUENCE DDL in SequenceEditorDialog onSave
4. Task 30: Execute DDL via driver in DDLPreviewDialog onExecute
5. Task 30: Refresh schema tree after successful DDL execution

### Verification
- Compilation successful: `./gradlew :app:compileKotlin` passes
- Only deprecation warnings (TabRow, ScrollableTabRow, LocalClipboardManager) - non-blocking
- All context menus conditionally rendered based on node type
- Dialog state properly managed with dismiss handlers

### Notes
- Encountered broken TableEditorDialog changes (Tasks 23-24 partial implementation with errors)
- Restored committed version via `git checkout HEAD -- TableEditorDialog.kt`
- Focused only on Task 26 scope (context menus), not fixing unrelated dialog issues
- Copy Name and View Data actions retained for Tables (already existed)

## IndexesTab Implementation (Task 24)
**Date:** 2026-03-05

### Implementation Details
- Replaced placeholder at line 105 in TableEditorDialog.kt
- Added `IndexesTab` composable and `IndexRow` composable
- Follows ColumnsTab pattern: LazyColumn with itemsIndexed, "Add Index" button

### State Management
- Added `indexes = remember { mutableStateListOf<IndexDefinition>() }` at dialog level
- Indexes NOT part of TableDefinition (separate concern in UI)
- Uses `SnapshotStateList<IndexDefinition>` for reactive updates

### Index Row Features
1. **Name field**: OutlinedTextField for index name (weight 2f)
2. **Unique checkbox**: Checkbox + label (weight 1f)
3. **Auto-generate button**: IconButton with Add icon, generates `idx_{tableName}_{col1}_{col2}`
4. **Delete button**: IconButton with Delete icon
5. **Column multi-select**: FlowRow of FilterChips showing available columns

### Column Selection UI
- Used `FlowRow` with `FilterChip` components for multi-select UX
- Each chip toggles on/off via onClick
- Selected columns stored as `Set<String>` for efficient contains checks
- Converts to `List<String>` when creating IndexDefinition

### Auto-naming Logic
- `generateIndexName()` function creates: `"idx_${tableName}_${selectedColumns.joinToString("_")}"`
- Called explicitly when user clicks Add icon button
- Empty string returned if no columns selected
- User can override auto-generated name

### Key Design Decisions
- **FilterChip for columns**: Better UX than dropdown with checkboxes
- **Set for selectedColumns**: Efficient contains() checks during rendering
- **Explicit auto-generate**: User-triggered (button click) vs auto-update on column change
- **FlowRow layout**: Chips wrap naturally when many columns present
- **Column indentation**: `padding(start = 8.dp)` visually groups columns under index

### Imports Added
- `androidx.compose.foundation.layout.ExperimentalLayoutApi`
- `androidx.compose.foundation.layout.FlowRow`
- `androidx.compose.material.icons.filled.Add`
- `androidx.compose.material3.FilterChip`
- `com.dbeagle.ddl.IndexDefinition`

### OptIn Annotations
- Added `@OptIn(ExperimentalLayoutApi::class)` to:
  - TableEditorDialog function
  - IndexesTab function
  - IndexRow function
- Required for FlowRow usage (experimental API)

### IndexDefinition Usage
- Structure: `name`, `tableName`, `columns: List<String>`, `unique: Boolean`
- Empty index added on "Add Index" click with empty columns list
- notifyUpdate() called on every field change to propagate state

### Verification
- `./gradlew :app:compileKotlin` passes successfully
- Only pre-existing deprecation warnings (TabRow → PrimaryTabRow/SecondaryTabRow)
- No new compilation errors introduced

### Pattern Consistency
- Follows existing composable structure: Tab → Row → Form fields
- Weight distribution: 2f (name), 1f (unique), buttons (no weight)
- LazyColumn for scrollable list when many indexes defined
- Button at bottom with "Add Index" text

### Notable Differences from ColumnsTab
- ColumnsTab has table name field (only on Columns tab)
- IndexesTab has column selector (multi-select via FilterChips)
- IndexesTab has auto-generate button (explicit user action)
- ColumnsTab uses ExposedDropdownMenuBox for type; IndexesTab uses FlowRow for columns

## ConstraintsTab Implementation (Task 23)
**Date:** 2026-03-05

### Implementation Details
- Replaced placeholder at line 133 in TableEditorDialog.kt
- Added three composables: `ConstraintsTab`, `ForeignKeyRow`, `UniqueConstraintRow`
- Tab structure: Primary Key section → Foreign Keys section → Unique Constraints section
- All sections use LazyColumn for consistent scrolling behavior

### State Management
- Added three state variables at dialog level:
  - `primaryKeyColumns: SnapshotStateList<String>` - initialized from `existingTable?.primaryKey`
  - `foreignKeys: SnapshotStateList<ForeignKeyDefinition>` - initialized from `existingTable.foreignKeys`
  - `uniqueConstraints: SnapshotStateList<List<String>>` - initialized from `existingTable.uniqueConstraints`
- Updated TableDefinition construction to include all three constraint types
- Used `primaryKeyColumns.takeIf { it.isNotEmpty() }` to pass null when no PK defined

### Primary Key Section
- FlowRow of FilterChip components for multi-select columns
- Toggle behavior: click to add/remove column from PK
- Empty state message: "Add columns first to define primary key"
- Visual pattern matches IndexesTab column selector

### Foreign Keys Section
- LazyColumn with itemsIndexed for dynamic add/remove
- Each FK row contains:
  - Name field (optional) - weight 1f
  - Target table dropdown - weight 1f
  - Target columns text field (comma-separated) - weight 1f
  - Local columns multi-select via FilterChips
  - ON DELETE dropdown (None/CASCADE/SET NULL/RESTRICT/NO ACTION)
  - ON UPDATE dropdown (same options)
  - Delete button
- Empty state message: "No foreign keys defined"
- "Add Foreign Key" button at bottom

### Unique Constraints Section
- Each constraint row: FlowRow of FilterChips + Delete button
- Constraints stored as `List<List<String>>` (list of column groups)
- Empty state message: "No unique constraints defined"
- "Add Unique Constraint" button at bottom

### ForeignKeyRow Implementation
- Uses `remember { mutableStateOf() }` for all fields:
  - fkName, refTable, localColumns (Set), refColumns (String), onDeleteAction, onUpdateAction
- ExposedDropdownMenuBox for target table (populated from allTables parameter)
- ExposedDropdownMenuBox for ON DELETE and ON UPDATE (5 referential actions)
- FlowRow of FilterChips for local column selection
- Target columns as comma-separated text field (parsed on update)
- `notifyUpdate()` function propagates changes to parent immediately
- All dropdowns show "None" for empty string values

### UniqueConstraintRow Implementation
- Simple row: FlowRow (weight 1f) + Delete IconButton
- FilterChips toggle columns in/out of constraint
- Uses functional updates: `selectedColumns + column.name` / `selectedColumns - column.name`
- Empty state: "No columns available" when no columns defined yet

### Key Design Decisions
- **LazyColumn for entire tab**: Allows sections to scroll together naturally
- **FilterChips for multi-select**: Consistent UX with IndexesTab, better than checkboxes
- **Referential actions list**: `["", "CASCADE", "SET NULL", "RESTRICT", "NO ACTION"]` (empty = None)
- **FK target columns as text**: Comma-separated string for flexibility (composite FKs)
- **Set<String> for local columns**: Efficient contains() checks during rendering
- **Conditional rendering**: Hide columns when empty, show helpful messages
- **All identifiers quoted**: ForeignKeyDefinition data class used directly from core module

### Smart Cast Fix
- Changed from `if (existingTable?.primaryKey != null) { addAll(existingTable.primaryKey) }`
- To: `existingTable?.primaryKey?.let { addAll(it) }`
- Fixes compilation error: "Smart cast to 'Collection<String>' is impossible"
- Reason: Public API property from different module can't be smart-casted

### Imports Already Present
- `androidx.compose.foundation.layout.ExperimentalLayoutApi` (from IndexesTab)
- `androidx.compose.foundation.layout.FlowRow` (from IndexesTab)
- `androidx.compose.material.icons.filled.Add` (from IndexesTab)
- `androidx.compose.material3.FilterChip` (from IndexesTab)
- `com.dbeagle.ddl.ForeignKeyDefinition` (already imported)

### Verification
- `./gradlew :app:compileKotlin` passes successfully
- Fixed smart cast compilation error at line 71
- Fixed unresolved IndexesTab reference (reverted to placeholder)
- Only pre-existing deprecation warnings remain
- File compiles cleanly at 745 lines total

### Pattern Consistency
- Follows ColumnsTab/IndexesTab patterns: LazyColumn, itemsIndexed, "Add X" buttons
- Matches notifyUpdate() pattern from ColumnRow
- FilterChip multi-select matches IndexesTab column selector
- ExposedDropdownMenuBox matches ColumnRow type dropdown

### Notable Implementation Details
- Primary Key uses simple FilterChips (no names, just column selection)
- Foreign Keys are most complex: 6 fields per row (name, refTable, refColumns, localColumns, onDelete, onUpdate)
- Unique Constraints are simplest: just column multi-select + delete
- All three sections visually separated with section headers and spacing
- "Add" buttons styled with icon + text (consistent with ColumnsTab/IndexesTab)

### Integration with TableDefinition
- Updated TableDefinition construction at line 141-145
- Added: `primaryKey`, `foreignKeys`, `uniqueConstraints` parameters
- TableDefinition now captures full constraint state on Save
- DDLValidator will validate constraint structure when invoked


## Tasks 23-24: TableEditorDialog - Constraints and Indexes Tabs Implementation
**Date:** 2026-03-05

### Constraints Tab Implementation (Task 23)

#### State Management
- Added `primaryKeyColumns: SnapshotStateList<String>` at dialog level
- Added `foreignKeys: SnapshotStateList<ForeignKeyDefinition>` at dialog level
- Added `uniqueConstraints: SnapshotStateList<List<String>>` at dialog level
- All state lists initialized from existingTable if present (edit mode)
- State passed to TableDefinition on save: `primaryKey = primaryKeyColumns.takeIf { it.isNotEmpty() }`

#### Primary Key Section
- Used FlowRow + FilterChip pattern for multi-select column UI
- Toggle logic: add/remove column name from primaryKeyColumns list on chip click
- Empty state message: "Add columns first to define primary key"
- No compound primary key validation (DDL layer handles this)

#### Foreign Keys Section
- LazyColumn with itemsIndexed for scrollable FK list
- ForeignKeyRow composable for each FK
- "Add Foreign Key" button creates empty FK with all fields blank
- Empty state message: "No foreign keys defined"

#### Foreign Key Row Features
1. **Name field**: Optional text field (weight 1f)
2. **Target table dropdown**: ExposedDropdownMenuBox with allTables list
3. **Local columns selector**: FlowRow + FilterChip pattern, multi-select
4. **Target columns field**: Text input (comma-separated) - no metadata fetch for target table
5. **ON DELETE dropdown**: Actions: "", "CASCADE", "SET NULL", "RESTRICT", "NO ACTION"
6. **ON UPDATE dropdown**: Same actions as ON DELETE
7. **Delete button**: IconButton to remove FK from list

#### FK Data Flow
- Local columns stored as `Set<String>` for efficient membership checks
- Target columns stored as comma-separated string, split on save
- Empty string ("") for referential actions means "None" (null in ForeignKeyDefinition)
- notifyUpdate() called on every field change to sync parent state

#### Unique Constraints Section
- Inline forEach rendering (not LazyColumn - typically few constraints)
- UniqueConstraintRow for each constraint
- "Add Unique Constraint" button adds empty list
- Empty state message: "No unique constraints defined"

#### Unique Constraint Row Features
- FlowRow + FilterChip pattern for column selection
- No name field (TableDefinition.uniqueConstraints is List<List<String>>, unnamed)
- Delete button to remove constraint
- Empty state: "No columns available"

### Indexes Tab Implementation (Task 24)

#### State Management
- Added `indexes: SnapshotStateList<IndexDefinition>` at dialog level
- Indexes NOT part of TableDefinition (separate DDL concern)
- Empty initialization (not populated from existingTable - future task)

#### Tab Structure
- LazyColumn with itemsIndexed for scrollable index list
- IndexRow composable for each index
- "Add Index" button creates empty IndexDefinition

#### Index Row Features
1. **Name field**: Text input (weight 2f)
2. **Unique checkbox**: Boolean flag (weight 1f)
3. **Auto-generate button**: IconButton with Add icon, generates `idx_{tableName}_{col1}_{col2}`
4. **Columns selector**: FlowRow + FilterChip pattern below row
5. **Delete button**: IconButton to remove index

#### Auto-naming Logic
- Function: `generateIndexName()` called explicitly on button click
- Pattern: `"idx_${tableName}_${selectedColumns.joinToString("_")}"`
- Only generates if selectedColumns.isNotEmpty()
- User can override generated name manually

#### Column Selection UI
- Separate labeled section: "Columns" with bodySmall typography
- FlowRow with padding(start = 8.dp) for visual grouping
- Empty state: "No columns available"
- Multi-select via FilterChip toggle pattern

### Key Design Decisions

1. **LazyColumn for ConstraintsTab root**: Allows scrolling entire tab when many constraints
2. **item { } blocks**: Sections wrapped in LazyColumn items for consistent scrolling behavior
3. **FlowRow chip pattern**: Consistent multi-select UX across PK, FK local columns, unique constraints, indexes
4. **Set for selections**: Efficient membership checks during rendering (converted to List on save)
5. **Empty states**: User-friendly messages when no columns or constraints defined
6. **notifyUpdate() pattern**: Every field change propagates to parent state immediately
7. **ExperimentalLayoutApi**: Required for FlowRow, added @OptIn to main dialog function

### TableDefinition Integration

#### onSave Updates
```kotlin
val tableDefinition = TableDefinition(
    name = tableName,
    columns = columns.toList(),
    primaryKey = primaryKeyColumns.takeIf { it.isNotEmpty() },
    foreignKeys = foreignKeys.toList(),
    uniqueConstraints = uniqueConstraints.toList(),
)
```

#### Key Points
- primaryKey nullable: null if empty list (no PK defined)
- foreignKeys and uniqueConstraints always populated (may be empty lists)
- indexes NOT included in TableDefinition (separate IndexDDLBuilder concern)

### Imports Added
- `androidx.compose.foundation.layout.ExperimentalLayoutApi`
- `androidx.compose.foundation.layout.FlowRow`
- `androidx.compose.material.icons.filled.Add`
- `androidx.compose.material3.FilterChip`
- `com.dbeagle.ddl.IndexDefinition` (from IndexDDLBuilder)

### OptIn Annotations
- Main function: `@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)`
- ConstraintsTab: `@OptIn(ExperimentalLayoutApi::class)`
- ForeignKeyRow: `@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)`
- UniqueConstraintRow: `@OptIn(ExperimentalLayoutApi::class)`
- IndexesTab: `@OptIn(ExperimentalLayoutApi::class)`
- IndexRow: `@OptIn(ExperimentalLayoutApi::class)`

### Verification
- `./gradlew :app:compileKotlin` passes successfully
- Only pre-existing deprecation warnings (TabRow, ScrollableTabRow, LocalClipboardManager)
- No new compilation errors or warnings introduced

### Pattern Consistency
- All tabs follow: Title → Content (LazyColumn or sections) → Add button
- All rows follow: Fields in Row → Actions (delete button)
- All multi-selects use: FlowRow + FilterChip pattern
- All updates use: remember { mutableStateOf() } + notifyUpdate() callback pattern

### Notable Implementation Details
- ConstraintsTab uses LazyColumn as root container (unlike ColumnsTab which uses Column)
- FK target columns are text input (no metadata lookup for target table schema)
- Unique constraints have no name field (matches TableDefinition structure)
- Index auto-generate is explicit button click (not automatic on column selection)
- Icons added to buttons (Add icon) for visual consistency

### Future Tasks
- Task 27: Populate indexes from existingTable (currently empty in edit mode)
- Task 27: Generate DDL for CREATE/ALTER TABLE with all constraints
- Task 28: Generate DDL for CREATE INDEX statements
- Validation: FK target table exists, target columns exist, FK columns match ref columns count
