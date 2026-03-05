# Code Quality Improvement Plan

## TL;DR

> **Quick Summary**: Comprehensive code quality overhaul of DB Eagle Kotlin/Compose Desktop application - replacing wildcard imports with explicit imports, removing inline package references, splitting god classes, reducing complexity, and removing dead code. Each task includes Oracle review and commit steps.
>
> **Deliverables**:
> - All 50 import issues resolved (36 wildcard imports, 14 inline references)
> - App.kt (1020 lines) split into focused modules
> - ConnectionManagerScreen.kt (606 lines) refactored
> - Code duplication eliminated
> - Decision trees converted to abstractions
> - Dead code removed
> - Full documentation of decisions and gotchas
>
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 5 waves
> **Critical Path**: Wave 1 (imports) → Wave 2 (god class split) → Wave 3 (complexity) → Wave 4 (abstractions) → Wave 5 (cleanup)

---

## Context

### Original Request
User requested comprehensive code quality improvement:
1. Remove wildcard imports - use explicit imports
2. Remove inline full package references - use imports
3. Reduce code duplication
4. Reduce function/class complexity (single responsibility)
5. Replace decision trees with abstractions
6. Remove dead code and unused resources
7. Oracle review after EACH task
8. Commit after EACH task
9. Document all decisions, gotchas, issues, blockers

### Analysis Results

**Wildcard Imports Found (50 total issues)**:
- 36 wildcard imports across 10 files in app module
- 14 inline full package references across 4 files
- Core module is clean (0 issues)

**Files by Wildcard Import Count**:
| File | Wildcards | Inline Refs |
|------|-----------|-------------|
| App.kt | 3 | 1 |
| ResultGrid.kt | 5 | 0 |
| SchemaTree.kt | 5 | 0 |
| SQLEditor.kt | 4 | 0 |
| HistoryScreen.kt | 4 | 0 |
| FavoritesScreen.kt | 4 | 0 |
| ConnectionDialog.kt | 3 | 0 |
| ExportDialog.kt | 3 | 0 |
| SettingsScreen.kt | 3 | 0 |
| ConnectionManagerScreen.kt | 2 | 0 |
| SessionViewModel.kt | 0 | 2 |
| PostgreSQLDriver.kt | 0 | 6 |
| SQLiteDriver.kt | 0 | 5 |

**Complexity Issues Found**:
| File | Lines | Issue |
|------|-------|-------|
| App.kt | 1020 | **CRITICAL** - God class, multiple responsibilities |
| ConnectionManagerScreen.kt | 606 | Large, needs extraction |
| SQLiteDriver.kt | 409 | Consider extraction |
| PostgreSQLDriver.kt | 349 | Consider extraction |
| FavoritesScreen.kt | 279 | Acceptable |
| ResultGrid.kt | 264 | Acceptable |

**Decision Trees**:
- App.kt: 5 `when` statements
- QueryExecutor.kt: 2 `when` statements
- PreferencesBackedConnectionProfileRepository.kt: 2 `when` statements

### User Constraints (Verbatim)
- "Avoid running the same check multiple times" - NO duplicate verification
- "Remember we are professional, senior engineers" - High quality
- Oracle review after EACH task
- Commit after EACH task
- Document everything

---

## Work Objectives

### Core Objective
Transform DB Eagle codebase to follow clean code principles: explicit imports, single responsibility, minimal complexity, and zero dead code.

### Concrete Deliverables
- 10 files with resolved wildcard imports
- 4 files with resolved inline package references
- App.kt split into ≤300 line focused modules
- ConnectionManagerScreen.kt refactored
- Documented decision log in `.sisyphus/evidence/decisions.md`

### Definition of Done
- [ ] `./gradlew spotlessCheck` passes (no format issues)
- [ ] `./gradlew detekt` passes (no new issues)
- [ ] `./gradlew build test` passes
- [ ] Zero wildcard imports in production code
- [ ] Zero inline package references
- [ ] No file >400 lines (except drivers which have natural cohesion)

### Must Have
- Oracle reviews after EACH implementation task
- Git commits after EACH task
- Documentation of decisions made

