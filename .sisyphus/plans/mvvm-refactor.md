# MVVM Architecture Refactor

## TL;DR

> **Quick Summary**: Refactor DB Eagle from hybrid state management (callbacks + remember) to clean MVVM architecture using Koin DI, StateFlow, and proper separation of concerns. ViewModels own repositories, App.kt becomes a thin coordinator.
> 
> **Deliverables**: 
> - 5 new ViewModels (QueryEditor, ConnectionList, Settings, Favorites, History)
> - Koin module setup for all ViewModels and repositories
> - Refactored App.kt with reduced state and callback drilling
> - Unit tests for each new ViewModel
> - Preserved existing functionality (pure refactor, no new features)
> 
> **Estimated Effort**: Large (12-15 tasks across 4 waves)
> **Parallel Execution**: YES - 4 waves
> **Critical Path**: Wave 1 (Koin setup) → Wave 2 (Core ViewModels) → Wave 3 (Simple ViewModels + App.kt) → Wave 4 (Integration + Final Verification)

---

## Context

### Original Request
User requested MVVM architecture refactor after discovering only `SessionViewModel` exists while other screens use `remember {}` + callbacks. Analysis showed:
- QueryEditorScreen has 16 callback parameters
- ConnectionListScreen has 8 state vars with business logic
- SettingsScreen has 8 state vars with validation logic
- Business logic mixed with UI code across multiple screens

### Interview Summary
**Key Discussions**:
- ViewModel Creation: Use Koin DI (KMP best practices)
- Data Flow: Use StateFlow for unidirectional data flow
- Repository Ownership: ViewModels own repositories (not screens)
- App.kt Scope: Full refactor included
- Testing: Add unit tests for each new ViewModel

**Research Findings**:
- Screen complexity analysis identified QueryEditorScreen (P0), ConnectionListScreen (P1), SettingsScreen (P2) as priority targets
- Current architecture is "lite" unidirectional data flow with App.kt as coordinator
- MVVM best practices for Compose Desktop: Koin for DI, StateFlow, 1:1 screen:VM ratio
- `kotlinx-coroutines-swing` needed for proper Desktop dispatcher

### Metis Review
**Identified Gaps** (addressed):
- Koin vs remember decision: **Resolved** → Use Koin
- Repository ownership: **Resolved** → ViewModels own repositories
- App.kt scope: **Resolved** → Full refactor included
- Test requirements: **Resolved** → Unit tests for ViewModels

---

## Work Objectives

### Core Objective
Migrate DB Eagle to clean MVVM architecture where screens are pure UI, ViewModels contain business logic, and Koin manages dependency injection.

### Concrete Deliverables
- `QueryEditorViewModel.kt` - Query execution, export, history recording
- `ConnectionListViewModel.kt` - Connection management, profile CRUD
- `SettingsViewModel.kt` - Settings persistence, validation
- `FavoritesViewModel.kt` - Favorites CRUD, search
- `HistoryViewModel.kt` - History access, clearing
- Updated `AppModule.kt` with all ViewModel/repository bindings
- Refactored `App.kt` with minimal state
- Unit tests for all new ViewModels

### Definition of Done
- [ ] `./gradlew build test` passes
- [ ] All screens work identically to before (no functional changes)
- [ ] All ViewModels injected via Koin
- [ ] Repositories created in Koin modules (not screens)
- [ ] Unit tests pass for all new ViewModels
- [ ] App.kt has reduced state management

### Must Have
- Koin DI for all ViewModels
- StateFlow for all ViewModel state
- Repository ownership in ViewModels
- Unit tests for new ViewModels
- Preserved existing functionality

### Must NOT Have (Guardrails)
- **NO new features** - Pure refactoring only
- **NO changes to repository implementations** - Only move ownership
- **NO changes to data models** - Use existing models as-is
- **NO Android-specific dependencies** - KMP/Desktop compatible only
- **NO breaking changes to screen behavior** - Identical functionality
- **NO touching SchemaBrowserScreen** - Already uses SessionViewModel well
- **NO touching ConnectionManagerScreen** - Thin wrapper, minimal state

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES (bun test equivalent: `./gradlew test`)
- **Automated tests**: YES (Tests-after for ViewModels)
- **Framework**: JUnit 5 with Kotlin Test

### QA Policy
Every task MUST include agent-executed QA scenarios.
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **ViewModel Logic**: Bash (./gradlew test) - Run unit tests, verify pass
- **UI Integration**: Manual verification via running app (./gradlew run)
- **Build Verification**: Bash (./gradlew build) - Full compile check

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation — Koin setup, base patterns):
├── Task 1: Setup Koin ViewModel infrastructure [quick]
├── Task 2: Add kotlinx-coroutines-swing dependency [quick]
└── Task 3: Create base ViewModel pattern/utilities [quick]

