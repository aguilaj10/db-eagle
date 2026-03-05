# Architecture Decisions

## Session Management Extraction Analysis (2026-03-04)

### Decision: **DO NOT extract session management from App.kt**

### Analysis

**Task**: Evaluate extracting session management to SessionManager.kt as per the refactoring plan.

**Finding**: The current architecture is already properly structured. Extraction would harm code quality.

---

### What Already Exists

**SessionViewModel.kt** (185 lines) serves as the session manager:
- ✅ Manages multiple database sessions (drivers, state)
- ✅ Handles session lifecycle (open/close/switch between sessions)
- ✅ Stores per-session data (query results, SQL text, schema cache)
- ✅ Exposes `StateFlow` for Compose reactivity
- ✅ Encapsulates all session business logic
- ✅ Tested with dedicated test file

**DatabaseConnectionPool.kt** handles connection pooling:
- ✅ HikariCP-based connection pool per profile
- ✅ Thread-safe singleton with `ConcurrentHashMap`
- ✅ Pool statistics API (`getPoolStats`, `getAllPoolStats`)
- ✅ Lifecycle management (create/close pools)

---

### Session Code in App.kt (Lines 126-144)

```kotlin
val sessionViewModel = remember { SessionViewModel() }
val sessionOrder by sessionViewModel.sessionOrder.collectAsState()
val sessionStates by sessionViewModel.sessionStates.collectAsState()
val activeProfileId by sessionViewModel.activeProfileId.collectAsState()

val activeSession = activeProfileId?.let { sessionStates[it] }
val activeDriver = activeProfileId?.let { sessionViewModel.getDriver(it) }
val activeProfileName = activeSession?.profileName

var poolStats by remember { mutableStateOf<DatabaseConnectionPool.PoolStats?>(null) }
LaunchedEffect(activeProfileId) {
    while (true) {
        poolStats = activeProfileId?.let { DatabaseConnectionPool.getPoolStats(it) }
        delay(1_000)
    }
}
```

---

### Why This Cannot Be Extracted

1. **`remember {}` is @Composable-only**: SessionViewModel instantiation requires Compose context for proper lifecycle management
2. **`collectAsState()` is Compose integration**: StateFlow → State conversion is a Compose primitive that triggers recomposition
3. **Derived values are reactive**: `activeSession`, `activeDriver`, `activeProfileName` automatically recompose when underlying state changes
4. **`LaunchedEffect` is lifecycle-aware**: The poolStats polling coroutine is tied to the Composable's lifecycle (starts on composition, cancels on disposal)
5. **Local UI state**: `poolStats` is presentation-layer concern (status bar display), not business logic

---

### Why "SessionManager" Would Harm the Codebase

Creating a separate SessionManager would:

1. **Duplicate SessionViewModel**: All session management logic already lives in SessionViewModel (185 lines)
2. **Break Compose patterns**: Fighting the framework's declarative state management model
3. **Add useless indirection**: Extra layer with zero functional benefit
4. **Violate Single Responsibility**: SessionViewModel already encapsulates session management
5. **Increase complexity**: Two classes doing one job, unclear ownership

---

### Current Architecture Is Correct

```
┌─────────────────────────────────────────┐
│         App.kt (Composable UI)          │
│  - Observes SessionViewModel state      │
│  - Renders UI based on state            │
│  - Polls pool stats for status bar      │
└─────────────────────────────────────────┘
                    ↓ observes via StateFlow
┌─────────────────────────────────────────┐
│       SessionViewModel (State Holder)    │
│  - Manages session state (sessions map) │
│  - Session lifecycle (open/close)       │
│  - Active session tracking              │
│  - Query results + schema cache         │
└─────────────────────────────────────────┘
                    ↓ uses
┌─────────────────────────────────────────┐
│  DatabaseConnectionPool (Data Layer)    │
│  - HikariCP connection pooling          │
│  - Pool statistics                      │
│  - Pool lifecycle management            │
└─────────────────────────────────────────┘
```

**This follows standard Compose architecture:**
- ✅ **UI Layer**: Composables observe state and render
- ✅ **State Layer**: ViewModels hold state and business logic
- ✅ **Data Layer**: Repositories/pools manage resources