### Must NOT Have (Guardrails)
- **NO duplicate command runs** - run `spotlessCheck`, `detekt`, `build test` only ONCE per task
- **NO over-abstraction** - don't extract unless clear benefit
- **NO premature optimization** - focus on readability first
- **NO breaking changes** - all public APIs must remain stable
- **NO test code changes** - wildcard imports in tests are acceptable (common practice)

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: YES (bun test equivalent: `./gradlew test`)
- **Automated tests**: Tests-after (verify existing tests pass, don't add new ones unless needed)
- **Framework**: JUnit 5 + kotlin.test

### QA Policy
Every task includes agent-executed verification:
- **Build verification**: `./gradlew build test` (once per task)
- **Format check**: `./gradlew spotlessCheck` (once per task)
- **Static analysis**: `./gradlew detekt` (once per task)
- Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.txt`

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately — Import Cleanup, MAX PARALLEL):
├── Task 1: Fix wildcard imports - App.kt [quick]
├── Task 2: Fix wildcard imports - UI screens batch 1 (ResultGrid, SchemaTree, SQLEditor) [quick]
├── Task 3: Fix wildcard imports - UI screens batch 2 (History, Favorites, Settings) [quick]
├── Task 4: Fix wildcard imports - UI dialogs (Connection, Export, ConnectionManager) [quick]
├── Task 5: Fix inline references - SessionViewModel.kt [quick]
├── Task 6: Fix inline references - PostgreSQLDriver.kt [quick]
└── Task 7: Fix inline references - SQLiteDriver.kt [quick]

Wave 2 (After Wave 1 — God Class Extraction):
├── Task 8: Extract App.kt navigation logic → NavigationManager.kt [deep]
├── Task 9: Extract App.kt session management → SessionManager.kt [deep]
├── Task 10: Extract App.kt UI state → AppState.kt [deep]
└── Task 11: Refactor ConnectionManagerScreen.kt - extract dialogs [unspecified-high]

Wave 3 (After Wave 2 — Complexity Reduction):
├── Task 12: Simplify App.kt main composable (remaining code) [unspecified-high]
├── Task 13: Review and simplify QueryExecutor when statements [unspecified-high]
└── Task 14: Review PreferencesBackedConnectionProfileRepository complexity [quick]

Wave 4 (After Wave 3 — Abstraction & Dead Code):
├── Task 15: Analyze and remove dead code [deep]
├── Task 16: Document decisions and create gotchas file [writing]
└── Task 17: Final code review and cleanup [unspecified-high]

Wave FINAL (After ALL tasks — Independent Review):
├── Task F1: Plan compliance audit [oracle]
├── Task F2: Code quality review [unspecified-high]
├── Task F3: Full application QA [unspecified-high]
└── Task F4: Scope fidelity check [deep]

Critical Path: Tasks 1-7 → Tasks 8-11 → Tasks 12-14 → Tasks 15-17 → F1-F4
Parallel Speedup: ~60% faster than sequential
Max Concurrent: 7 (Wave 1)
```

### Dependency Matrix
| Task | Depends On | Blocks |
|------|------------|--------|
| 1-7 | None | 8-11 |
| 8-11 | 1-7 | 12-14 |
| 12-14 | 8-11 | 15-17 |
| 15-17 | 12-14 | F1-F4 |
| F1-F4 | 15-17 | None |

### Agent Dispatch Summary
| Wave | Tasks | Categories |
|------|-------|------------|
| 1 | 7 | All `quick` |
| 2 | 4 | `deep` (×3), `unspecified-high` (×1) |
| 3 | 3 | `unspecified-high` (×2), `quick` (×1) |
| 4 | 3 | `deep` (×1), `writing` (×1), `unspecified-high` (×1) |
| FINAL | 4 | `oracle`, `unspecified-high` (×2), `deep` |

---

## TODOs

- [x] 1. Fix wildcard imports in App.kt

  **What to do**:
  - Replace 3 wildcard imports with explicit imports:
    - Line 4: `import androidx.compose.foundation.layout.*` → explicit imports for used classes
    - Line 9: `import androidx.compose.material3.*` → explicit imports for used classes
    - Line 10: `import androidx.compose.runtime.*` → explicit imports for used classes
  - Analyze actual usage in the file to determine which specific classes are needed
  - Run `./gradlew spotlessApply` to auto-format if needed

  **Must NOT do**:
  - Do NOT modify any logic or functionality
  - Do NOT touch test files
  - Do NOT run verification commands more than once

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple find-and-replace task with clear scope
  - **Skills**: `[]`
    - No special skills needed - standard Kotlin editing

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2-7)
  - **Blocks**: Tasks 8-11 (god class extraction needs clean imports first)
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `app/src/main/kotlin/com/dbeagle/App.kt:1-50` - Current imports to analyze
    - `core/src/main/kotlin/com/dbeagle/` - Clean module with no wildcards (pattern to follow)
  - **Why Each Reference Matters**:
    - App.kt imports section shows current wildcards and context
    - Core module demonstrates the explicit import style to match

  **Acceptance Criteria**:
  - [ ] Zero wildcard imports in App.kt
  - [ ] `./gradlew spotlessCheck` passes
  - [ ] `./gradlew build` passes (app still compiles)

  **QA Scenarios**:
  ```
  Scenario: Verify no wildcard imports remain
    Tool: Bash (grep)
    Preconditions: Task complete, imports replaced
    Steps:
      1. Run: grep -n "import .*\.\*" app/src/main/kotlin/com/dbeagle/App.kt
    Expected Result: No matches found (exit code 1, empty output)
    Failure Indicators: Any line containing "import xxx.*"
    Evidence: .sisyphus/evidence/task-1-no-wildcards.txt

  Scenario: Verify build succeeds
    Tool: Bash (gradle)
    Preconditions: Imports changed
    Steps:
      1. Run: ./gradlew :app:compileKotlin --no-daemon
    Expected Result: BUILD SUCCESSFUL
    Failure Indicators: "Unresolved reference" errors
    Evidence: .sisyphus/evidence/task-1-build.txt
  ```

  **Oracle Review**: YES - after implementation, Oracle reviews changes
  **Commit**: YES
  - Message: `refactor(imports): replace wildcard imports with explicit imports in App.kt`
  - Files: `app/src/main/kotlin/com/dbeagle/App.kt`