Wave 2 (Core ViewModels — highest complexity, parallel):
├── Task 4: QueryEditorViewModel (depends: 1, 3) [deep]
├── Task 5: ConnectionListViewModel (depends: 1, 3) [deep]
└── Task 6: SettingsViewModel (depends: 1, 3) [unspecified-high]

Wave 3 (Simple ViewModels + App.kt refactor):
├── Task 7: FavoritesViewModel (depends: 1, 3) [quick]
├── Task 8: HistoryViewModel (depends: 1, 3) [quick]
└── Task 9: Refactor App.kt (depends: 4, 5, 6, 7, 8) [deep]

Wave 4 (Testing + Integration):
├── Task 10: Unit tests for QueryEditorViewModel (depends: 4) [unspecified-high]
├── Task 11: Unit tests for ConnectionListViewModel (depends: 5) [unspecified-high]
├── Task 12: Unit tests for SettingsViewModel (depends: 6) [quick]
├── Task 13: Unit tests for FavoritesViewModel + HistoryViewModel (depends: 7, 8) [quick]
└── Task 14: Integration verification (depends: 9) [deep]

Wave FINAL (Independent review, 4 parallel):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)

Critical Path: Task 1 → Task 4 → Task 9 → Task 14 → F1-F4
Parallel Speedup: ~60% faster than sequential
Max Concurrent: 3 (Wave 2)
```

### Dependency Matrix

| Task | Depends On | Blocks | Wave |
|------|------------|--------|------|
| 1 | — | 4, 5, 6, 7, 8 | 1 |
| 2 | — | 4, 5, 6 | 1 |
| 3 | — | 4, 5, 6, 7, 8 | 1 |
| 4 | 1, 2, 3 | 9, 10 | 2 |
| 5 | 1, 2, 3 | 9, 11 | 2 |
| 6 | 1, 2, 3 | 9, 12 | 2 |
| 7 | 1, 3 | 9, 13 | 3 |
| 8 | 1, 3 | 9, 13 | 3 |
| 9 | 4, 5, 6, 7, 8 | 14 | 3 |
| 10-13 | 4-8 | 14 | 4 |
| 14 | 9, 10-13 | F1-F4 | 4 |
| F1-F4 | 14 | — | FINAL |

### Agent Dispatch Summary

- **Wave 1**: 3 tasks → `quick` x3
- **Wave 2**: 3 tasks → `deep` x2, `unspecified-high` x1
- **Wave 3**: 3 tasks → `quick` x2, `deep` x1
- **Wave 4**: 5 tasks → `unspecified-high` x2, `quick` x2, `deep` x1
- **FINAL**: 4 tasks → `oracle` x1, `unspecified-high` x2, `deep` x1

### Task Workflow (CRITICAL - User Requested)

**For EVERY task, follow this exact workflow:**
1. **Implement** - Make the code changes
2. **Oracle Review** - Submit to Oracle agent for code review
3. **Quality Checks** - Run `./gradlew build test` (ONLY if Oracle approves)
4. **Commit** - Create commit with descriptive message (ONLY if quality passes)

**Trust subagent results** - Do NOT repeat quality checks if they already passed.

---

## TODOs

- [ ] 1. Setup Koin ViewModel Infrastructure

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt` with ViewModel bindings
  - Update `AppModule.kt` to include the new ViewModelModule
  - Add `koin-compose` dependency to `app/build.gradle.kts` if not present
  - Register empty ViewModel placeholders initially (will be filled in later tasks)

  **Must NOT do**:
  - Add Android-specific Koin dependencies (use koin-compose, not koin-android)
  - Create actual ViewModel implementations yet (just the module structure)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple module setup, follows existing patterns
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3)
  - **Blocks**: Tasks 4, 5, 6, 7, 8
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/di/AppModule.kt` - Existing Koin module structure, use `includes()` pattern

  **Build References**:
  - `app/build.gradle.kts:21` - Existing `libs.koin.core` dependency

  **External References**:
  - Koin Compose docs: https://insert-koin.io/docs/reference/koin-compose/compose/

  **WHY Each Reference Matters**:
  - AppModule.kt shows how to structure Koin modules and use includes() - follow this exact pattern
  - build.gradle.kts shows where to add koin-compose dependency (near koin.core)

  **Acceptance Criteria**:
  - [ ] `ViewModelModule.kt` exists with proper package declaration
  - [ ] `AppModule.kt` includes ViewModelModule
  - [ ] `./gradlew build` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Koin module structure compiles
    Tool: Bash
    Preconditions: Clean build state
    Steps:
      1. Run: ./gradlew clean build
      2. Check: Exit code is 0
      3. Check: No "Unresolved reference" errors in output
    Expected Result: BUILD SUCCESSFUL, no compile errors
    Evidence: .sisyphus/evidence/task-1-koin-setup-compile.txt

  Scenario: ViewModelModule included in AppModule
    Tool: Bash (grep)
    Preconditions: Task code changes applied
    Steps:
      1. Run: grep -r "ViewModelModule" app/src/main/kotlin/com/dbeagle/di/
      2. Check: AppModule.kt contains reference to ViewModelModule
    Expected Result: grep finds ViewModelModule in includes() call
    Evidence: .sisyphus/evidence/task-1-koin-includes.txt
  ```

  **Commit**: YES
  - Message: `refactor(di): setup Koin ViewModel infrastructure`
  - Files: `app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt`, `app/src/main/kotlin/com/dbeagle/di/AppModule.kt`, `app/build.gradle.kts`
  - Pre-commit: `./gradlew build`