---

### Alternative Improvement: Already Implemented

The original plan suggested extracting NavigationTab to NavigationManager.kt.

**This was already completed** (see learnings.md):
- ✅ Created `navigation/NavigationManager.kt` (10 lines)
- ✅ Moved `NavigationTab` enum from App.kt
- ✅ Clean separation of navigation concerns
- ✅ Build passes, no behavior changes

---

### Conclusion

**Status**: ✅ **Architecture is already correct. No extraction needed.**

**SessionViewModel IS the session manager.** The code in App.kt is proper Compose UI integration, not business logic that should be extracted.

**Recommendation**: Mark this task complete. Any further "extraction" would degrade code quality by fighting Compose's declarative state management model.

---

### Related Files
- `/app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt` (185 lines)
- `/data/src/main/kotlin/com/dbeagle/pool/DatabaseConnectionPool.kt` (191 lines)
- `/app/src/main/kotlin/com/dbeagle/App.kt` (lines 126-144)

---

## UI State Extraction Analysis (2026-03-04)

### Decision: **DO NOT extract UI state to AppState.kt**

### Analysis

**Task**: Evaluate extracting UI state variables from App.kt to AppState.kt.

**Finding**: The scattered state variables in App.kt are intentionally separated. Consolidation would fight Compose lifecycle management and add unnecessary complexity.

---

### State Inventory (Lines 123-155)

**8 state variables in `main()` function:**
```kotlin
var selectedTab by remember { mutableStateOf(NavigationTab.Connections) }
var statusText by remember { mutableStateOf("Status: Disconnected") }
var poolStats by remember { mutableStateOf<DatabaseConnectionPool.PoolStats?>(null) }
var memoryStats by remember { mutableStateOf(readMemoryStats()) }
var scratchSql by remember { mutableStateOf(SessionViewModel.DEFAULT_SQL) }
var favoriteQueryDraft by remember { mutableStateOf("") }
var showSaveFavoriteDialog by remember { mutableStateOf(false) }
var triggerNewConnection by remember { mutableStateOf(false) }
```

**4 dependencies:**
```kotlin
val sessionViewModel = remember { SessionViewModel() }
val snackbarHostState = remember { SnackbarHostState() }
val historyRepository = remember { FileQueryHistoryRepository() }
val favoritesRepository = remember { FileFavoritesRepository() }
```

---

### Why Extraction Would Harm Code Quality

#### 1. **Compose Lifecycle Constraint**

**Problem**: `remember {}` is a @Composable function that ties state to the composition lifecycle. State is automatically cleaned up when the Window closes.

```kotlin
// Current (correct):
var selectedTab by remember { mutableStateOf(NavigationTab.Connections) }

// Hypothetical extraction:
class AppUiState {
    var selectedTab by mutableStateOf(NavigationTab.Connections) // ❌ No remember {} scope
}
```

**Without `remember {}`**:
- State persists across window close/reopen (memory leak)
- Requires manual `reset()` method and explicit cleanup in `onCloseRequest`
- Loses automatic lifecycle management

#### 2. **Mixed Concerns Prevent Clean Extraction**

The 8 variables serve fundamentally different purposes:

| Variable | Purpose | Why Cannot Extract |
|----------|---------|-------------------|
| `selectedTab` | Navigation routing | Modified by keyboard shortcuts + tab clicks - extraction adds indirection |
| `statusText` | Status bar text | Written by 15+ components via callback - state holder adds boilerplate |
| `poolStats` | Connection pool metrics | Polled by `LaunchedEffect` - requires @Composable context |
| `memoryStats` | JVM memory usage | Polled by `LaunchedEffect` - requires @Composable context |
| `scratchSql` | Disconnected mode buffer | Fallback for `activeSession?.queryEditorSql` - conditional logic prevents extraction |
| `favoriteQueryDraft` | Dialog data | Transient state for dialog flow - scoped to composition |
| `showSaveFavoriteDialog` | Dialog visibility | Transient UI flag - scoped to composition |
| `triggerNewConnection` | Keyboard shortcut trigger | One-time flag reset after use - scoped to composition |

**Three categories that cannot be unified:**