---

- [x] 2. Fix wildcard imports in UI screens batch 1 (ResultGrid, SchemaTree, SQLEditor)

  **What to do**:
  - ResultGrid.kt: Replace 5 wildcard imports (lines 3, 5, 9, 10, 16)
  - SchemaTree.kt: Replace 5 wildcard imports (lines 5, 10, 11, 12, 13)
  - SQLEditor.kt: Replace 4 wildcard imports (lines 5, 10, 11, 14)
  - Analyze actual usage in each file to determine specific imports needed
  - Run `./gradlew spotlessApply` after changes

  **Must NOT do**:
  - Do NOT modify any logic or functionality
  - Do NOT touch test files
  - Do NOT run verification commands more than once per file

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Repetitive find-and-replace across 3 similar files
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3-7)
  - **Blocks**: Tasks 8-11
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `app/src/main/kotlin/com/dbeagle/ui/ResultGrid.kt:1-20` - Current imports
    - `app/src/main/kotlin/com/dbeagle/ui/SchemaTree.kt:1-20` - Current imports
    - `app/src/main/kotlin/com/dbeagle/ui/SQLEditor.kt:1-20` - Current imports

  **Acceptance Criteria**:
  - [ ] Zero wildcard imports in ResultGrid.kt, SchemaTree.kt, SQLEditor.kt
  - [ ] `./gradlew spotlessCheck` passes
  - [ ] `./gradlew :app:compileKotlin` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no wildcard imports in batch 1 files
    Tool: Bash (grep)
    Preconditions: All 3 files updated
    Steps:
      1. Run: grep -l "import .*\.\*" app/src/main/kotlin/com/dbeagle/ui/ResultGrid.kt app/src/main/kotlin/com/dbeagle/ui/SchemaTree.kt app/src/main/kotlin/com/dbeagle/ui/SQLEditor.kt
    Expected Result: No matches (exit code 1)
    Failure Indicators: Any filename returned
    Evidence: .sisyphus/evidence/task-2-no-wildcards.txt

  Scenario: Verify compilation
    Tool: Bash (gradle)
    Steps:
      1. Run: ./gradlew :app:compileKotlin --no-daemon
    Expected Result: BUILD SUCCESSFUL
    Evidence: .sisyphus/evidence/task-2-build.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(imports): replace wildcard imports in ResultGrid, SchemaTree, SQLEditor`
  - Files: `app/src/main/kotlin/com/dbeagle/ui/ResultGrid.kt`, `app/src/main/kotlin/com/dbeagle/ui/SchemaTree.kt`, `app/src/main/kotlin/com/dbeagle/ui/SQLEditor.kt`

---

- [x] 3. Fix wildcard imports in UI screens batch 2 (HistoryScreen, FavoritesScreen, SettingsScreen)

  **What to do**:
  - HistoryScreen.kt: Replace 4 wildcard imports (lines 4, 7, 8, 17)
    - Note: `java.util.*` on line 17 should become `import java.text.SimpleDateFormat`
  - FavoritesScreen.kt: Replace 4 wildcard imports (lines 4, 10, 11, 20)
    - Note: `java.util.*` on line 20 should become `import java.text.SimpleDateFormat`
  - SettingsScreen.kt: Replace 3 wildcard imports (lines 3, 4, 5)

  **Must NOT do**:
  - Do NOT modify any logic
  - Do NOT touch test files

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 8-11
  - **Blocked By**: None

  **References**:
  - `app/src/main/kotlin/com/dbeagle/ui/HistoryScreen.kt:1-20`
  - `app/src/main/kotlin/com/dbeagle/ui/FavoritesScreen.kt:1-25`
  - `app/src/main/kotlin/com/dbeagle/ui/SettingsScreen.kt:1-10`

  **Acceptance Criteria**:
  - [ ] Zero wildcard imports in HistoryScreen.kt, FavoritesScreen.kt, SettingsScreen.kt
  - [ ] `./gradlew :app:compileKotlin` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no wildcard imports in batch 2
    Tool: Bash (grep)
    Steps:
      1. Run: grep -c "import .*\.\*" app/src/main/kotlin/com/dbeagle/ui/HistoryScreen.kt app/src/main/kotlin/com/dbeagle/ui/FavoritesScreen.kt app/src/main/kotlin/com/dbeagle/ui/SettingsScreen.kt || true
    Expected Result: All counts are 0 or files not found in grep results
    Evidence: .sisyphus/evidence/task-3-no-wildcards.txt

  Scenario: SimpleDateFormat import correct
    Tool: Bash (grep)
    Steps:
      1. Run: grep "import java.text.SimpleDateFormat" app/src/main/kotlin/com/dbeagle/ui/HistoryScreen.kt app/src/main/kotlin/com/dbeagle/ui/FavoritesScreen.kt
    Expected Result: Both files contain the import
    Evidence: .sisyphus/evidence/task-3-simpledateformat.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(imports): replace wildcard imports in HistoryScreen, FavoritesScreen, SettingsScreen`