---

- [ ] 2. Add kotlinx-coroutines-swing Dependency

  **What to do**:
  - Add `kotlinx-coroutines-swing` to `libs.versions.toml` (version catalog)
  - Add dependency to `app/build.gradle.kts`
  - This enables proper Swing EDT integration for Desktop coroutines

  **Must NOT do**:
  - Change any existing coroutine usage
  - Add Android coroutine dependencies

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single dependency addition
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3)
  - **Blocks**: Tasks 4, 5, 6
  - **Blocked By**: None

  **References**:

  **Build References**:
  - `gradle/libs.versions.toml` - Version catalog for dependency management
  - `app/build.gradle.kts:7-26` - Dependencies section

  **External References**:
  - KMP ViewModel docs: https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html (mentions swing dependency)

  **WHY Each Reference Matters**:
  - libs.versions.toml is where all dependencies are defined - add new entry here
  - Existing coroutines version in version catalog ensures compatibility

  **Acceptance Criteria**:
  - [ ] `kotlinx-coroutines-swing` in libs.versions.toml
  - [ ] Dependency added to app/build.gradle.kts
  - [ ] `./gradlew build` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Dependency resolves correctly
    Tool: Bash
    Preconditions: Clean build
    Steps:
      1. Run: ./gradlew dependencies --configuration runtimeClasspath | grep swing
      2. Check: kotlinx-coroutines-swing appears in output
    Expected Result: Dependency resolved with correct version
    Evidence: .sisyphus/evidence/task-2-swing-dependency.txt

  Scenario: Build succeeds with new dependency
    Tool: Bash
    Steps:
      1. Run: ./gradlew clean build
      2. Check: Exit code 0
    Expected Result: BUILD SUCCESSFUL
    Evidence: .sisyphus/evidence/task-2-build.txt
  ```

  **Commit**: YES
  - Message: `build(deps): add kotlinx-coroutines-swing for Desktop`
  - Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`
  - Pre-commit: `./gradlew build`

---

