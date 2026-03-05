# Learnings - Settings Migration & Flow Optimization

## Conventions
<!-- Add coding conventions discovered during execution -->

## Patterns
<!-- Add reusable patterns found in the codebase -->

## Tips
<!-- Add helpful tips for future tasks -->

## ORCHESTRATOR RULES (MANDATORY)
- **ALWAYS use `category="deep"` for ALL task delegations** - User preference
- Do NOT use `quick`, `unspecified-high`, or other categories
- This applies to ALL tasks regardless of plan recommendation

## Task 11: HistoryViewModel Flow Collection (2026-03-05)

**Implementation:**
- Replaced manual `refreshHistory()` call in init with reactive Flow collection
- Added `viewModelScope.launch` to collect `repository.getAllFlow()`
- Applied `.distinctUntilChanged()` before collection (explicit, though StateFlow has built-in)
- Converted `refreshHistory()` to no-op with explanatory comment
- Kept `clearHistory()` unchanged - repository clear triggers Flow update automatically

**Key Pattern:**
```kotlin
init {
    viewModelScope.launch {
        repository.getAllFlow()
            .distinctUntilChanged()
            .collect { entries ->
                updateStateFlow(_uiState) { it.copy(entries = entries) }
            }
    }
}
```

**Architecture Notes:**
- Flow collection runs in viewModelScope (from BaseViewModel)
- No manual refresh needed - Flow emits on repository changes
- clearHistory() still directly updates UI state for immediate feedback, but repository clear triggers Flow emission

## Task 10: FavoritesViewModel Flow Collection (2026-03-05)

**Implementation:**
- Replaced manual `getAll()` call with reactive `getAllFlow()` collection in init block
- Used `distinctUntilChanged()` to prevent redundant updates (though StateFlow already deduplicates)
- Collected Flow in `viewModelScope.launch` to update _uiState
- Converted `refreshFavorites()` to no-op with explanatory comment
- Removed manual `refreshFavorites()` calls from `saveFavorite()` and `deleteFavorite()`
- Flow automatically propagates repository changes to UI state

**Pattern Applied:**
```kotlin
init {
    viewModelScope.launch {
        repository.getAllFlow()
            .distinctUntilChanged()
            .collect { favorites ->
                updateStateFlow(_uiState) { it.copy(favorites = favorites) }
            }
    }
}
```

**Benefits:**
- Automatic reactivity - no manual refresh needed
- UI stays synchronized with repository changes
- Cleaner code - removed imperative refresh calls

**Note:** Did not use `stateIn()` as suggested in task description because we're already updating a MutableStateFlow. The pattern works correctly without additional StateFlow conversion.

## Task 9: SettingsViewModel Reactive Migration

### Pattern: Reactive ViewModel with Flow Collection
- Collect repository Flow in init block using `repository.settingsFlow.collect { }`
- Update state reactively on each emission using `updateStateFlow` helper
- Initialize MutableStateFlow with default AppSettings() to avoid nullable state
- Flow collection runs in viewModelScope.launch and updates UI state continuously

### State Initialization Strategy
- Start with non-null default: `MutableStateFlow(SettingsUiState(settings = AppSettings()))`
- Collect Flow in init and update all input fields to match settings on each emission
- This ensures UI always reflects current settings from repository

### Save Operations
- Replace synchronous `AppPreferences.save()` with `repository.saveSettings()`
- Keep existing `viewModelScope.launch` for async execution
- Repository handles all individual field updates via suspend functions

### DI Wiring
- Register `AppPreferencesRepository` as singleton in CoreModule
- Use Koin's `get()` to inject dependencies in correct parameter order
- Factory pattern: `factory { SettingsViewModel(get(), get()) }`

### Compilation Notes
- Warnings about ExperimentalSettingsApi in AppPreferencesRepository are expected (from core module)
- ViewModel changes compile successfully once DI container provides repository instance

## Task 13: DI Module Updates for Repositories

### Changes Made
1. **DataModule.kt**: Added repository bindings
   - `single<FavoritesRepository> { FileFavoritesRepository() }`
   - `single<QueryHistoryRepository> { FileQueryHistoryRepository() }`

2. **ThemeManager.kt**: Already updated by Task 12 to inject `AppPreferencesRepository`
   - Constructor now takes `AppPreferencesRepository` parameter
   - Uses reactive `darkModeFlow` from repository
   - Properly manages coroutine scope for async operations

3. **ViewModelModule.kt**: Already updated to inject repository into ThemeManager
   - `single { ThemeManager(get()) }` - Koin resolves `AppPreferencesRepository`

### DI Pattern Applied
- Interface-based bindings: `single<Interface> { Implementation() }`
- Fully qualified names used in DataModule to avoid import ambiguity
- ViewModels use `get()` to resolve dependencies
- Singleton scope for stateful services (ThemeManager, repositories)

### Verification
- ✅ `./gradlew :app:compileKotlin` passes
- ✅ All ViewModels can resolve dependencies (FavoritesViewModel, HistoryViewModel, SettingsViewModel)
- ✅ ThemeManager reactively observes dark mode changes via repository flow

## Task 8: ConnectionProfileRepositoryTest Migration

Successfully migrated ConnectionProfileRepositoryTest from java.util.prefs.Preferences to multiplatform-settings.

### Key Changes:
1. **Import changes**: 
   - Removed: `import java.util.prefs.Preferences`
   - Added: `import com.russhwolf.settings.MapSettings` and `import com.russhwolf.settings.Settings`
   - Added: `import kotlinx.coroutines.flow.first` for Flow testing