---

- [x] 4. Fix wildcard imports in UI dialogs (ConnectionDialog, ExportDialog, ConnectionManagerScreen)

  **What to do**:
  - ConnectionDialog.kt: Replace 3 wildcard imports (lines 3, 4, 5)
  - ExportDialog.kt: Replace 3 wildcard imports (lines 3, 4, 5)
  - ConnectionManagerScreen.kt: Replace 2 wildcard imports (lines 21, 22)

  **Must NOT do**:
  - Do NOT modify any logic
  - Do NOT touch test files

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 8-11
  - **Blocked By**: None

  **References**:
  - `app/src/main/kotlin/com/dbeagle/ui/ConnectionDialog.kt:1-10`
  - `app/src/main/kotlin/com/dbeagle/ui/ExportDialog.kt:1-10`
  - `app/src/main/kotlin/com/dbeagle/ui/ConnectionManagerScreen.kt:1-30`

  **Acceptance Criteria**:
  - [ ] Zero wildcard imports in all 3 dialog files
  - [ ] `./gradlew :app:compileKotlin` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no wildcard imports in dialogs
    Tool: Bash (grep)
    Steps:
      1. Run: grep -l "import .*\.\*" app/src/main/kotlin/com/dbeagle/ui/ConnectionDialog.kt app/src/main/kotlin/com/dbeagle/ui/ExportDialog.kt app/src/main/kotlin/com/dbeagle/ui/ConnectionManagerScreen.kt 2>/dev/null || echo "CLEAN"
    Expected Result: Output is "CLEAN"
    Evidence: .sisyphus/evidence/task-4-no-wildcards.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(imports): replace wildcard imports in ConnectionDialog, ExportDialog, ConnectionManagerScreen`

---

- [x] 5. Fix inline package references in SessionViewModel.kt

  **What to do**:
  - Line 99: Replace `kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)` with `withContext(Dispatchers.IO)`
  - Line 107: Same fix as line 99
  - Add missing imports: `import kotlinx.coroutines.withContext` and ensure `import kotlinx.coroutines.Dispatchers` exists

  **Must NOT do**:
  - Do NOT modify any logic
  - Do NOT change coroutine behavior

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 8-11
  - **Blocked By**: None

  **References**:
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt:90-120` - Lines with inline refs

  **Acceptance Criteria**:
  - [ ] No inline `kotlinx.coroutines` references in SessionViewModel.kt
  - [ ] `./gradlew :app:compileKotlin` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no inline kotlinx references
    Tool: Bash (grep)
    Steps:
      1. Run: grep -n "kotlinx\.coroutines\." app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt | grep -v "^[0-9]*:import"
    Expected Result: No matches (only import lines should have kotlinx.coroutines)
    Evidence: .sisyphus/evidence/task-5-no-inline-kotlinx.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(imports): replace inline kotlinx references in SessionViewModel`

---

- [x] 6. Fix inline package references in PostgreSQLDriver.kt

  **What to do**:
  - Line 151: Replace `java.sql.DatabaseMetaData.columnNoNulls` with proper import
  - Lines 315, 317: Replace `java.io.PrintWriter` with import
  - Line 323: Replace `java.util.logging.Logger` with import
  - Line 326: Replace `java.sql.SQLFeatureNotSupportedException` with import
  - Add all required imports at the top of the file

  **Must NOT do**:
  - Do NOT modify any SQL logic or driver behavior

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 8-11
  - **Blocked By**: None

  **References**:
  - `data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt:140-160` - DatabaseMetaData usage
  - `data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt:310-330` - PrintWriter and Logger

  **Acceptance Criteria**:
  - [ ] No inline `java.` references (except in import block) in PostgreSQLDriver.kt
  - [ ] `./gradlew :data:compileKotlin` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no inline java references
    Tool: Bash (grep)
    Steps:
      1. Run: grep -nE "(java\.sql\.|java\.io\.|java\.util\.logging\.)" data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt | grep -v "^[0-9]*:import"
    Expected Result: No matches
    Evidence: .sisyphus/evidence/task-6-no-inline-java.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(imports): replace inline java references in PostgreSQLDriver`