- [ ] 3. Create Base ViewModel Pattern/Utilities

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/viewmodel/BaseViewModel.kt` with:
    - CoroutineScope management (using Dispatchers.Default + SupervisorJob)
    - Common StateFlow update patterns
    - Cleanup/dispose method for scope cancellation
  - This establishes the pattern all ViewModels will follow

  **Must NOT do**:
  - Use Android ViewModel base class
  - Add lifecycle dependencies
  - Create actual business logic ViewModels

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Small utility class, follows research patterns
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2)
  - **Blocks**: Tasks 4, 5, 6, 7, 8
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt:42-55` - StateFlow patterns to follow
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt:172-178` - Private update helper pattern

  **External References**:
  - KMP Pure Kotlin ViewModels: https://medium.com/@fredosuala/kmp-architecture-the-case-for-pure-kotlin-viewmodels-c85ce95499ee

  **WHY Each Reference Matters**:
  - SessionViewModel shows proven StateFlow patterns - new ViewModels should be consistent
  - Medium article shows how to structure pure Kotlin VMs without Android dependencies

  **Acceptance Criteria**:
  - [ ] `BaseViewModel.kt` exists with CoroutineScope
  - [ ] Has protected StateFlow update helper
  - [ ] Has dispose/cleanup method
  - [ ] `./gradlew build` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: BaseViewModel compiles and follows pattern
    Tool: Bash
    Steps:
      1. Run: ./gradlew build
      2. Run: grep -l "CoroutineScope" app/src/main/kotlin/com/dbeagle/viewmodel/BaseViewModel.kt
    Expected Result: File exists with CoroutineScope, builds successfully
    Evidence: .sisyphus/evidence/task-3-basevm.txt

  Scenario: No Android dependencies
    Tool: Bash (grep)
    Steps:
      1. Run: grep -r "androidx.lifecycle" app/src/main/kotlin/com/dbeagle/viewmodel/
      2. Check: No matches (empty output)
    Expected Result: No Android lifecycle imports
    Evidence: .sisyphus/evidence/task-3-no-android.txt
  ```

  **Commit**: YES
  - Message: `refactor(vm): create base ViewModel utilities`
  - Files: `app/src/main/kotlin/com/dbeagle/viewmodel/BaseViewModel.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 4. Extract QueryEditorViewModel

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/viewmodel/QueryEditorViewModel.kt`
  - Extract from QueryEditorScreen:
    - Query execution logic (QueryExecutor usage)
    - Export logic (CsvExporter, JsonExporter, SqlExporter)
    - History recording (historyRepository.add)
    - Inline edit logic (InlineUpdate.buildUpdateById)
  - UI State class with: isRunning, currentQuery, lastResult, exportDialogState, editError
  - StateFlow exposure following SessionViewModel pattern
  - Register in ViewModelModule.kt with Koin `viewModel { }` DSL
  - ViewModel owns: QueryHistoryRepository (injected via Koin)

  **Must NOT do**:
  - Change query execution behavior
  - Modify QueryExecutor or exporter classes
  - Add new query features

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Complex extraction, 16 callbacks → single ViewModel, needs careful refactoring
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 5, 6)
  - **Blocks**: Tasks 9, 10
  - **Blocked By**: Tasks 1, 2, 3

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/ui/QueryEditorScreen.kt` - Source of logic to extract (FULL FILE)
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt:29-38` - UI state class pattern
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt:42-55` - StateFlow exposure pattern

  **Business Logic to Extract**:
  - `QueryEditorScreen.kt:180-220` (approx) - Query execution with QueryExecutor
  - `QueryEditorScreen.kt` - Export dialog handling, CsvExporter/JsonExporter/SqlExporter usage
  - `QueryEditorScreen.kt` - InlineUpdate.buildUpdateById usage for inline editing
  - `QueryEditorScreen.kt` - historyRepository.add() calls

  **Type References**:
  - `core/src/main/kotlin/com/dbeagle/model/QueryResult.kt` - QueryResult.Success shape
  - `app/src/main/kotlin/com/dbeagle/history/FileQueryHistoryRepository.kt` - Repository interface

  **WHY Each Reference Matters**:
  - QueryEditorScreen is the PRIMARY source - read it completely to understand all 16 callbacks
  - SessionViewModel patterns ensure consistency across ViewModels
  - QueryResult.Success shape determines what ViewModel stores for results

  **Acceptance Criteria**:
  - [ ] `QueryEditorViewModel.kt` exists with all business logic
  - [ ] QueryEditorScreen uses ViewModel (collectAsState)
  - [ ] 16 callbacks reduced to ViewModel method calls
  - [ ] ViewModel registered in Koin module
  - [ ] `./gradlew build test` passes
  - [ ] Query execution works identically

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: QueryEditorViewModel compiles and registers
    Tool: Bash
    Steps:
      1. Run: ./gradlew build
      2. Run: grep "QueryEditorViewModel" app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt
    Expected Result: ViewModel registered in Koin module
    Evidence: .sisyphus/evidence/task-4-vm-registered.txt

  Scenario: QueryEditorScreen uses ViewModel
    Tool: Bash (grep)
    Steps:
      1. Run: grep "koinViewModel" app/src/main/kotlin/com/dbeagle/ui/QueryEditorScreen.kt
      2. Check: koinViewModel<QueryEditorViewModel>() call exists
    Expected Result: Screen injects ViewModel via Koin
    Evidence: .sisyphus/evidence/task-4-screen-uses-vm.txt

  Scenario: Query execution still works (integration)
    Tool: Bash
    Preconditions: App running with test database
    Steps:
      1. Run: ./gradlew run (launch app)
      2. Manual: Connect to database, run SELECT query
      3. Verify: Results display correctly
    Expected Result: Query execution unchanged
    Evidence: .sisyphus/evidence/task-4-query-works.txt (describe manual test)
  ```

  **Commit**: YES
  - Message: `refactor(query): extract QueryEditorViewModel`
  - Files: `QueryEditorViewModel.kt`, `QueryEditorScreen.kt`, `ViewModelModule.kt`
  - Pre-commit: `./gradlew build test`

---