**A. Transient UI Flags** (composition-scoped):
- `showSaveFavoriteDialog` - Dialog open/close
- `triggerNewConnection` - One-time trigger reset after use

**B. Polled Metrics** (LaunchedEffect-bound):
```kotlin
LaunchedEffect(activeProfileId) {
    while (true) {
        poolStats = activeProfileId?.let { DatabaseConnectionPool.getPoolStats(it) }
        memoryStats = readMemoryStats()
        delay(1_000)
    }
}
```
Cannot extract: `LaunchedEffect` is @Composable-only. Moving to a class requires manual coroutine lifecycle management.

**C. Conditional Fallback State**:
```kotlin
val sqlText = activeSession?.queryEditorSql ?: scratchSql
```
`scratchSql` only exists as fallback when no session is active. Cannot consolidate with session state due to conditional logic.

#### 3. **The State Holder Pattern Doesn't Fit**

**Compose state holder pattern requires:**
1. All state owned by the holder class
2. State updates go through the holder
3. UI reads from the holder

**App.kt violates this by design:**
- `statusText` is written by 15+ different UI components (ConnectionManagerScreen, QueryEditor, SchemaTree, FavoritesScreen, HistoryScreen, etc.)
- `selectedTab` is modified by keyboard shortcuts (`Cmd+Comma`) AND Tab clicks
- `scratchSql` is only relevant when `activeSession == null`

**Creating AppUiState would require:**
```kotlin
// Every status update across 15 components becomes:
onStatusTextChanged = { appState.statusText = it } // ❌ Unnecessary indirection

// vs. current:
onStatusTextChanged = { statusText = it } // ✅ Direct, simple
```

#### 4. **Per-Window State Is Intentional**

Every `application {}` call creates a new window with **fresh state**. This is the correct behavior:
- Window 1: User has connection tab selected, status shows "Connected to PostgreSQL"
- Window 2 (new instance): User gets fresh state, connection tab selected, status shows "Disconnected"

**Extracting to a singleton AppUiState would:**
- Share state across windows (wrong)
- Require manual reset on window close (boilerplate)
- Break per-window isolation

---

### Current Architecture Is Optimal

**The code follows Compose best practices:**

```
┌─────────────────────────────────────────┐
│         App.kt (Composable UI)          │
│  - Per-window state (remember {})      │
│  - Navigation routing                   │
│  - Status bar updates (from children)   │
│  - Metrics polling (LaunchedEffect)     │
└─────────────────────────────────────────┘
                    ↓ observes
┌─────────────────────────────────────────┐
│       SessionViewModel (State Holder)    │
│  - Multi-session business logic         │
│  - Query results, schema cache          │
│  - Exposes StateFlow for reactivity     │
└─────────────────────────────────────────┘
```

**Why this structure is correct:**
1. ✅ **Separation of concerns**: SessionViewModel handles session business logic, App.kt handles window-level UI state
2. ✅ **Lifecycle alignment**: `remember {}` automatically manages state cleanup on window close
3. ✅ **Direct communication**: Child components update `statusText` via callback without unnecessary indirection
4. ✅ **Compose idioms**: `LaunchedEffect` for side effects, `remember {}` for UI state, `collectAsState()` for observing StateFlow

---

### Comparison to SessionViewModel Decision

**Task 9 rejected SessionManager extraction because:**
- ✅ SessionViewModel already exists
- ✅ Business logic already extracted from UI
- ✅ Compose integration code must stay in App.kt

**This task (Task 10) rejects AppState extraction because:**
- ❌ No AppState exists, but creating one would fight Compose lifecycle management
- ❌ State variables are not cohesive (different concerns, different lifecycles)
- ❌ Extraction would add boilerplate without improving organization
- ❌ Current structure follows Compose best practices

**Both decisions share the same principle**: Do not fight the framework's state management model.

---

### Conclusion

**Status**: ✅ **Current architecture is already optimal. No extraction needed.**

**The 8 state variables in App.kt are:**
1. Properly scoped to the window's composition lifecycle
2. Intentionally separated due to different concerns (routing, metrics, dialogs, fallback state)
3. Following Compose best practices for local UI state

