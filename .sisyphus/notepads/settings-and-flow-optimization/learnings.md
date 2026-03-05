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