- [ ] 5. Extract ConnectionListViewModel

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/viewmodel/ConnectionListViewModel.kt`
  - Extract from ConnectionListScreen:
    - Profile loading (repository.loadAll)
    - Profile CRUD (save, delete)
    - Connection logic (DatabaseDriver instantiation, pool management)
    - Status updates (connection state tracking)
  - UI State class with: profiles, isLoading, error, connectionError, connectionStatus
  - ViewModel owns: ConnectionProfileRepository (injected via Koin)
  - Register in ViewModelModule.kt

  **Must NOT do**:
  - Change connection behavior
  - Modify DatabaseDriver or pool classes
  - Add new connection features

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Complex business logic extraction, connection management is critical
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 4, 6)
  - **Blocks**: Tasks 9, 11
  - **Blocked By**: Tasks 1, 2, 3

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/ui/ConnectionListScreen.kt` - Source of logic to extract (FULL FILE)
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt:61-90` - Session management pattern

  **Business Logic to Extract**:
  - `ConnectionListScreen.kt` - `refreshProfiles()` function
  - `ConnectionListScreen.kt` - `updateStatus()` function
  - `ConnectionListScreen.kt` - `connectToProfile()` function
  - `ConnectionListScreen.kt` - DatabaseDriver instantiation
  - `ConnectionListScreen.kt` - repository.save(), repository.delete() calls

  **Type References**:
  - `core/src/main/kotlin/com/dbeagle/profile/ConnectionProfileRepository.kt` - Repository interface
  - `core/src/main/kotlin/com/dbeagle/model/ConnectionProfile.kt` - Profile model

  **WHY Each Reference Matters**:
  - ConnectionListScreen contains ALL connection business logic - extract completely
  - SessionViewModel shows how to manage connection state with StateFlow

  **Acceptance Criteria**:
  - [ ] `ConnectionListViewModel.kt` exists with all business logic
  - [ ] ConnectionListScreen uses ViewModel
  - [ ] 8 state vars managed by ViewModel
  - [ ] ViewModel registered in Koin module
  - [ ] `./gradlew build test` passes
  - [ ] Connection flow works identically

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: ConnectionListViewModel compiles and registers
    Tool: Bash
    Steps:
      1. Run: ./gradlew build
      2. Run: grep "ConnectionListViewModel" app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt
    Expected Result: ViewModel registered
    Evidence: .sisyphus/evidence/task-5-vm-registered.txt

  Scenario: Connection flow preserved
    Tool: Bash
    Preconditions: App running
    Steps:
      1. ./gradlew run
      2. Manual: Create new connection profile
      3. Manual: Connect to database
      4. Manual: Verify connection status shows "Connected"
    Expected Result: Connection works as before
    Evidence: .sisyphus/evidence/task-5-connection-works.txt
  ```

  **Commit**: YES
  - Message: `refactor(connection): extract ConnectionListViewModel`
  - Files: `ConnectionListViewModel.kt`, `ConnectionListScreen.kt`, `ViewModelModule.kt`
  - Pre-commit: `./gradlew build test`

---

- [x] 6. Extract SettingsViewModel

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/viewmodel/SettingsViewModel.kt`
  - Extract from SettingsScreen:
    - Settings loading (AppPreferences.load)
    - Settings saving (AppPreferences.save)
    - Validation logic
    - Pool stats retrieval
  - UI State class with: settings (AppSettings), validationError, poolStats, isSaving
  - ViewModel owns: AppPreferences access
  - Register in ViewModelModule.kt

  **Must NOT do**:
  - Change settings persistence mechanism
  - Modify AppPreferences or AppSettings classes
  - Add new settings

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Medium complexity, 8 state vars, validation logic
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 4, 5)
  - **Blocks**: Tasks 9, 12
  - **Blocked By**: Tasks 1, 2, 3

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/ui/SettingsScreen.kt` - Source of logic to extract (FULL FILE)

  **Business Logic to Extract**:
  - `SettingsScreen.kt` - AppPreferences.load() call
  - `SettingsScreen.kt` - AppPreferences.save() with validation
  - `SettingsScreen.kt` - Pool stats retrieval (DatabaseConnectionPool.getAllPoolStats)

  **Type References**:
  - `app/src/main/kotlin/com/dbeagle/settings/AppSettings.kt` - Settings model
  - `app/src/main/kotlin/com/dbeagle/settings/AppPreferences.kt` - Preferences API

  **WHY Each Reference Matters**:
  - SettingsScreen has all settings logic to extract
  - AppSettings validation rules must be preserved in ViewModel

  **Acceptance Criteria**:
  - [ ] `SettingsViewModel.kt` exists with all business logic
  - [ ] SettingsScreen uses ViewModel
  - [ ] 8 state vars managed by ViewModel
  - [ ] Validation preserved
  - [ ] `./gradlew build test` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: SettingsViewModel compiles and registers
    Tool: Bash
    Steps:
      1. Run: ./gradlew build
      2. Run: grep "SettingsViewModel" app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt
    Expected Result: ViewModel registered
    Evidence: .sisyphus/evidence/task-6-vm-registered.txt

  Scenario: Settings save/load works
    Tool: Bash
    Steps:
      1. ./gradlew run
      2. Manual: Go to Settings, change result limit
      3. Manual: Save, close settings, reopen
      4. Verify: Changed value persisted
    Expected Result: Settings persistence unchanged
    Evidence: .sisyphus/evidence/task-6-settings-persist.txt
  ```

  **Commit**: YES
  - Message: `refactor(settings): extract SettingsViewModel`
  - Files: `SettingsViewModel.kt`, `SettingsScreen.kt`, `ViewModelModule.kt`
  - Pre-commit: `./gradlew build test`

---

- [x] 7. Extract FavoritesViewModel

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/viewmodel/FavoritesViewModel.kt`
  - Extract from FavoritesScreen:
    - Favorites loading (repository.getAll)
    - Search/filter logic (repository.search)
    - CRUD operations (save, delete)
  - UI State class with: favorites, searchQuery, editingFavorite, deletingFavorite
  - ViewModel owns: FavoritesRepository (injected via Koin)
  - Register in ViewModelModule.kt

  **Must NOT do**:
  - Change favorites behavior
  - Modify FavoritesRepository implementation

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simpler extraction, 4 state vars, straightforward CRUD
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 8, 9)
  - **Blocks**: Tasks 9, 13
  - **Blocked By**: Tasks 1, 3

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/ui/FavoritesScreen.kt` - Source of logic (FULL FILE)

  **Type References**:
  - `app/src/main/kotlin/com/dbeagle/favorites/FileFavoritesRepository.kt` - Repository interface
  - `core/src/main/kotlin/com/dbeagle/model/FavoriteQuery.kt` - Model

  **WHY Each Reference Matters**:
  - FavoritesScreen is simple - straightforward extraction

  **Acceptance Criteria**:
  - [ ] `FavoritesViewModel.kt` exists
  - [ ] FavoritesScreen uses ViewModel
  - [ ] `./gradlew build test` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: FavoritesViewModel registered
    Tool: Bash
    Steps:
      1. Run: ./gradlew build
      2. Run: grep "FavoritesViewModel" app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt
    Expected Result: ViewModel registered
    Evidence: .sisyphus/evidence/task-7-vm-registered.txt

  Scenario: Favorites CRUD works
    Tool: Bash
    Steps:
      1. ./gradlew run
      2. Manual: Add favorite, search, delete
    Expected Result: All operations work
    Evidence: .sisyphus/evidence/task-7-favorites-crud.txt
  ```

  **Commit**: YES
  - Message: `refactor(favorites): extract FavoritesViewModel`
  - Files: `FavoritesViewModel.kt`, `FavoritesScreen.kt`, `ViewModelModule.kt`
  - Pre-commit: `./gradlew build test`