---

- [x] 7. Fix inline package references in SQLiteDriver.kt

  **What to do**:
  - Lines 362, 364: Replace `java.io.PrintWriter` with import
  - Line 370: Replace `java.util.logging.Logger` with import
  - Line 373: Replace `java.sql.SQLFeatureNotSupportedException` with import
  - Add all required imports at the top of the file

  **Must NOT do**:
  - Do NOT modify any SQL logic or driver behavior

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 8-11
  - **Blocked By**: None

  **References**:
  - `data/src/main/kotlin/com/dbeagle/driver/SQLiteDriver.kt:355-380` - PrintWriter and Logger area

  **Acceptance Criteria**:
  - [ ] No inline `java.` references (except in import block) in SQLiteDriver.kt
  - [ ] `./gradlew :data:compileKotlin` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no inline java references
    Tool: Bash (grep)
    Steps:
      1. Run: grep -nE "(java\.sql\.|java\.io\.|java\.util\.logging\.)" data/src/main/kotlin/com/dbeagle/driver/SQLiteDriver.kt | grep -v "^[0-9]*:import"
    Expected Result: No matches
    Evidence: .sisyphus/evidence/task-7-no-inline-java.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(imports): replace inline java references in SQLiteDriver`

---

- [x] 8. Extract navigation logic from App.kt to NavigationManager.kt

  **What to do**:
  - Create new file: `app/src/main/kotlin/com/dbeagle/navigation/NavigationManager.kt`
  - Extract `NavigationTab` enum and navigation-related state management
  - Extract tab switching logic and navigation state
  - Keep App.kt as thin orchestration layer
  - Target: NavigationManager.kt ≤150 lines

  **Must NOT do**:
  - Do NOT change navigation behavior
  - Do NOT break tab switching functionality
  - Do NOT over-abstract - keep it simple

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Requires careful analysis of App.kt structure and dependencies
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 9, 10, 11)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 12-14
  - **Blocked By**: Tasks 1-7 (imports must be clean first)

  **References**:
  - `app/src/main/kotlin/com/dbeagle/App.kt:76-84` - NavigationTab enum
  - `app/src/main/kotlin/com/dbeagle/App.kt:96` - selectedTab state

  **Acceptance Criteria**:
  - [ ] NavigationManager.kt exists and compiles
  - [ ] App.kt uses NavigationManager for navigation
  - [ ] All 6 tabs still work correctly
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify NavigationManager exists and is used
    Tool: Bash
    Steps:
      1. Run: ls -la app/src/main/kotlin/com/dbeagle/navigation/NavigationManager.kt
      2. Run: grep -l "NavigationManager" app/src/main/kotlin/com/dbeagle/App.kt
    Expected Result: File exists, App.kt imports/uses NavigationManager
    Evidence: .sisyphus/evidence/task-8-navigation-manager.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(app): extract navigation logic to NavigationManager`

