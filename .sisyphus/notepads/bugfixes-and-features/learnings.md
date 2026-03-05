
## Task 5: QueryLogService Integration

**Pattern**: Logging wrapper for query execution
- Captured start time before execution, calculated duration after
- Logged both SELECT (paginated) and non-SELECT paths
- Used silent exception handling for logging failures (non-blocking)
- Extracted row count from QueryResult.Success, error message from QueryResult.Error
- QueryLogService.logQuery() is a synchronous call wrapped in try-catch

**Implementation**: Added timing + logging to both execution branches in QueryExecutor.execute():
1. Non-SELECT path: Direct driver.executeQuery() call
2. SELECT path: Paginated execution through executePage()

**Status extraction**: Used Kotlin's smart casts and safe cast operators for clean status/rowCount/errorMessage extraction from sealed QueryResult types.

## IndexEditorDialog Implementation

**Key Patterns:**
- Dialog signature receives `dialect: DDLDialect` (not DatabaseDriver)
- Caller provides `tables: List<String>` and `getColumnsForTable` callback
- Used `mutableStateListOf<String>()` for multi-select columns
- LaunchedEffect(selectedTable) loads columns dynamically when table changes
- Multi-select dropdown: Checkbox inside DropdownMenuItem with manual toggle logic
- Disabled OutlinedTextField with clickable modifier for dropdown trigger pattern
- Preview button opens DDLPreviewDialog with `isDestructive = false` for indexes
- Create button uses coroutine scope to call async onCreate callback

**Form Validation:**
- Index name: DDLValidator.validateIdentifier()
- Table: Required (dropdown selection)
- Columns: Required, at least one selected
- Unique: Boolean checkbox, defaults to false

**DDL Generation:**
- IndexDDLBuilder.buildCreateIndex(dialect, IndexDefinition)
- IndexDefinition: name, tableName, columns (List<String>), unique (Boolean)


## Tasks 10-11: Context Menu Actions for Views and Indexes

**Pattern**: Following existing Table/Sequence context menu structure
- SchemaTree.kt callbacks: Pass function parameters for each operation (onNewView, onDropView, onNewIndex, onDropIndex)
- Pass callbacks through SchemaTreeNodeItem composable
- showContextMenu condition: Check section IDs ("section:views", "section:indexes") and node types (SchemaTreeNode.View, SchemaTreeNode.Index)
- DropdownMenuItem pattern: Call callback, set showContextMenu = false

**SchemaBrowserScreen.kt wiring:**
- ViewEditorDialog and IndexEditorDialog exist with different signatures than Table/Sequence editors
- ViewEditorDialog: Takes dialect, onDismiss, onSave(ddl) - returns DDL directly
- IndexEditorDialog: Takes dialect, tables, getColumnsForTable, onDismiss, onPreview(ddl), onCreate(ddl) - two-step preview/create
- Drop operations: Inline DDL generation using ViewDDLBuilder.buildDropView() and IndexDDLBuilder.buildDropIndex()
- No SchemaEditorViewModel methods exist for views/indexes - direct DDL builder usage + local executeDDL helper

**Local helper functions added to SchemaBrowserScreen:**
- getDialectForDriver(driver): Maps driver.getName() to DDLDialect (PostgreSQLDDLDialect, SQLiteDDLDialect)
- executeDDL(driver, ddl): Async DDL execution with error mapping via DDLErrorMapper, returns Result<Unit>

**Important differences from Table/Sequence:**
- Views: No "Edit View" - use CREATE OR REPLACE VIEW instead
- Indexes: No "Edit Index" - recreate indexes, don't alter
- Drop operations use DDL builders directly (not SchemaEditorViewModel)