---

- [x] 8. Extract HistoryViewModel

  **What to do**:
  - Create `app/src/main/kotlin/com/dbeagle/viewmodel/HistoryViewModel.kt`
  - Extract from HistoryScreen:
    - History loading (repository.getAll)
    - History clearing (repository.clear)
  - UI State class with: entries, showClearDialog
  - ViewModel owns: QueryHistoryRepository (injected via Koin)
  - Register in ViewModelModule.kt

  **Must NOT do**:
  - Change history behavior
  - Modify QueryHistoryRepository implementation

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simplest extraction, 2 state vars
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 7, 9)
  - **Blocks**: Tasks 9, 13
  - **Blocked By**: Tasks 1, 3

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/ui/HistoryScreen.kt` - Source of logic (FULL FILE)

  **Type References**:
  - `app/src/main/kotlin/com/dbeagle/history/FileQueryHistoryRepository.kt` - Repository

  **Acceptance Criteria**:
  - [ ] `HistoryViewModel.kt` exists
  - [ ] HistoryScreen uses ViewModel
  - [ ] `./gradlew build test` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: HistoryViewModel registered
    Tool: Bash
    Steps:
      1. Run: ./gradlew build
      2. Run: grep "HistoryViewModel" app/src/main/kotlin/com/dbeagle/di/ViewModelModule.kt
    Expected Result: ViewModel registered
    Evidence: .sisyphus/evidence/task-8-vm-registered.txt
  ```

  **Commit**: YES
  - Message: `refactor(history): extract HistoryViewModel`
  - Files: `HistoryViewModel.kt`, `HistoryScreen.kt`, `ViewModelModule.kt`
  - Pre-commit: `./gradlew build test`

---