---

- [x] 9. Extract session management from App.kt to SessionManager.kt

  **What to do**:
  - Create new file: `app/src/main/kotlin/com/dbeagle/session/SessionManager.kt`
  - Extract session-related state (SessionViewModel usage, active sessions)
  - Extract session creation, switching, closing logic
  - Keep App.kt focused on composition
  - Target: SessionManager.kt ≤200 lines

  **Must NOT do**:
  - Do NOT change session behavior
  - Do NOT modify SessionViewModel internals
  - Do NOT break multi-session functionality

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 8, 10, 11)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 12-14
  - **Blocked By**: Tasks 1-7

  **References**:
  - `app/src/main/kotlin/com/dbeagle/App.kt:99-100` - SessionViewModel usage
  - `app/src/main/kotlin/com/dbeagle/session/SessionViewModel.kt` - Existing session logic

  **Acceptance Criteria**:
  - [ ] SessionManager.kt exists and compiles
  - [ ] Session operations work correctly (create, switch, close)
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify SessionManager integration
    Tool: Bash
    Steps:
      1. Run: ls -la app/src/main/kotlin/com/dbeagle/session/SessionManager.kt
      2. Run: grep "SessionManager" app/src/main/kotlin/com/dbeagle/App.kt
    Expected Result: File exists, App.kt uses SessionManager
    Evidence: .sisyphus/evidence/task-9-session-manager.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(app): extract session management to SessionManager`

---

- [x] 10. Extract UI state from App.kt to AppState.kt

  **What to do**:
  - Create new file: `app/src/main/kotlin/com/dbeagle/state/AppState.kt`
  - Extract UI state variables (statusText, dialog visibility, etc.)
  - Create clean state holder class with remember-based state
  - Keep App.kt as pure composition
  - Target: AppState.kt ≤150 lines

  **Must NOT do**:
  - Do NOT change UI behavior
  - Do NOT break dialog show/hide functionality

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 8, 9, 11)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 12-14
  - **Blocked By**: Tasks 1-7

  **References**:
  - `app/src/main/kotlin/com/dbeagle/App.kt:97` - statusText state

  **Acceptance Criteria**:
  - [ ] AppState.kt exists and compiles
  - [ ] All UI state properly managed
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify AppState integration
    Tool: Bash
    Steps:
      1. Run: ls -la app/src/main/kotlin/com/dbeagle/state/AppState.kt
      2. Run: grep "AppState" app/src/main/kotlin/com/dbeagle/App.kt
    Expected Result: File exists, App.kt uses AppState
    Evidence: .sisyphus/evidence/task-10-app-state.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(app): extract UI state to AppState`

---

- [x] 11. Refactor ConnectionManagerScreen.kt - extract dialog components

  **What to do**:
  - Identify dialog components embedded in ConnectionManagerScreen.kt (606 lines)
  - Extract reusable dialog components to separate files if >100 lines each
  - Consider: EditConnectionDialog, DeleteConfirmDialog, etc.
  - Target: ConnectionManagerScreen.kt ≤350 lines

  **Must NOT do**:
  - Do NOT change dialog behavior
  - Do NOT break connection management functionality

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 8, 9, 10)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 12-14
  - **Blocked By**: Tasks 1-7

  **References**:
  - `app/src/main/kotlin/com/dbeagle/ui/ConnectionManagerScreen.kt` - Full file analysis needed

  **Acceptance Criteria**:
  - [ ] ConnectionManagerScreen.kt ≤350 lines
  - [ ] Extracted dialogs compile and work
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify line count reduction
    Tool: Bash (wc)
    Steps:
      1. Run: wc -l app/src/main/kotlin/com/dbeagle/ui/ConnectionManagerScreen.kt
    Expected Result: ≤350 lines
    Evidence: .sisyphus/evidence/task-11-line-count.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(ui): extract dialog components from ConnectionManagerScreen`

---

- [x] 12. Simplify App.kt main composable

  **What to do**:
  - After extractions (Tasks 8-10), review remaining App.kt code
  - Simplify main composable function structure
  - Ensure App.kt is ≤400 lines (ideally ≤300)
  - Remove any dead code left from extractions
  - Improve readability

  **Must NOT do**:
  - Do NOT change any behavior
  - Do NOT add new abstractions

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 13, 14)
  - **Parallel Group**: Wave 3
  - **Blocks**: Tasks 15-17
  - **Blocked By**: Tasks 8-11

  **References**:
  - `app/src/main/kotlin/com/dbeagle/App.kt` - Post-extraction state

  **Acceptance Criteria**:
  - [ ] App.kt ≤400 lines
  - [ ] No dead code from extractions
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify App.kt size
    Tool: Bash (wc)
    Steps:
      1. Run: wc -l app/src/main/kotlin/com/dbeagle/App.kt
    Expected Result: ≤400 lines
    Evidence: .sisyphus/evidence/task-12-app-size.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor(app): simplify App.kt main composable`