2. **Test setup refactored**:
   - Changed from `Preferences` to `Settings` (interface type)
   - Used `MapSettings()` implementation (in-memory, no filesystem needed)
   - Removed `@AfterTest teardown()` - not needed with MapSettings
   - Changed repository type from interface to concrete class for profilesFlow access

3. **API adaptations**:
   - `preferences.get(key, default)` → `settings.getStringOrNull(key)`
   - `preferences` constructor param → `settings` constructor param
   - Removed Preferences cleanup (clear/removeNode) - not needed for MapSettings

4. **New Flow tests added**:
   - Test that profilesFlow emits empty list initially
   - Test that profilesFlow emits updated list after save
   - Test that profilesFlow emits updated list after delete
   - All use `repository.profilesFlow.first()` with runTest

5. **Side effect**: Fixed AppPreferencesTest.kt which also had MockSettings import (doesn't exist) - changed to MapSettings

### Important Notes:
- MapSettings is a simple Map-backed Settings implementation perfect for tests
- No external dependencies needed (already in multiplatform-settings)
- All encryption tests still pass - logic unchanged
- profilesFlow requires concrete repository type, not interface

## ViewDDLBuilder Implementation (2026-03-05)

### Pattern Followed
- Created `ViewDDLBuilder` object following `SequenceDDLBuilder` pattern
- Used `DDLDialect` interface for database-agnostic identifier quoting
- Implemented `buildCreateView()` and `buildDropView()` functions

### Key Features
- **Schema Support**: Qualified view names (`schema.view_name`)
- **OR REPLACE**: `CREATE OR REPLACE VIEW` for PostgreSQL/Oracle
- **IF EXISTS**: `DROP VIEW IF EXISTS` when `dialect.supportsIfExists()` is true
- **CASCADE**: `DROP VIEW ... CASCADE` option for PostgreSQL

### Database Coverage
- PostgreSQL: Full support (OR REPLACE, IF EXISTS, CASCADE)
- SQLite: CREATE VIEW, DROP VIEW IF EXISTS
- Oracle: CREATE OR REPLACE VIEW, DROP VIEW (no IF EXISTS typically)

### Implementation Notes
- Used `buildString {}` builder pattern for clean SQL generation
- Schema prefix handled via conditional qualified naming
- Followed existing KDoc documentation style for consistency
- No materialized view support (out of scope)

## QueryLogService Implementation (Task 3 - Query Log Feature)

### Pattern Learnings
1. **NDJSON Format**: One JSON object per line enables efficient appending without parsing entire file
2. **kotlinx.serialization**: Use `Json.encodeToString()` for serialization, `decodeFromString()` for parsing
3. **Lazy File Initialization**: Use `by lazy` for file path construction (matches ErrorHandler pattern)
4. **Silent Failure**: Logging services should fail silently (printStackTrace) to avoid crashing the app
5. **Directory Creation**: Always call `mkdirs()` before file operations

### File Structure
- Location: `~/.dbeagle/query.log`
- Format: NDJSON (newline-delimited JSON)
- Directory: Same `.dbeagle` directory as crash.log, error.log

### Data Model
- **QueryStatus**: Simple enum (SUCCESS, ERROR)
- **QueryLogEntry**: Captures timestamp, sql, duration, status, optional rowCount/errorMessage
- **QueryLogService**: Singleton object with logQuery(), getLogs(), clearLogs()

### Error Handling
- Malformed JSON lines are skipped during parsing (mapNotNull pattern)
- File I/O errors are caught and printed but don't crash the service
- Empty/non-existent files return empty list (graceful degradation)

### Compilation Note
- Pre-existing error in PreferencesBackedConnectionProfileRepository.kt (exhaustive when for Oracle)
- QueryLogService compiles cleanly (verified via Gradle compilation output)

## Oracle Driver Implementation (2026-03-05)

### Sequences Query Pattern
- Oracle uses `user_sequences` system view for sequence metadata
- Key columns: `sequence_name`, `min_value`, `max_value`, `increment_by`, `last_number`, `cycle_flag`
- `cycle_flag` is 'Y' for cycling sequences, any other value means no cycle
- `last_number` approximates start value (Oracle doesn't store original start value after use)
- Schema determined via `SELECT USER FROM DUAL` query

### Implementation Structure
- Followed PostgreSQLDriver pattern closely for consistency
- Oracle test connection uses `SELECT 1 FROM DUAL` (Oracle's dummy table)
- JDBC URL format: `jdbc:oracle:thin:@{host}:{port}:{database}`
- getCurrentSchema() helper returns current user as schema (Oracle's user-schema model)

### Code Organization Issues
- Private top-level classes with same name cause compilation conflicts across files in same package
- Solution: Renamed Oracle-specific helpers with `Oracle` prefix:
  - `OraclePoolBackedDataSource` instead of `PoolBackedDataSource`
  - `oracleColumnNames()` and `oracleRowsAsStringMaps()` extension functions
- Alternative would be extracting shared utilities to common file

### DatabaseType Integration
- Added `DatabaseType.Oracle` to sealed class in core module
- Updated all exhaustive `when` expressions:
  - `PreferencesBackedConnectionProfileRepository` (save/load type discriminators)
  - `DatabaseConnectionPool.buildJdbcUrl()` (already done)
- Registered in `DataDrivers.registerAll()`

### Testing Approach
- Compilation verified with `./gradlew :data:compileKotlin`
- Build successful confirms all when expressions exhaustive
- No runtime Oracle database needed for compilation verification