- [ ] 9. Refactor App.kt with Koin ViewModels

  **What to do**:
  - Remove direct repository creation (`remember { FileQueryHistoryRepository() }`, etc.)
  - Remove callback drilling for ViewModels (screens get VMs via Koin)
  - Keep SessionViewModel (shared state for database sessions)
  - Simplify state in App.kt:
    - Remove: favoriteQueryDraft, showSaveFavoriteDialog (move to QueryEditorViewModel)
    - Keep: selectedTab, statusText (navigation concerns)
  - Update screen invocations to pass only navigation-related callbacks
  - SessionViewModel should also be injected via Koin (convert from `remember {}`)

  **Must NOT do**:
  - Remove SessionViewModel (still needed for multi-session management)
  - Change navigation behavior
  - Add new features

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Central coordinator refactor, touches all screen invocations
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (after Wave 2)
  - **Blocks**: Task 14
  - **Blocked By**: Tasks 4, 5, 6, 7, 8

  **References**:

  **Pattern References**:
  - `app/src/main/kotlin/com/dbeagle/App.kt` - File to refactor (FULL FILE)
  - All ViewModel files created in Tasks 4-8

  **Current State Analysis**:
  - `App.kt:107-115` - State vars to potentially move to ViewModels
  - `App.kt:113-114` - Repository creation to remove
  - `App.kt:305-373` - Screen invocations to simplify

  **WHY Each Reference Matters**:
  - App.kt is the central file being refactored - understand completely before changes
  - ViewModels from prior tasks determine what state moves out of App.kt

  **Acceptance Criteria**:
  - [ ] No `remember { XxxRepository() }` in App.kt
  - [ ] Screens get ViewModels via Koin (not passed from App.kt)
  - [ ] SessionViewModel injected via Koin
  - [ ] App.kt has fewer state variables
  - [ ] `./gradlew build test` passes
  - [ ] All screens still work

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: App.kt simplified
    Tool: Bash (grep)
    Steps:
      1. Run: grep -c "remember {" app/src/main/kotlin/com/dbeagle/App.kt
      2. Count should be significantly reduced (target: <5 from ~10+)
    Expected Result: Fewer remember blocks
    Evidence: .sisyphus/evidence/task-9-app-simplified.txt

  Scenario: All screens work end-to-end
    Tool: Bash + Manual
    Steps:
      1. ./gradlew run
      2. Navigate: Connections → QueryEditor → Schema → Favorites → History → Settings
      3. Perform action in each screen
    Expected Result: All screens functional
    Evidence: .sisyphus/evidence/task-9-all-screens.txt
  ```

  **Commit**: YES
  - Message: `refactor(app): simplify App.kt with Koin ViewModels`
  - Files: `App.kt`, potentially screen files for callback changes
  - Pre-commit: `./gradlew build test`

---

- [ ] 10. Unit Tests for QueryEditorViewModel

  **What to do**:
  - Create `app/src/test/kotlin/com/dbeagle/viewmodel/QueryEditorViewModelTest.kt`
  - Test cases:
    - Query execution updates state correctly
    - History recording called on successful query
    - Export functions produce correct output
    - Error handling updates error state
  - Use mock QueryHistoryRepository

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: ViewModel has complex logic, needs thorough testing
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4 (with Tasks 11, 12, 13)
  - **Blocks**: Task 14
  - **Blocked By**: Task 4

  **References**:

  **Pattern References**:
  - Existing test files in `app/src/test/kotlin/` for test structure
  - `QueryEditorViewModel.kt` - Code under test

  **Acceptance Criteria**:
  - [ ] Test file exists with meaningful tests
  - [ ] `./gradlew test` passes
  - [ ] Key business logic covered

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Tests pass
    Tool: Bash
    Steps:
      1. Run: ./gradlew test --tests "*QueryEditorViewModelTest*"
    Expected Result: All tests pass
    Evidence: .sisyphus/evidence/task-10-tests-pass.txt
  ```

  **Commit**: YES
  - Message: `test(vm): add unit tests for QueryEditorViewModel`
  - Files: `QueryEditorViewModelTest.kt`
  - Pre-commit: `./gradlew test`

---

- [ ] 11. Unit Tests for ConnectionListViewModel

  **What to do**:
  - Create `app/src/test/kotlin/com/dbeagle/viewmodel/ConnectionListViewModelTest.kt`
  - Test cases:
    - Profile loading populates state
    - Profile save/delete updates state
    - Connection state transitions
    - Error handling

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Connection logic is critical, needs good coverage
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 14
  - **Blocked By**: Task 5

  **References**:
  - `ConnectionListViewModel.kt` - Code under test

  **Acceptance Criteria**:
  - [ ] Test file exists
  - [ ] `./gradlew test` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Tests pass
    Tool: Bash
    Steps:
      1. Run: ./gradlew test --tests "*ConnectionListViewModelTest*"
    Expected Result: All tests pass
    Evidence: .sisyphus/evidence/task-11-tests-pass.txt
  ```

  **Commit**: YES
  - Message: `test(vm): add unit tests for ConnectionListViewModel`
  - Files: `ConnectionListViewModelTest.kt`
  - Pre-commit: `./gradlew test`

---

- [ ] 12. Unit Tests for SettingsViewModel

  **What to do**:
  - Create `app/src/test/kotlin/com/dbeagle/viewmodel/SettingsViewModelTest.kt`
  - Test cases:
    - Settings load populates state
    - Validation rejects invalid values
    - Save persists correctly

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simpler ViewModel, fewer test cases
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 14
  - **Blocked By**: Task 6

  **References**:
  - `SettingsViewModel.kt` - Code under test

  **Acceptance Criteria**:
  - [ ] Test file exists
  - [ ] `./gradlew test` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Tests pass
    Tool: Bash
    Steps:
      1. Run: ./gradlew test --tests "*SettingsViewModelTest*"
    Expected Result: All tests pass
    Evidence: .sisyphus/evidence/task-12-tests-pass.txt
  ```

  **Commit**: YES
  - Message: `test(vm): add unit tests for SettingsViewModel`
  - Files: `SettingsViewModelTest.kt`
  - Pre-commit: `./gradlew test`

---