---

- [x] 13. Review and simplify QueryExecutor when statements

  **What to do**:
  - Analyze 2 when statements in QueryExecutor.kt (121 lines)
  - Determine if when statements are necessary or can be simplified
  - If >5 branches, consider strategy pattern or map-based dispatch
  - Document decision if kept as-is (acceptable for small enums)

  **Must NOT do**:
  - Do NOT change query execution behavior
  - Do NOT over-engineer if when statements are appropriate

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 12, 14)
  - **Parallel Group**: Wave 3
  - **Blocks**: Tasks 15-17
  - **Blocked By**: Tasks 8-11

  **References**:
  - `core/src/main/kotlin/com/dbeagle/query/QueryExecutor.kt` - When statements

  **Acceptance Criteria**:
  - [ ] When statements reviewed and documented
  - [ ] Simplified if >5 branches, or documented rationale for keeping
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify when statement analysis complete
    Tool: Bash (grep)
    Steps:
      1. Run: grep -c "when" core/src/main/kotlin/com/dbeagle/query/QueryExecutor.kt
    Expected Result: Count documented in evidence
    Evidence: .sisyphus/evidence/task-13-when-analysis.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES (if changes made)
  - Message: `refactor(query): simplify QueryExecutor decision trees`

---

- [x] 14. Review PreferencesBackedConnectionProfileRepository complexity

  **What to do**:
  - Analyze 2 when statements in file (101 lines total)
  - File is already small - likely acceptable
  - Document decision to keep or simplify
  - Minor cleanup only if obvious improvements

  **Must NOT do**:
  - Do NOT change persistence behavior
  - Do NOT over-engineer

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 12, 13)
  - **Parallel Group**: Wave 3
  - **Blocks**: Tasks 15-17
  - **Blocked By**: Tasks 8-11

  **References**:
  - `core/src/main/kotlin/com/dbeagle/profile/PreferencesBackedConnectionProfileRepository.kt`

  **Acceptance Criteria**:
  - [ ] Complexity reviewed and documented
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify review complete
    Tool: Bash (wc)
    Steps:
      1. Run: wc -l core/src/main/kotlin/com/dbeagle/profile/PreferencesBackedConnectionProfileRepository.kt
    Expected Result: Line count documented
    Evidence: .sisyphus/evidence/task-14-preferences-review.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES (if changes made)
  - Message: `refactor(profile): review ConnectionProfileRepository complexity`

---

- [x] 15. Analyze and remove dead code

  **What to do**:
  - Run static analysis to find unused functions, classes, parameters
  - Check for commented-out code blocks
  - Check for unreachable code paths
  - Remove confirmed dead code
  - Document any code kept with rationale

  **Must NOT do**:
  - Do NOT remove code used via reflection or DI
  - Do NOT remove public API methods (may be used externally)
  - Be conservative - when in doubt, keep it

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 16, 17)
  - **Parallel Group**: Wave 4
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 12-14

  **References**:
  - All source files in `app/`, `core/`, `data/`
  - Use LSP find_references to verify usage

  **Acceptance Criteria**:
  - [ ] Dead code analysis complete
  - [ ] Confirmed dead code removed
  - [ ] `./gradlew build test` passes

  **QA Scenarios**:
  ```
  Scenario: Verify no obvious dead code
    Tool: Bash (gradle)
    Steps:
      1. Run: ./gradlew detekt --no-daemon
    Expected Result: No "unused" warnings in report
    Evidence: .sisyphus/evidence/task-15-dead-code.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `refactor: remove dead code and unused resources`

---

- [x] 16. Document decisions and create gotchas file

  **What to do**:
  - Create `.sisyphus/evidence/decisions.md` with all decisions made
  - Document:
    - Why certain when statements were kept
    - Why certain files weren't split further
    - Any gotchas discovered during refactoring
    - Blockers encountered and how resolved
    - Future improvement suggestions

  **Must NOT do**:
  - Do NOT include sensitive information
  - Do NOT duplicate code comments

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 15, 17)
  - **Parallel Group**: Wave 4
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 12-14

  **References**:
  - All evidence files from previous tasks
  - Commit messages for context

  **Acceptance Criteria**:
  - [ ] `.sisyphus/evidence/decisions.md` exists
  - [ ] Contains all major decisions
  - [ ] Readable and useful for future reference

  **QA Scenarios**:
  ```
  Scenario: Verify decisions.md exists and has content
    Tool: Bash (wc)
    Steps:
      1. Run: wc -l .sisyphus/evidence/decisions.md
    Expected Result: >20 lines of documented decisions
    Evidence: .sisyphus/evidence/task-16-decisions-doc.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES
  - Message: `docs: document refactoring decisions and gotchas`