**Creating AppState.kt would:**
1. Break automatic lifecycle management (`remember {}`)
2. Add unnecessary indirection for status updates (15+ components)
3. Require manual cleanup on window close
4. Fight Compose's declarative state management model

**Recommendation**: Mark this task complete. The current structure is the correct Compose pattern for window-level UI state.

---

### Related Files
- `/app/src/main/kotlin/com/dbeagle/App.kt` (lines 123-155: state variables)
- `/app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt` (session business logic)

---

## When Statement Analysis (2026-03-04)

### Decision: **KEEP existing when statements - they are idiomatic Kotlin**

### Task 13: QueryExecutor.kt Analysis

**File**: `core/src/main/kotlin/com/dbeagle/query/QueryExecutor.kt` (121 lines)

**When statements found (2)**:

1. **Lines 28-41**: `when (val first = executePage(...))`
   - Branches: `PageOutcome.Err` (1 line), `PageOutcome.Ok` (8 lines)
   - Type: Sealed interface `PageOutcome` with exactly 2 subclasses
   - Purpose: Exhaustive handling of page execution result

2. **Lines 64-71**: `when (val result = driver.executeQuery(...))`
   - Branches: `QueryResult.Error` (1 line), `QueryResult.Success` (4 lines)
   - Type: Sealed class `QueryResult` with exactly 2 subclasses
   - Purpose: Exhaustive handling of query execution result

**Why no changes needed**:
- ✅ Both when statements have exactly 2 branches (minimum possible)
- ✅ Both operate on sealed types (compiler enforces exhaustiveness)
- ✅ Pattern is idiomatic Kotlin for sealed type matching
- ✅ Strategy pattern would add complexity without benefit
- ✅ File is only 121 lines - no complexity reduction needed

---

### Task 14: PreferencesBackedConnectionProfileRepository.kt Analysis

**File**: `core/src/main/kotlin/com/dbeagle/profile/PreferencesBackedConnectionProfileRepository.kt` (101 lines)

**When statements found (2)**:

1. **Lines 42-45**: `when (profile.type)`
   ```kotlin
   val typeDiscriminator = when (profile.type) {
       is com.dbeagle.model.DatabaseType.PostgreSQL -> "PostgreSQL"
       is com.dbeagle.model.DatabaseType.SQLite -> "SQLite"
   }
   ```
   - Branches: 2 (PostgreSQL, SQLite)
   - Type: Sealed class `DatabaseType`
   - Purpose: Serialize database type to string discriminator

2. **Lines 73-77**: `when (stored.typeDiscriminator)`
   ```kotlin
   val databaseType = when (stored.typeDiscriminator) {
       "PostgreSQL" -> com.dbeagle.model.DatabaseType.PostgreSQL
       "SQLite" -> com.dbeagle.model.DatabaseType.SQLite
       else -> throw IllegalArgumentException("Unknown database type: ${stored.typeDiscriminator}")
   }
   ```
   - Branches: 2 + else (for deserializing unknown types)
   - Type: String → sealed class mapping
   - Purpose: Deserialize string discriminator to database type

**Why no changes needed**:
- ✅ Both when statements have exactly 2 branches (+ 1 error branch)
- ✅ Serialization/deserialization pattern is standard practice
- ✅ The else branch is correct - handles corrupted/unknown data
- ✅ File is only 101 lines - already minimal
- ✅ No code duplication between the two when statements (different directions)

---

### Conclusion

**Status**: ✅ **All when statements are idiomatic Kotlin. No changes needed.**

**When to simplify when statements** (none apply here):
- ❌ >5 branches: Both have only 2 branches
- ❌ Complex logic per branch: All branches are 1-8 lines
- ❌ Repeated pattern in multiple files: Each when is unique
- ❌ Open/closed principle violation: Sealed types require exhaustive matching

**When to keep when statements as-is** (all apply here):
- ✅ Sealed type matching: Compiler enforces exhaustiveness
- ✅ 2-3 branches: Minimum complexity
- ✅ Single responsibility per branch: Map input to output
- ✅ Standard Kotlin idiom: Pattern is documented best practice

**Recommendation**: Mark Tasks 13 and 14 as complete. No code changes required.