- [ ] 13. Unit Tests for FavoritesViewModel + HistoryViewModel

  **What to do**:
  - Create `app/src/test/kotlin/com/dbeagle/viewmodel/FavoritesViewModelTest.kt`
  - Create `app/src/test/kotlin/com/dbeagle/viewmodel/HistoryViewModelTest.kt`
  - Basic CRUD operation tests for both

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple ViewModels, basic tests
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 14
  - **Blocked By**: Tasks 7, 8

  **References**:
  - `FavoritesViewModel.kt`, `HistoryViewModel.kt` - Code under test

  **Acceptance Criteria**:
  - [ ] Both test files exist
  - [ ] `./gradlew test` passes

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Tests pass
    Tool: Bash
    Steps:
      1. Run: ./gradlew test --tests "*FavoritesViewModelTest*" --tests "*HistoryViewModelTest*"
    Expected Result: All tests pass
    Evidence: .sisyphus/evidence/task-13-tests-pass.txt
  ```

  **Commit**: YES
  - Message: `test(vm): add unit tests for FavoritesViewModel and HistoryViewModel`
  - Files: `FavoritesViewModelTest.kt`, `HistoryViewModelTest.kt`
  - Pre-commit: `./gradlew test`

---

- [ ] 14. Integration Verification

  **What to do**:
  - Run full test suite: `./gradlew build test`
  - Manual smoke test of entire application
  - Verify all screens work with new MVVM architecture
  - Document any regressions found
  - Create evidence file with full verification results

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Comprehensive verification, needs careful attention
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (after all other tasks)
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 9, 10, 11, 12, 13

  **Acceptance Criteria**:
  - [ ] `./gradlew build test` passes
  - [ ] All screens functional
  - [ ] No regressions

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Full build and test
    Tool: Bash
    Steps:
      1. Run: ./gradlew clean build test
    Expected Result: BUILD SUCCESSFUL, all tests pass
    Evidence: .sisyphus/evidence/task-14-full-build.txt

  Scenario: Application smoke test
    Tool: Bash + Manual
    Steps:
      1. ./gradlew run
      2. Test each screen: Connections, QueryEditor, Schema, Favorites, History, Settings
      3. Perform typical user workflows
    Expected Result: All features work
    Evidence: .sisyphus/evidence/task-14-smoke-test.txt
  ```

  **Commit**: YES
  - Message: `test(integration): verify MVVM migration complete`
  - Files: Evidence files only
  - Pre-commit: `./gradlew build test`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Rejection → fix → re-run.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, check Koin module). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew build test`. Review all new ViewModel files for: `as any`/`@Suppress`, empty catches without comments, hardcoded strings, unused parameters. Check MVVM patterns: StateFlow exposure, no direct repository creation in screens.
  Output: `Build [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Run `./gradlew run`. Navigate through ALL screens: Connections → QueryEditor → SchemaBrowser → Favorites → History → Settings. Verify each screen works identically to before. Test: new connection, run query, view schema, add favorite, view history, change settings.
  Output: `Scenarios [N/N pass] | Regressions [NONE/list] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff. Verify pure refactoring (no new features). Check "Must NOT do" compliance. Detect scope creep: new UI elements, changed behavior, new dependencies beyond kotlinx-coroutines-swing.
  Output: `Tasks [N/N compliant] | Scope Creep [NONE/list] | VERDICT`

---

## Commit Strategy

| Task | Commit Message | Pre-commit |
|------|---------------|------------|
| 1 | `refactor(di): setup Koin ViewModel infrastructure` | `./gradlew build` |
| 2 | `build(deps): add kotlinx-coroutines-swing for Desktop` | `./gradlew build` |
| 3 | `refactor(vm): create base ViewModel utilities` | `./gradlew build` |
| 4 | `refactor(query): extract QueryEditorViewModel` | `./gradlew build test` |
| 5 | `refactor(connection): extract ConnectionListViewModel` | `./gradlew build test` |
| 6 | `refactor(settings): extract SettingsViewModel` | `./gradlew build test` |
| 7 | `refactor(favorites): extract FavoritesViewModel` | `./gradlew build test` |
| 8 | `refactor(history): extract HistoryViewModel` | `./gradlew build test` |
| 9 | `refactor(app): simplify App.kt with Koin ViewModels` | `./gradlew build test` |
| 10-13 | `test(vm): add unit tests for [ViewModel]` | `./gradlew test` |
| 14 | `test(integration): verify MVVM migration` | `./gradlew build test` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build  # Expected: BUILD SUCCESSFUL
./gradlew test   # Expected: All tests pass
./gradlew run    # Expected: App launches, all screens functional
```

### Final Checklist
- [ ] All "Must Have" present (Koin DI, StateFlow, VM tests)
- [ ] All "Must NOT Have" absent (no new features, no Android deps)
- [ ] All screens work identically to before
- [ ] App.kt has reduced complexity (fewer state vars, fewer callbacks)
- [ ] All ViewModels have unit tests