---

- [x] 17. Final code review and cleanup

  **What to do**:
  - Run full verification: `./gradlew clean build test detekt spotlessCheck`
  - Fix any remaining issues
  - Ensure all acceptance criteria met
  - Final review of all changed files
  - Verify no regressions

  **Must NOT do**:
  - Do NOT introduce new features
  - Do NOT change scope

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 15, 16)
  - **Parallel Group**: Wave 4
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 12-14

  **References**:
  - All modified files
  - All evidence files

  **Acceptance Criteria**:
  - [ ] `./gradlew clean build test detekt spotlessCheck` passes
  - [ ] All previous task acceptance criteria verified

  **QA Scenarios**:
  ```
  Scenario: Full verification suite
    Tool: Bash (gradle)
    Steps:
      1. Run: ./gradlew clean build test detekt spotlessCheck --no-daemon
    Expected Result: BUILD SUCCESSFUL for all tasks
    Evidence: .sisyphus/evidence/task-17-final-verification.txt

  Scenario: No wildcard imports anywhere
    Tool: Bash (grep)
    Steps:
      1. Run: grep -r "import .*\.\*" app/src/main/kotlin/ core/src/main/kotlin/ data/src/main/kotlin/ || echo "CLEAN"
    Expected Result: "CLEAN" (no matches)
    Evidence: .sisyphus/evidence/task-17-no-wildcards-final.txt
  ```

  **Oracle Review**: YES
  **Commit**: YES (if any fixes needed)
  - Message: `refactor: final code quality cleanup`

---

## Final Verification Wave

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists. For each "Must NOT Have": search codebase for forbidden patterns. Check evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew build test detekt spotlessCheck`. Review all changed files for: wildcard imports remaining, inline package refs, files >400 lines. Check for AI slop patterns.
  Output: `Build [PASS/FAIL] | Detekt [PASS/FAIL] | Spotless [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Full Application QA** — `unspecified-high`
  Start the application with `./gradlew run`. Verify all tabs work (Connections, Query Editor, Schema Browser, Favorites, History, Settings). Test connecting to a database, running queries, and viewing results.
  Output: `App Launch [PASS/FAIL] | Tabs [N/N working] | Core Features [N/N] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual git diff. Verify 1:1 correspondence. Check "Must NOT do" compliance. Detect scope creep. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Scope Creep [CLEAN/N issues] | VERDICT`

---

## Commit Strategy

Each task commits independently with format:
```
refactor(scope): description

- Bullet point of key changes
```

Example commits:
- `refactor(imports): replace wildcard imports with explicit imports in App.kt`
- `refactor(app): extract navigation logic to NavigationManager`
- `refactor(app): extract session management to SessionManager`

---

## Success Criteria

### Verification Commands
```bash
./gradlew spotlessCheck  # Expected: BUILD SUCCESSFUL
./gradlew detekt         # Expected: BUILD SUCCESSFUL (no new issues)
./gradlew build test     # Expected: BUILD SUCCESSFUL, all tests pass
grep -r "import .*\.\*" app/src/main/kotlin/  # Expected: no matches
```

### Final Checklist
- [ ] Zero wildcard imports in production code
- [ ] Zero inline package references
- [ ] App.kt ≤400 lines
- [ ] ConnectionManagerScreen.kt ≤400 lines
- [ ] All tests pass
- [ ] Decisions documented in `.sisyphus/evidence/decisions.md`
