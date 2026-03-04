# DB Eagle - Full-Featured Database Management Tool

## TL;DR

> **Quick Summary**: Build a cross-platform desktop database manager (Compose Multiplatform + Kotlin) targeting developers, with PostgreSQL and SQLite support, featuring multi-connection management, SQL editor, schema browser, data editing, and ER diagrams. Freemium model with premium features architecture-ready.
> 
> **Deliverables**:
> - Compose Desktop application (macOS, Windows, Linux)
> - PostgreSQL + SQLite database support with plugin architecture
> - SQL query editor with syntax highlighting and execution
> - Schema browser with multi-connection support
> - Data viewer/editor with inline CRUD operations
> - Query history and favorites management
> - ER diagram visualization
> - Secure credential storage (AES-GCM encryption)
> - Basic export (CSV, JSON, SQL)
> - Native installers (DMG, MSI, DEB)
> 
> **Estimated Effort**: XL (aggressive 2-3 month timeline)
> **Parallel Execution**: YES - 8 waves with max 7 concurrent tasks
> **Critical Path**: Project setup → Core abstractions → Connection manager → SQL editor → Schema browser → Integration → Packaging

---

## Context

### Original Request
"Build a desktop tool to manage db instances, starting with PostgreSQL, with other database managers added later. Multiplatform (Mac, Linux, Windows), preferably desktop app. Kotlin expert, consider it as an option."

### Interview Summary
**Key Discussions**:
- **Scope**: Full-featured manager (not lightweight query tool) targeting developers first, DBA features later
- **Timeline**: Aggressive 2-3 months for MVP
- **Databases**: PostgreSQL primary, SQLite secondary, extensible for MySQL/SQL Server/MongoDB
- **Distribution**: GitHub releases + website downloads, auto-update deferred post-MVP
- **Monetization**: Freemium - free core features, premium cloud/AI features
- **Tech Stack**: Compose Multiplatform Desktop + Kotlin (leveraging user's expertise)
- **Testing**: kotlin.test framework
- **Premium Features** (architecture-ready, not implemented): Cloud sync, team collaboration, performance analysis, AI-powered query assistance

**Research Findings**:
- **Compose Multiplatform**: Production-ready (1.10.1 stable), requires JDK 17+, no cross-compilation (build per OS)
- **Exposed ORM**: Multi-DB abstraction, supports JDBC + R2DBC, HikariCP for connection pooling
- **DBeaver Pattern**: Plugin-per-database architecture (100+ DBs supported) with capability discovery
- **pgAdmin Approach**: Web UI + native wrapper, modern React + Python Flask
- **Local Reference**: JEAAP POS project demonstrates Compose Desktop production patterns (Koin DI, credential encryption, plugin architecture)

### Metis Review
**Identified Gaps** (addressed):

**1. Scope Creep Risks** (GUARDRAILS APPLIED):
- ❌ NO visual query builder in MVP (text SQL only)
- ❌ NO data import in MVP (export only: CSV, JSON, SQL)
- ❌ NO migrations/schema versioning tools in MVP
- ❌ NO SSH tunneling in MVP (direct connections only)
- ❌ NO performance monitoring dashboards in MVP
- ❌ NO stored procedure debugging in MVP
- ❌ NO database backups/restore in MVP
- ❌ NO user/role management UI in MVP
- ❌ NO dark/light theme switching in MVP (single theme)
- ❌ NO plugin marketplace in MVP (built-in drivers only)

**2. Technical Risks** (MITIGATIONS):
- **Cross-platform builds**: Set up CI/CD early (GitHub Actions with macOS/Windows/Linux runners)
- **Connection pooling complexity**: Use battle-tested HikariCP, don't build custom
- **Credential security**: Use proven pattern from JEAAP POS (AES-GCM + Java Preferences)
- **Large result sets**: Implement pagination from day 1 (max 1000 rows default)
- **SQL syntax highlighting**: Use existing libraries (KodeView or similar), don't build parser

**3. Aggressive Timeline Constraints**:
- **UI Polish**: Functional > Beautiful - minimal styling, focus on working features
- **Error Handling**: Basic only - show errors, don't build sophisticated retry/recovery
- **Documentation**: Inline comments only - no separate user manual in MVP
- **Localization**: English only - no i18n in MVP
- **Accessibility**: Not prioritized - standard Compose widgets only

**4. Architecture Decisions Validated**:
- ✅ Exposed + HikariCP correct for external DB connections (vs SQLDelight for embedded)
- ✅ Koin DI proven in local reference project (JEAAP POS)
- ✅ Plugin architecture essential for multi-DB support
- ✅ Separate connection profiles from active connections (security + UX)

**5. Missing Specs Resolved**:
- **Result set limit**: Default 1000 rows, configurable per connection
- **Connection timeout**: 30 seconds default
- **Query timeout**: 60 seconds default
- **Max concurrent connections**: 10 per database type
- **Credential storage**: AES-GCM with user-provided master password
- **SQL highlighting**: Syntax only, no semantic validation
- **ER diagram**: Limited to foreign key relationships (no inferred relationships)

---

## Work Objectives

### Core Objective
Build a production-ready, cross-platform desktop database management tool (db-eagle) that empowers developers to efficiently manage PostgreSQL and SQLite databases through an intuitive Compose Multiplatform UI, with extensible architecture supporting future database types and premium features.

### Concrete Deliverables
- **Application**: `db-eagle` desktop app installable on macOS, Windows, Linux
- **Installers**: `db-eagle-{version}-{platform}.{dmg,msi,deb}`
- **Connection Management**: Encrypted profile storage, multi-connection support
- **SQL Editor**: Syntax-highlighted query execution with result streaming
- **Schema Browser**: Tree view of tables, views, indexes, columns with metadata
- **Data Editor**: Grid view with inline editing, pagination (1000 rows default)
- **Query Tools**: History persistence, favorites with tags
- **ER Diagrams**: Visual FK relationship rendering
- **Export**: CSV, JSON, SQL dump formats

### Definition of Done
- [ ] `./gradlew packageDmg` produces working macOS installer
- [ ] `./gradlew packageMsi` produces working Windows installer
- [ ] `./gradlew packageDeb` produces working Linux installer
- [ ] User can create PostgreSQL connection profile with encrypted credentials
- [ ] User can execute `SELECT * FROM users LIMIT 10` and see results in grid
- [ ] User can browse PostgreSQL schema (tables, columns, indexes) in tree view
- [ ] User can edit cell values in result grid and commit changes
- [ ] User can export query results to CSV/JSON/SQL formats
- [ ] User can save queries to favorites and retrieve from history
- [ ] User can view ER diagram for PostgreSQL database with FK relationships
- [ ] `./gradlew test` passes with >80% code coverage on core modules
- [ ] Application starts in <5 seconds on modern hardware
- [ ] Memory usage <500MB with 5 active connections

### Must Have
- Plugin architecture for database drivers (following DBeaver pattern)
- Encrypted credential storage using AES-GCM (following JEAAP POS pattern)
- Multi-connection support (multiple databases open simultaneously)
- Syntax-highlighted SQL editor with execution
- Schema browser with lazy loading
- Paginated result sets (prevent memory exhaustion)
- Inline data editing with transaction support
- Query history persistence across sessions
- Favorites with tagging/search
- ER diagram with FK relationships
- Export to CSV, JSON, SQL
- PostgreSQL driver (primary)
- SQLite driver (secondary)
- Connection pooling via HikariCP
- Koin dependency injection
- kotlin.test test suite with >80% coverage

### Must NOT Have (Guardrails)
- ❌ Visual query builder (text SQL only)
- ❌ Data import features (export only)
- ❌ Migration/versioning tools
- ❌ SSH tunneling (direct connections only)
- ❌ Performance monitoring dashboards
- ❌ Stored procedure debugging
- ❌ Database backup/restore UI
- ❌ User/role management UI
- ❌ Theme switching (single theme only)
- ❌ Plugin marketplace (built-in drivers only)
- ❌ Real-time collaboration features
- ❌ Cloud sync in MVP (architecture-ready only)
- ❌ AI features in MVP (architecture-ready only)
- ❌ Excessive animations or polish (functional over beautiful)
- ❌ Custom SQL parser (use libraries)
- ❌ Non-English localization
- ❌ Advanced accessibility features

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed. No exceptions.
> Acceptance criteria requiring "user manually tests/confirms" are FORBIDDEN.

### Test Decision
- **Infrastructure exists**: NO (greenfield project)
- **Automated tests**: TDD (test-first for core logic, test-after for UI components)
- **Framework**: kotlin.test
- **Test Levels**:
  - Unit tests: Business logic, data models, utilities (TDD)
  - Integration tests: Database drivers, connection pooling, query execution (TestContainers)
  - UI tests: Compose UI testing framework (test-after, manual QA scenarios)
  - E2E tests: Full user workflows via Compose UI test APIs

### QA Policy
Every task MUST include agent-executed QA scenarios (see TODO template below).
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Backend/Logic**: Use Bash (./gradlew test) — Run tests, assert pass/fail, capture reports
- **Database Integration**: Use Bash (docker + psql/sqlite3) — Start DB, run queries, verify data
- **UI Components**: Use Bash (./gradlew desktopTest) — Run Compose UI tests, capture output
- **Full Application**: Use interactive_bash (manual QA with screenshots) — Launch app, interact, verify behavior

---

## Execution Strategy

### Parallel Execution Waves

> Maximize throughput by grouping independent tasks into parallel waves.
> Each wave completes before the next begins.
> Target: 5-8 tasks per wave. Fewer than 3 per wave (except final) = under-splitting.

```
Wave 1 (Foundation - 7 tasks, start immediately):
├── Task 1: Project scaffolding + Gradle config [quick]
├── Task 2: Core domain models (Connection, Query, Schema types) [quick]
├── Task 3: Database driver abstraction interface [quick]
├── Task 4: Credential encryption module (AES-GCM) [unspecified-high]
├── Task 5: Koin DI setup + module structure [quick]
├── Task 6: kotlin.test infrastructure + TestContainers setup [quick]
└── Task 7: CI/CD workflow (GitHub Actions multi-OS builds) [unspecified-high]

Wave 2 (Database Layer - 6 tasks, after Wave 1):
├── Task 8: HikariCP connection pool wrapper [unspecified-high]
├── Task 9: PostgreSQL driver implementation (Exposed) [deep]
├── Task 10: SQLite driver implementation (Exposed) [deep]
├── Task 11: Driver registry + plugin loading [unspecified-high]
├── Task 12: Connection profile persistence (encrypted) [unspecified-high]
└── Task 13: Query execution engine with pagination [deep]

Wave 3 (UI Foundation - 7 tasks, after Wave 2):
├── Task 14: Compose Desktop main window scaffold [visual-engineering]
├── Task 15: Navigation structure (sidebar + content area) [visual-engineering]
├── Task 16: Connection manager UI (list, create, edit, delete) [visual-engineering]
├── Task 17: SQL editor component with syntax highlighting [visual-engineering]
├── Task 18: Result grid component with pagination [visual-engineering]
├── Task 19: Schema browser tree component [visual-engineering]
└── Task 20: Export dialog UI (format selection, file picker) [visual-engineering]

Wave 4 (Core Features Integration - 7 tasks, after Wave 3):
├── Task 21: Connection manager integration (UI ↔ DB layer) [deep]
├── Task 22: SQL editor integration (execute query → display results) [deep]
├── Task 23: Schema browser integration (load schema → tree view) [deep]
├── Task 24: Data editing logic (inline grid edits → SQL UPDATE) [deep]
├── Task 25: Query history persistence + UI [unspecified-high]
├── Task 26: Favorites system (save/load/tag queries) [unspecified-high]
└── Task 27: Export functionality (results → CSV/JSON/SQL files) [unspecified-high]

Wave 5 (Advanced Features - 5 tasks, after Wave 4):
├── Task 28: ER diagram generator (FK relationships) [deep]
├── Task 29: ER diagram renderer (Compose Canvas) [visual-engineering]
├── Task 30: Multi-connection session management [deep]
├── Task 31: Error handling + user feedback (toasts, dialogs) [unspecified-high]
└── Task 32: Application settings (theme, limits, timeouts) [unspecified-high]

Wave 6 (Polish & Optimization - 6 tasks, after Wave 5):
├── Task 33: Connection pooling optimization + leak detection [deep]
├── Task 34: UI responsiveness (async loading, progress indicators) [visual-engineering]
├── Task 35: Keyboard shortcuts + accessibility basics [visual-engineering]
├── Task 36: Memory profiling + large result set optimization [deep]
├── Task 37: Application icon + branding assets [visual-engineering]
└── Task 38: Crash reporting + logging infrastructure [unspecified-high]

Wave 7 (Packaging & Distribution - 5 tasks, after Wave 6):
├── Task 39: macOS packaging (DMG + signing) [unspecified-high]
├── Task 40: Windows packaging (MSI + signing) [unspecified-high]
├── Task 41: Linux packaging (DEB + RPM) [unspecified-high]
├── Task 42: GitHub release automation [git]
└── Task 43: Landing page + download links [writing]

Wave FINAL (Verification - 4 tasks, after ALL implementation):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA - full workflows (unspecified-high + playwright skill)
└── Task F4: Scope fidelity check (deep)

Critical Path: T1 → T3,T5 → T8,T9,T11 → T21 → T22 → T30 → T39-T41 → F1-F4
Parallel Speedup: ~75% faster than sequential
Max Concurrent: 7 (Waves 1, 3, 4)
```

### Dependency Matrix

**Wave 1** (Foundation):
- 1: — → 2,3,4,5,6,7 | All Wave 2+
- 2: — → 9,10,12,13 | All domain-dependent tasks
- 3: — → 9,10,11 | Driver implementations
- 4: — → 12 | Credential storage
- 5: — → 8,11,21+ | DI-dependent modules
- 6: — → All test tasks
- 7: — → 39-42 | CI/CD dependent tasks

**Wave 2** (Database Layer):
- 8: 5 → 9,10,13,21,22,23,30
- 9: 2,3,5,6 → 11,21,22,23,28
- 10: 2,3,5,6 → 11,21,22,23
- 11: 3,5 → 21,30
- 12: 2,4,5 → 16,21
- 13: 2,5,8 → 22,24,27

**Wave 3** (UI Foundation):
- 14: 1,5 → 15,16,17,18,19,20 | All UI
- 15: 14 → 16,17,18,19,20 | Layout-dependent
- 16: 14,15 → 21
- 17: 14,15 → 22
- 18: 14,15 → 22,24
- 19: 14,15 → 23
- 20: 14,15 → 27

**Wave 4** (Integration):
- 21: 8,9,10,11,12,16,5 → 30
- 22: 13,17,18,5 → 24,25
- 23: 9,10,19,5 → 28
- 24: 18,13,5 → —
- 25: 22,5 → 26
- 26: 25,5 → —
- 27: 13,20,5 → —

**Wave 5** (Advanced):
- 28: 9,23 → 29
- 29: 28 → —
- 30: 8,11,21 → —
- 31: 21,22,23 → —
- 32: 5 → —

**Wave 6** (Polish):
- 33: 8,30 → —
- 34: 21,22,23 → —
- 35: All UI tasks → —
- 36: 22,24 → —
- 37: 1 → 39-41
- 38: 31 → —

**Wave 7** (Packaging):
- 39: 1,7,37 → 42
- 40: 1,7,37 → 42
- 41: 1,7,37 → 42
- 42: 39,40,41,7 → 43
- 43: 42 → —

**Wave FINAL**:
- F1-F4: ALL tasks → —

### Agent Dispatch Summary

- **Wave 1**: 7 tasks — 4× `quick`, 2× `unspecified-high`
- **Wave 2**: 6 tasks — 3× `deep`, 3× `unspecified-high`
- **Wave 3**: 7 tasks — 7× `visual-engineering`
- **Wave 4**: 7 tasks — 4× `deep`, 3× `unspecified-high`
- **Wave 5**: 5 tasks — 2× `deep`, 1× `visual-engineering`, 2× `unspecified-high`
- **Wave 6**: 6 tasks — 2× `deep`, 3× `visual-engineering`, 1× `unspecified-high`
- **Wave 7**: 5 tasks — 3× `unspecified-high`, 1× `git`, 1× `writing`
- **Wave FINAL**: 4 tasks — 1× `oracle`, 2× `unspecified-high`, 1× `deep`

---

## TODOs

> Implementation + Test = ONE Task. Never separate.
> EVERY task MUST have: Recommended Agent Profile + Parallelization info + QA Scenarios.
> **A task WITHOUT QA Scenarios is INCOMPLETE. No exceptions.**

- [ ] 1. Project Scaffolding + Gradle Configuration

  **What to do**:
  - Initialize Compose Multiplatform Desktop project structure
  - Configure Gradle with Kotlin 2.1+ and Compose plugin
  - Add dependencies: Compose Desktop, Exposed, HikariCP, Koin, kotlin.test, TestContainers
  - Set up multi-module structure: `app` (desktop UI), `core` (business logic), `data` (DB access)
  - Configure JVM target 17 minimum
  - Create `.gitignore` for IntelliJ/Gradle/build artifacts
  - Add `gradle.properties` with memory settings for large builds

  **Must NOT do**:
  - Don't add web or Android targets (desktop only)
  - Don't include unnecessary dependencies (keep minimal for aggressive timeline)
  - Don't configure auto-update mechanisms (deferred post-MVP)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Straightforward project initialization with known dependencies
  - **Skills**: []
  - **Skills Evaluated but Omitted**:
    - `git-master`: Not needed for initial setup, only later for commits

  **Parallelization**:
  - **Can Run In Parallel**: NO (foundation must be first)
  - **Parallel Group**: Wave 1 (foundation)
  - **Blocks**: Tasks 2-43 (all depend on project structure)
  - **Blocked By**: None (can start immediately)

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/build.gradle.kts` - Multi-module Compose Desktop Gradle configuration
  - `/home/jonathan/desarrollo/jeaap-pos/settings.gradle.kts` - Module inclusion pattern

  **API/Type References**:
  - Official Compose Multiplatform docs: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-setup.html

  **External References**:
  - Exposed ORM: https://github.com/JetBrains/Exposed (add `exposed-core`, `exposed-jdbc`)
  - HikariCP: https://github.com/brettwooldridge/HikariCP (version 5.0+)
  - Koin: https://insert-koin.io/ (version 4.0+)

  **WHY Each Reference Matters**:
  - JEAAP POS project is proven production Compose Desktop structure - copy its module organization
  - Official docs ensure you use current Compose plugin syntax (not legacy `org.jetbrains.compose`)
  - Library versions must be recent to avoid compatibility issues with Kotlin 2.1

  **Acceptance Criteria**:

  **If TDD (tests enabled):**
  - [ ] Test file created: `core/src/test/kotlin/com/dbeagle/ProjectSetupTest.kt`
  - [ ] `./gradlew test` → PASS (smoke test verifying dependencies resolve)

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Gradle builds successfully with all dependencies
    Tool: Bash
    Preconditions: Fresh checkout of repository
    Steps:
      1. Run `./gradlew clean build --refresh-dependencies`
      2. Check exit code is 0
      3. Verify build/libs/ contains JAR artifacts for all modules
    Expected Result: BUILD SUCCESSFUL, no dependency resolution errors, JARs present
    Failure Indicators: Dependency resolution failure, compilation error, wrong Kotlin version
    Evidence: .sisyphus/evidence/task-1-gradle-build.txt

  Scenario: Compose Desktop application launches
    Tool: Bash
    Preconditions: Gradle build succeeded
    Steps:
      1. Run `./gradlew :app:run` (should show empty window or placeholder)
      2. Wait 10 seconds for window to appear
      3. Kill process
    Expected Result: Compose window opens without crash
    Failure Indicators: NoClassDefFoundError, UnsatisfiedLinkError, crash on startup
    Evidence: .sisyphus/evidence/task-1-app-launch.txt
  ```

  **Evidence to Capture**:
  - [ ] task-1-gradle-build.txt (Gradle build output)
  - [ ] task-1-app-launch.txt (Application launch output)

  **Commit**: YES
  - Message: `feat(project): initialize Compose Desktop project with Gradle config`
  - Files: `build.gradle.kts`, `settings.gradle.kts`, `app/`, `core/`, `data/`, `.gitignore`
  - Pre-commit: `./gradlew clean build`

- [ ] 2. Core Domain Models

  **What to do**:
  - Create sealed class `DatabaseType` (PostgreSQL, SQLite, Future...)
  - Create data class `ConnectionProfile` (id, name, type, host, port, database, username, encrypted password, options map)
  - Create data class `ConnectionConfig` (profile + runtime options like timeout, pool size)
  - Create sealed class `QueryResult` (Success with ResultSet, Error with message)
  - Create data class `SchemaMetadata` (tables, views, indexes, FK relationships)
  - Create data class `TableMetadata` (name, schema, columns, PK, indexes)
  - Create data class `ColumnMetadata` (name, type, nullable, default)
  - Create data class `QueryHistoryEntry` (id, query, timestamp, duration, connection profile)
  - Create data class `FavoriteQuery` (id, name, query, tags, created, last modified)
  - All models immutable (val properties), serializable (for persistence)

  **Must NOT do**:
  - Don't add UI-specific properties (no color, icon, display name logic)
  - Don't add validation logic (keep models pure data structures)
  - Don't add database-specific fields yet (generic abstractions only)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Data class definitions with no business logic
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (independent of other Wave 1 tasks except T1)
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 9, 10, 12, 13 (database layer needs models)
  - **Blocked By**: Task 1 (needs module structure)

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/shared/src/commonMain/kotlin/com/jeaap/pos/model/` - Model structure pattern

  **WHY Each Reference Matters**:
  - JEAAP POS demonstrates clean data class organization with immutability
  - Use similar package structure: `com.dbeagle.core.model.*`

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `core/src/test/kotlin/com/dbeagle/model/ConnectionProfileTest.kt`
  - [ ] Verify serialization/deserialization roundtrip
  - [ ] `./gradlew :core:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: ConnectionProfile serializes to JSON correctly
    Tool: Bash
    Preconditions: Models compiled
    Steps:
      1. Run Kotlin script: create ConnectionProfile, serialize with kotlinx.serialization
      2. Verify JSON contains all fields
      3. Deserialize back to object
      4. Assert equals original
    Expected Result: Roundtrip succeeds, all fields preserved
    Failure Indicators: Missing fields, type mismatch, serialization exception
    Evidence: .sisyphus/evidence/task-2-model-serialization.txt
  ```

  **Evidence to Capture**:
  - [ ] task-2-model-serialization.txt

  **Commit**: YES
  - Message: `feat(domain): add core domain models (Connection, Query, Schema)`
  - Files: `core/src/main/kotlin/com/dbeagle/model/*.kt`
  - Pre-commit: `./gradlew :core:test`

- [ ] 3. Database Driver Abstraction Interface

  **What to do**:
  - Create interface `DatabaseDriver` with methods: `connect(config)`, `disconnect()`, `executeQuery(sql, params)`, `getSchema()`, `getTables()`, `getColumns(table)`, `getForeignKeys()`, `testConnection()`
  - Create interface `ConnectionPool` for managing driver connections
  - Create sealed class `DatabaseCapability` (SupportsTransactions, SupportsFK, SupportsPreparedStatements, etc.)
  - Each driver declares capabilities via `getCapabilities(): Set<DatabaseCapability>`
  - Design for pluggability: drivers discovered via service loader or manual registration
  - Add suspend functions where IO-bound (Kotlin coroutines ready)

  **Must NOT do**:
  - Don't implement drivers yet (interface only)
  - Don't add database-specific methods (keep generic)
  - Don't add UI concerns (drivers don't know about Compose)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Interface definitions with clear contracts
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 9, 10, 11 (driver implementations need interface)
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/server/src/main/kotlin/com/jeaap/pos/di/DatabaseProvider.kt` - Provider interface pattern
  - DBeaver plugin architecture: https://github.com/dbeaver/dbeaver/tree/devel/plugins (observe how drivers expose capabilities)

  **API/Type References**:
  - Exposed Database API: https://github.com/JetBrains/Exposed/wiki/DSL (understand `Database.connect()` signature)

  **WHY Each Reference Matters**:
  - JEAAP POS DatabaseProvider shows clean separation between interface and implementation
  - DBeaver's capability system prevents calling unsupported features per DB type
  - Exposed API guides method signatures for compatibility

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `core/src/test/kotlin/com/dbeagle/driver/DatabaseDriverTest.kt` (mock implementation)
  - [ ] Verify mock driver passes capability checks
  - [ ] `./gradlew :core:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: Mock driver implements all required methods
    Tool: Bash
    Preconditions: Interface compiled
    Steps:
      1. Create mock driver class implementing DatabaseDriver
      2. Call each method (return dummy data)
      3. Verify no NotImplementedError thrown
    Expected Result: All methods callable without exceptions
    Failure Indicators: AbstractMethodError, compilation error
    Evidence: .sisyphus/evidence/task-3-mock-driver.txt
  ```

  **Evidence to Capture**:
  - [ ] task-3-mock-driver.txt

  **Commit**: YES
  - Message: `feat(driver): define database driver abstraction interface`
  - Files: `core/src/main/kotlin/com/dbeagle/driver/*.kt`
  - Pre-commit: `./gradlew :core:test`

- [ ] 4. Credential Encryption Module (AES-GCM)

  **What to do**:
  - Implement `CredentialEncryption` class using AES-GCM-256
  - Use `cryptography-kotlin` library (proven in JEAAP POS)
  - Methods: `encrypt(plaintext: String, masterPassword: String): EncryptedData`, `decrypt(encrypted: EncryptedData, masterPassword: String): String`
  - `EncryptedData` data class: `ciphertext: ByteArray`, `iv: ByteArray`, `salt: ByteArray`
  - Store salt per encrypted value (for PBKDF2 key derivation)
  - Handle master password prompt (defer UI, just crypto logic)
  - Add unit tests with known plaintext/ciphertext pairs

  **Must NOT do**:
  - Don't store master password in memory longer than necessary
  - Don't log plaintext passwords
  - Don't use weak encryption (no AES-ECB, no XOR)
  - Don't implement custom crypto (use battle-tested library)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Security-critical component requiring careful implementation
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 12 (connection profile persistence needs encryption)
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/shared/src/jvmMain/kotlin/com/jeaap/pos/crypto/` - CryptoEngine pattern with AES-GCM

  **API/Type References**:
  - cryptography-kotlin: https://github.com/whyoleg/cryptography-kotlin (use `CryptographyProvider.Default.AES.GCM`)

  **External References**:
  - OWASP Key Storage: https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html

  **WHY Each Reference Matters**:
  - JEAAP POS provides working AES-GCM implementation - copy its structure exactly
  - cryptography-kotlin is multiplatform-ready (future mobile apps)
  - OWASP guides key derivation best practices (PBKDF2 with 100k+ iterations)

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `core/src/test/kotlin/com/dbeagle/crypto/CredentialEncryptionTest.kt`
  - [ ] Encrypt known plaintext, verify ciphertext differs
  - [ ] Decrypt ciphertext, verify matches original plaintext
  - [ ] Wrong master password fails decryption
  - [ ] `./gradlew :core:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: Encrypt and decrypt password successfully
    Tool: Bash
    Preconditions: Crypto module compiled
    Steps:
      1. Run Kotlin script: encrypt "myPassword123" with master "master"
      2. Verify ciphertext != plaintext
      3. Decrypt ciphertext with same master password
      4. Assert decrypted == "myPassword123"
    Expected Result: Roundtrip successful, plaintext recovered
    Failure Indicators: Decryption failure, wrong plaintext, exception
    Evidence: .sisyphus/evidence/task-4-encryption-roundtrip.txt

  Scenario: Wrong master password fails decryption
    Tool: Bash
    Preconditions: Crypto module compiled
    Steps:
      1. Encrypt "secret" with master "correct"
      2. Attempt decrypt with master "wrong"
      3. Verify exception or null result
    Expected Result: Decryption fails gracefully
    Failure Indicators: Decryption succeeds (security flaw), crash
    Evidence: .sisyphus/evidence/task-4-encryption-failure.txt
  ```

  **Evidence to Capture**:
  - [ ] task-4-encryption-roundtrip.txt
  - [ ] task-4-encryption-failure.txt

  **Commit**: YES
  - Message: `feat(security): implement AES-GCM credential encryption`
  - Files: `core/src/main/kotlin/com/dbeagle/crypto/*.kt`
  - Pre-commit: `./gradlew :core:test`

- [ ] 5. Koin DI Setup + Module Structure

  **What to do**:
  - Add Koin dependency injection framework
  - Create `CoreModule.kt` defining DI modules for core layer
  - Create `DataModule.kt` for data layer (drivers, repositories)
  - Create `AppModule.kt` for UI layer (ViewModels, UI state)
  - Define singleton scopes for connection pool, driver registry, credential encryption
  - Initialize Koin in main() function
  - Add `KoinTest` base class for unit tests

  **Must NOT do**:
  - Don't use ServiceLoader (Koin replaces this for DI)
  - Don't create manual factories (let Koin handle instantiation)
  - Don't inject UI into core layer (dependencies go core ← data ← app)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Standard DI setup following Koin conventions
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 8, 11, 21+ (all DI-dependent modules)
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/composeApp/src/commonMain/kotlin/com/jeaap/pos/di/` - Koin module organization

  **API/Type References**:
  - Koin docs: https://insert-koin.io/docs/reference/koin-compose/compose (Compose Desktop integration)

  **WHY Each Reference Matters**:
  - JEAAP POS demonstrates clean module separation by layer (data, feature, presentation)
  - Koin Compose docs show how to inject ViewModels into Composables

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `core/src/test/kotlin/com/dbeagle/di/KoinModuleTest.kt`
  - [ ] Start Koin, resolve all declared dependencies
  - [ ] Verify no circular dependencies
  - [ ] `./gradlew :core:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: Koin resolves all dependencies
    Tool: Bash
    Preconditions: DI modules defined
    Steps:
      1. Run test: startKoin { modules(coreModule, dataModule, appModule) }
      2. Resolve each declared dependency: get<DatabaseDriverRegistry>(), get<CredentialEncryption>()
      3. Verify no exceptions
    Expected Result: All dependencies resolve successfully
    Failure Indicators: NoBeanDefFoundException, circular dependency error
    Evidence: .sisyphus/evidence/task-5-koin-resolution.txt
  ```

  **Evidence to Capture**:
  - [ ] task-5-koin-resolution.txt

  **Commit**: YES
  - Message: `feat(di): setup Koin dependency injection structure`
  - Files: `core/src/main/kotlin/com/dbeagle/di/*.kt`, `app/src/main/kotlin/com/dbeagle/di/AppModule.kt`
  - Pre-commit: `./gradlew test`

- [ ] 6. kotlin.test Infrastructure + TestContainers Setup

  **What to do**:
  - Add kotlin.test dependency to all modules
  - Add TestContainers for PostgreSQL and SQLite
  - Create `BaseTest` abstract class with common test utilities
  - Create `DatabaseTest Container` helper that starts PostgreSQL/SQLite containers
  - Configure Gradle test task: parallel execution, HTML reports
  - Add coverage plugin (Kover) with 80% threshold
  - Create example smoke test verifying test infrastructure works

  **Must NOT do**:
  - Don't write actual feature tests yet (just infrastructure)
  - Don't add UI testing frameworks (Compose UI tests come later)
  - Don't add performance benchmarking (out of MVP scope)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Standard test setup with known tools
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: All test tasks throughout project
  - **Blocked By**: Task 1

  **References**:

  **API/Type References**:
  - kotlin.test: https://kotlinlang.org/api/latest/kotlin.test/
  - TestContainers: https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/
  - Kover: https://github.com/Kotlin/kotlinx-kover

  **WHY Each Reference Matters**:
  - kotlin.test is official Kotlin testing library (simple DSL)
  - TestContainers ensures tests run against real databases (not mocks)
  - Kover provides Gradle-native code coverage

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `core/src/test/kotlin/com/dbeagle/SmokeTest.kt`
  - [ ] Smoke test instantiates PostgreSQL container and runs simple query
  - [ ] `./gradlew test` → PASS, HTML report generated
  - [ ] `./gradlew koverHtmlReport` → coverage report >0%

  **QA Scenarios**:

  ```
  Scenario: PostgreSQL TestContainer starts and accepts connections
    Tool: Bash
    Preconditions: TestContainers configured
    Steps:
      1. Run test that starts PostgresContainer
      2. Execute `SELECT 1` query
      3. Verify result == 1
    Expected Result: Container starts, query succeeds
    Failure Indicators: Container start timeout, connection refused, query fails
    Evidence: .sisyphus/evidence/task-6-testcontainer-postgres.txt

  Scenario: Test coverage report generates
    Tool: Bash
    Preconditions: Kover configured
    Steps:
      1. Run `./gradlew koverHtmlReport`
      2. Check build/reports/kover/html/index.html exists
      3. Verify contains coverage percentages
    Expected Result: HTML report generated with coverage data
    Failure Indicators: Report missing, 0% coverage, build failure
    Evidence: .sisyphus/evidence/task-6-coverage-report.txt
  ```

  **Evidence to Capture**:
  - [ ] task-6-testcontainer-postgres.txt
  - [ ] task-6-coverage-report.txt

  **Commit**: YES
  - Message: `feat(test): add kotlin.test infrastructure with TestContainers`
  - Files: `build.gradle.kts` (test dependencies), `core/src/test/kotlin/BaseTest.kt`
  - Pre-commit: `./gradlew test`

- [ ] 7. CI/CD Workflow (GitHub Actions Multi-OS Builds)

  **What to do**:
  - Create `.github/workflows/build.yml` with matrix strategy (macOS, Windows, Ubuntu)
  - Jobs: `build` (compile), `test` (run tests + coverage), `package` (create installers)
  - Run on: push to main, pull requests
  - Upload artifacts: DMG, MSI, DEB to workflow artifacts
  - Add status badge to README.md
  - Set up dependency caching (Gradle cache)

  **Must NOT do**:
  - Don't publish releases automatically (manual approval required)
  - Don't run on every commit (only main/PRs)
  - Don't add deployment steps (just build verification)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: GitHub Actions requires understanding of matrix builds and Gradle caching
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 39-42 (packaging depends on CI infrastructure)
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - Compose Multiplatform examples: https://github.com/JetBrains/compose-multiplatform/tree/master/.github/workflows

  **API/Type References**:
  - GitHub Actions matrix: https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs

  **WHY Each Reference Matters**:
  - JetBrains Compose examples show correct Java versions and Gradle setups per OS
  - Matrix strategy enables parallel builds across OSes (faster feedback)

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] No unit tests for CI config (infrastructure as code)

  **QA Scenarios**:

  ```
  Scenario: GitHub Actions workflow builds on all platforms
    Tool: Bash (via GitHub Actions)
    Preconditions: Workflow file committed
    Steps:
      1. Push commit to trigger workflow
      2. Wait for workflow completion
      3. Check all 3 matrix jobs (macOS, Windows, Ubuntu) pass
      4. Download artifacts, verify DMG/MSI/DEB present
    Expected Result: All jobs green, artifacts available
    Failure Indicators: Job failure, missing artifacts, wrong OS
    Evidence: .sisyphus/evidence/task-7-ci-workflow.txt (workflow logs)
  ```

  **Evidence to Capture**:
  - [ ] task-7-ci-workflow.txt

  **Commit**: YES
  - Message: `ci: configure GitHub Actions for multi-OS builds`
  - Files: `.github/workflows/build.yml`, `README.md` (badge)
  - Pre-commit: `./gradlew build` (local verification)

- [ ] 8. HikariCP Connection Pool Wrapper

  **What to do**:
  - Create `DatabaseConnectionPool` class wrapping HikariCP
  - Configuration: max pool size (default 10), connection timeout (30s), idle timeout (10min), max lifetime (30min)
  - Methods: `getConnection(profile: ConnectionProfile): Connection`, `closePool(profile: ConnectionProfile)`, `closeAllPools()`
  - Track active pools by profile ID (one pool per connection profile)
  - Add JMX metrics exposure for pool health (HikariCP built-in)
  - Handle pool exhaustion gracefully (throw exception with retry suggestion)
  - Add leak detection (HikariCP `leakDetectionThreshold`)

  **Must NOT do**:
  - Don't implement custom pooling logic (HikariCP handles everything)
  - Don't store credentials in pool config (use encrypted profile)
  - Don't create pools eagerly (lazy init on first connection request)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Connection pooling requires careful resource management
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO (needs Task 5 Koin DI)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 9, 10, 13, 21, 22, 23, 30
  - **Blocked By**: Task 5

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/server/src/main/kotlin/com/jeaap/pos/di/DatabaseProvider.kt` - Connection lifecycle

  **API/Type References**:
  - HikariCP config: https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby

  **WHY Each Reference Matters**:
  - JEAAP POS shows proper connection management pattern (initialization, cleanup)
  - HikariCP config docs explain critical settings (pool size, timeouts, leak detection)

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `data/src/test/kotlin/com/dbeagle/pool/DatabaseConnectionPoolTest.kt`
  - [ ] Create pool, get connection, verify is HikariProxyConnection
  - [ ] Close pool, verify subsequent getConnection fails
  - [ ] `./gradlew :data:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: Pool provides working connections
    Tool: Bash (with TestContainers)
    Preconditions: PostgreSQL container running
    Steps:
      1. Create ConnectionProfile for test DB
      2. Call pool.getConnection(profile)
      3. Execute `SELECT 1` on connection
      4. Verify result == 1
      5. Close connection, return to pool
    Expected Result: Connection works, query succeeds, no leak
    Failure Indicators: Connection null, query fails, leak detected
    Evidence: .sisyphus/evidence/task-8-pool-connection.txt

  Scenario: Pool exhaustion throws helpful exception
    Tool: Bash
    Preconditions: Pool size = 2
    Steps:
      1. Get 2 connections (exhaust pool)
      2. Attempt 3rd connection (should wait/timeout)
      3. Verify exception message mentions pool exhaustion
    Expected Result: Exception thrown with retry suggestion
    Failure Indicators: Hangs forever, no exception, unclear message
    Evidence: .sisyphus/evidence/task-8-pool-exhaustion.txt
  ```

  **Evidence to Capture**:
  - [ ] task-8-pool-connection.txt
  - [ ] task-8-pool-exhaustion.txt

  **Commit**: YES
  - Message: `feat(pool): implement HikariCP connection pool wrapper`
  - Files: `data/src/main/kotlin/com/dbeagle/pool/*.kt`
  - Pre-commit: `./gradlew :data:test`

- [ ] 9. PostgreSQL Driver Implementation (Exposed)

  **What to do**:
  - Implement `PostgreSQLDriver` class implementing `DatabaseDriver` interface
  - Use Exposed ORM with PostgreSQL dialect
  - Connection string builder: `jdbc:postgresql://{host}:{port}/{database}`
  - Implement all interface methods: connect, disconnect, executeQuery, getSchema, getTables, getColumns, getForeignKeys
  - Return capabilities: SupportsTransactions, SupportsFK, SupportsPreparedStatements, SupportsJSON
  - Handle PostgreSQL-specific types (UUID, JSONB, ARRAY)
  - Add query timeout handling
  - Write integration tests with TestContainers PostgreSQL

  **Must NOT do**:
  - Don't add PostgreSQL-specific UI (driver is data layer only)
  - Don't hardcode connection strings (use ConnectionProfile)
  - Don't cache schema metadata forever (implement TTL or manual refresh)

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Complex integration with Exposed ORM and PostgreSQL-specific features
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (independent from SQLite driver)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 11, 21, 22, 23, 28
  - **Blocked By**: Tasks 2, 3, 5, 6

  **References**:

  **Pattern References**:
  - `/home/jonathan/desarrollo/jeaap-pos/server/src/main/kotlin/com/jeaap/pos/di/DatabaseProvider.kt` - Database connection pattern

  **API/Type References**:
  - Exposed PostgreSQL: https://github.com/JetBrains/Exposed/wiki/DataTypes#how-to-use-database-specific-types
  - PostgreSQL JDBC: https://jdbc.postgresql.org/documentation/

  **WHY Each Reference Matters**:
  - JEAAP POS shows connection lifecycle management
  - Exposed wiki explains PostgreSQL-specific type mappings (UUID, JSONB)
  - JDBC docs cover connection string parameters (SSL, timeouts)

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `data/src/test/kotlin/com/dbeagle/driver/PostgreSQLDriverTest.kt`
  - [ ] Start PostgreSQL container, connect, execute SELECT 1
  - [ ] Test getSchema() returns tables from public schema
  - [ ] Test getForeignKeys() returns FK relationships
  - [ ] `./gradlew :data:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: Connect to PostgreSQL and execute query
    Tool: Bash (TestContainers)
    Preconditions: PostgreSQL container with test data
    Steps:
      1. Start Postgres container with initialized schema (users table)
      2. Create ConnectionProfile for container
      3. Call driver.connect(profile)
      4. Execute `SELECT * FROM users LIMIT 10`
      5. Verify results contain expected columns (id, name, email)
    Expected Result: Query succeeds, results have 3 columns
    Failure Indicators: Connection refused, query timeout, wrong columns
    Evidence: .sisyphus/evidence/task-9-postgres-query.txt

  Scenario: getSchema returns all tables with metadata
    Tool: Bash (TestContainers)
    Preconditions: Postgres with 5 tables
    Steps:
      1. Call driver.getSchema()
      2. Verify returns 5 tables
      3. Check each table has columns metadata
      4. Verify FK relationships present
    Expected Result: Complete schema metadata returned
    Failure Indicators: Missing tables, no columns, FKs missing
    Evidence: .sisyphus/evidence/task-9-postgres-schema.txt
  ```

  **Evidence to Capture**:
  - [ ] task-9-postgres-query.txt
  - [ ] task-9-postgres-schema.txt

  **Commit**: YES
  - Message: `feat(driver): add PostgreSQL driver with Exposed ORM`
  - Files: `data/src/main/kotlin/com/dbeagle/driver/PostgreSQLDriver.kt`
  - Pre-commit: `./gradlew :data:test`

- [ ] 10. SQLite Driver Implementation (Exposed)

  **What to do**:
  - Implement `SQLiteDriver` class implementing `DatabaseDriver` interface
  - Use Exposed ORM with SQLite dialect
  - Connection string: `jdbc:sqlite:{file_path}` (support absolute paths and `:memory:`)
  - Implement all interface methods (same as PostgreSQL driver)
  - Return capabilities: SupportsTransactions, SupportsFK (if enabled), SupportsPreparedStatements (no JSON, no Arrays)
  - Handle SQLite file picker integration (defer UI, just file path handling)
  - Write integration tests with in-memory SQLite

  **Must NOT do**:
  - Don't assume SQLite has all PostgreSQL features (check capabilities)
  - Don't enable unsafe pragmas (foreign_keys should be ON by default)
  - Don't add encryption (SQLCipher deferred post-MVP)

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Similar complexity to PostgreSQL driver
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (independent from PostgreSQL driver)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 11, 21, 22, 23
  - **Blocked By**: Tasks 2, 3, 5, 6

  **References**:

  **API/Type References**:
  - Exposed SQLite: https://github.com/JetBrains/Exposed/wiki/Databases#sqlite
  - SQLite JDBC: https://github.com/xerial/sqlite-jdbc

  **WHY Each Reference Matters**:
  - Exposed wiki explains SQLite dialect configuration
  - sqlite-jdbc docs cover pragmas and connection strings

  **Acceptance Criteria**:

  **If TDD:**
  - [ ] Test: `data/src/test/kotlin/com/dbeagle/driver/SQLiteDriverTest.kt`
  - [ ] Connect to :memory: database, create table, insert row
  - [ ] Execute SELECT, verify results
  - [ ] Test getSchema() returns tables
  - [ ] `./gradlew :data:test` → PASS

  **QA Scenarios**:

  ```
  Scenario: Connect to in-memory SQLite and query
    Tool: Bash
    Preconditions: SQLite driver compiled
    Steps:
      1. Create ConnectionProfile with path=":memory:"
      2. Connect, create table: CREATE TABLE test(id INT, name TEXT)
      3. Insert row: INSERT INTO test VALUES(1, 'Alice')
      4. Query: SELECT * FROM test
      5. Verify result contains (1, 'Alice')
    Expected Result: Query succeeds with correct data
    Failure Indicators: Connection fails, table not created, query empty
    Evidence: .sisyphus/evidence/task-10-sqlite-memory.txt

  Scenario: Connect to file-based SQLite
    Tool: Bash
    Preconditions: test.db file with sample data
    Steps:
      1. Create ConnectionProfile with path="./test.db"
      2. Connect and query existing table
      3. Verify data loads correctly
    Expected Result: File database opens, data accessible
    Failure Indicators: File not found, locked database, query fails
    Evidence: .sisyphus/evidence/task-10-sqlite-file.txt
  ```

  **Evidence to Capture**:
  - [ ] task-10-sqlite-memory.txt
  - [ ] task-10-sqlite-file.txt

  **Commit**: YES
  - Message: `feat(driver): add SQLite driver with Exposed ORM`
  - Files: `data/src/main/kotlin/com/dbeagle/driver/SQLiteDriver.kt`
  - Pre-commit: `./gradlew :data:test`

- [ ] 11. Driver Registry + Plugin Loading

  **What to do**: Create `DatabaseDriverRegistry` singleton managing driver instances. Methods: `registerDriver(type, driver)`, `getDriver(type)`, `listAvailableDrivers()`. Auto-register PostgreSQL and SQLite drivers on init. Support manual registration for future plugins.
  
  **Agent**: `unspecified-high` | **Wave**: 2 | **Blocks**: 21, 30 | **Blocked By**: 3, 5
  
  **QA**: Test registry returns correct driver for each type. Evidence: task-11-registry.txt
  
  **Commit**: `feat(registry): implement driver registry and plugin loading`

- [ ] 12. Connection Profile Persistence (Encrypted)

  **What to do**: Create `ConnectionProfileRepository` using Java Preferences API for storage. Encrypt passwords with CredentialEncryption (Task 4). Methods: `save(profile)`, `load(id)`, `loadAll()`, `delete(id)`. Master password prompt on first save/load.
  
  **Agent**: `unspecified-high` | **Wave**: 2 | **Blocks**: 16, 21 | **Blocked By**: 2, 4, 5
  
  **QA**: Save profile with password, restart app, load profile, verify password decrypts correctly. Evidence: task-12-persistence.txt
  
  **Commit**: `feat(profile): add connection profile persistence with encryption`

- [ ] 13. Query Execution Engine with Pagination

  **What to do**: Create `QueryExecutor` wrapping driver.executeQuery(). Implement pagination: fetch results in batches (default 1000 rows), support offset/limit. Return `QueryResult` with `ResultSet` wrapper exposing `hasMore()`, `fetchNext()`. Handle query timeouts (60s default).
  
  **Agent**: `deep` | **Wave**: 2 | **Blocks**: 22, 24, 27 | **Blocked By**: 2, 5, 8
  
  **QA**: Execute query returning 5000 rows, verify only 1000 loaded initially, call fetchNext(), verify next batch loads. Evidence: task-13-pagination.txt
  
  **Commit**: `feat(query): implement query execution engine with pagination`

- [ ] 14. Compose Desktop Main Window Scaffold

  **What to do**: Create `MainWindow` composable with `MaterialTheme`. Top-level structure: `Scaffold` with TopAppBar (title: "DB Eagle"), left sidebar (connections list), center content area (selected tab), status bar (connection status). Responsive layout using `Row` + `Column` + `Box`.
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 15-20 (all UI) | **Blocked By**: 1, 5
  
  **QA**: Launch app, verify window opens 1200x800, sidebar visible, content area empty. Evidence: task-14-window-screenshot.png
  
  **Commit**: `feat(ui): add main window scaffold and navigation structure`

- [ ] 15. Navigation Structure (Sidebar + Content Area)

  **What to do**: Implement tabbed navigation: Connections, Query Editor, Schema Browser, Favorites, History. Sidebar shows active connection list + "New Connection" button. Content area switches based on selected tab. Use `remember` for tab state.
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 16-20 | **Blocked By**: 14
  
  **QA**: Click each tab, verify content area changes. Evidence: task-15-navigation.png
  
  **Commit**: (grouped with task 14)

- [ ] 16. Connection Manager UI (List, Create, Edit, Delete)

  **What to do**: Create connection list (LazyColumn) showing profile names + DB type icons. "+" button opens connection dialog. Dialog fields: name, type (dropdown), host, port, database, username, password. Save encrypts password. Edit/delete via context menu.
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 21 | **Blocked By**: 14, 15
  
  **QA**: Create connection, verify appears in list, edit connection, delete connection. Evidence: task-16-connection-ui.png
  
  **Commit**: `feat(ui): add connection manager UI components`

- [ ] 17. SQL Editor Component with Syntax Highlighting

  **What to do**: Create `SQLEditor` composable using `TextField` with monospace font. Integrate syntax highlighting library (KodeView or RichTextEditor). Toolbar: Run Query button, Clear button, Save to Favorites button. Multi-line input with line numbers.
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 22 | **Blocked By**: 14, 15
  
  **QA**: Type SQL query, verify keywords highlighted, press Run button (placeholder action). Evidence: task-17-sql-editor.png
  
  **Commit**: `feat(ui): add SQL editor with syntax highlighting`

- [ ] 18. Result Grid Component with Pagination

  **What to do**: Create `ResultGrid` composable displaying query results in table. Columns: auto-sized based on header text. Rows: LazyColumn for scrolling. Pagination controls: Previous/Next buttons, page indicator. Editable cells (double-click to edit).
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 22, 24 | **Blocked By**: 14, 15
  
  **QA**: Display mock ResultSet with 100 rows, scroll, verify pagination works. Evidence: task-18-result-grid.png
  
  **Commit**: `feat(ui): add result grid component with pagination`

- [ ] 19. Schema Browser Tree Component

  **What to do**: Create `SchemaTree` composable with expandable tree (Tables → Columns, Views, Indexes). Use `LazyColumn` + `Row` with indentation. Icons for database objects. Click table → load columns. Right-click → context menu (Copy Name, View Data).
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 23 | **Blocked By**: 14, 15
  
  **QA**: Expand table node, verify columns appear. Evidence: task-19-schema-tree.png
  
  **Commit**: `feat(ui): add schema browser tree component`

- [ ] 20. Export Dialog UI (Format Selection, File Picker)

  **What to do**: Create `ExportDialog` composable with format selection (CSV, JSON, SQL radio buttons), file path TextField + Browse button (native file picker). Export button triggers save. Progress indicator for large exports.
  
  **Agent**: `visual-engineering` | **Wave**: 3 | **Blocks**: 27 | **Blocked By**: 14, 15
  
  **QA**: Open dialog, select CSV, choose file path, click Export (mock action). Evidence: task-20-export-dialog.png
  
  **Commit**: `feat(ui): add export dialog with format selection`

- [ ] 21. Connection Manager Integration (UI ↔ DB Layer)

  **What to do**: Connect Task 16 UI to Task 8 pool + Task 11 registry. "Connect" button calls `pool.getConnection(profile)`, shows spinner, updates status. List displays active connections (green dot). "Disconnect" closes pool for profile. Handle connection failures with error dialog.
  
  **Agent**: `deep` | **Wave**: 4 | **Blocks**: 30 | **Blocked By**: 8, 9, 10, 11, 12, 16, 5
  
  **QA**: Create connection profile, click Connect, verify PostgreSQL connection succeeds, schema loads. Evidence: task-21-connection-integration.txt
  
  **Commit**: `feat(integration): connect connection manager UI to DB layer`

- [ ] 22. SQL Editor Integration (Execute Query → Display Results)

  **What to do**: Connect Task 17 editor to Task 13 executor. Run button calls `executor.executeQuery(sql, activeConnection)`, updates Task 18 grid with results. Show query duration in status bar. Handle errors (syntax error, connection lost) with error message.
  
  **Agent**: `deep` | **Wave**: 4 | **Blocks**: 24, 25 | **Blocked By**: 13, 17, 18, 5
  
  **QA**: Execute `SELECT * FROM users LIMIT 10`, verify results display in grid. Evidence: task-22-query-execution.txt
  
  **Commit**: `feat(integration): integrate SQL editor with query execution`

- [ ] 23. Schema Browser Integration (Load Schema → Tree View)

  **What to do**: Connect Task 19 tree to Task 9/10 drivers. On connection, call `driver.getSchema()`, populate tree with tables/views/indexes. Lazy-load columns on table expansion. Cache schema with TTL (5 min) or manual refresh button.
  
  **Agent**: `deep` | **Wave**: 4 | **Blocks**: 28 | **Blocked By**: 9, 10, 19, 5
  
  **QA**: Connect to PostgreSQL, verify schema tree populates with tables. Expand table, verify columns load. Evidence: task-23-schema-integration.txt
  
  **Commit**: `feat(integration): integrate schema browser with DB metadata`

- [ ] 24. Data Editing Logic (Inline Grid Edits → SQL UPDATE)

  **What to do**: Enable cell editing in Task 18 grid. Double-click cell → editable TextField. On blur, generate `UPDATE table SET column = value WHERE pk = id`. Execute update via Task 13 executor. Show success/failure feedback. Track dirty rows (highlight changed cells).
  
  **Agent**: `deep` | **Wave**: 4 | **Blocks**: — | **Blocked By**: 18, 13, 5
  
  **QA**: Edit cell value, press Enter, verify UPDATE executes, cell updates. Query table again, verify change persisted. Evidence: task-24-data-edit.txt
  
  **Commit**: `feat(edit): implement inline data editing with SQL UPDATE`

- [ ] 25. Query History Persistence + UI

  **What to do**: Create `QueryHistoryRepository` storing executed queries with timestamp, duration, connection profile. Persist to local file (JSON). Add History tab (Task 15) displaying list of past queries. Click query → load into editor. Clear history button.
  
  **Agent**: `unspecified-high` | **Wave**: 4 | **Blocks**: 26 | **Blocked By**: 22, 5
  
  **QA**: Execute 5 queries, restart app, verify history persists. Click history entry, verify loads into editor. Evidence: task-25-history.txt
  
  **Commit**: `feat(history): add query history persistence and UI`

- [ ] 26. Favorites System (Save/Load/Tag Queries)

  **What to do**: Create `FavoritesRepository` storing favorite queries with name, tags, created date. Add "Save to Favorites" button in editor (Task 17). Favorites tab shows list with search/filter by tag. Edit favorite (rename, re-tag). Delete favorite.
  
  **Agent**: `unspecified-high` | **Wave**: 4 | **Blocks**: — | **Blocked By**: 25, 5
  
  **QA**: Save query to favorites with tags "users, admin". Search by tag, verify appears. Load favorite, verify query loads into editor. Evidence: task-26-favorites.txt
  
  **Commit**: `feat(favorites): implement favorites system with tagging`

- [ ] 27. Export Functionality (Results → CSV/JSON/SQL Files)

  **What to do**: Connect Task 20 dialog to result export logic. CSV: write headers + rows with comma delimiter. JSON: array of objects. SQL: INSERT statements. Stream large result sets (don't load all into memory). Show progress bar for >1000 rows.
  
  **Agent**: `unspecified-high` | **Wave**: 4 | **Blocks**: — | **Blocked By**: 13, 20, 5
  
  **QA**: Execute query with 500 rows, export to CSV, verify file contains 501 lines (header + rows). Evidence: task-27-export-csv.txt
  
  **Commit**: `feat(export): integrate export functionality with file output`

- [ ] 28. ER Diagram Generator (FK Relationships)

  **What to do**: Create `ERDiagramGenerator` analyzing schema (Task 23) to extract FK relationships. Build graph: `Table -> List<FK>`. Return `ERDiagram` data structure with nodes (tables) and edges (FKs). Limit to single schema (no cross-schema).
  
  **Agent**: `deep` | **Wave**: 5 | **Blocks**: 29 | **Blocked By**: 9, 23
  
  **QA**: Generate ER diagram for schema with 5 tables + 3 FKs, verify graph structure correct. Evidence: task-28-er-generator.txt
  
  **Commit**: `feat(er): add ER diagram generator for FK relationships`

- [ ] 29. ER Diagram Renderer (Compose Canvas)

  **What to do**: Create `ERDiagramView` composable rendering Task 28 diagram. Use Compose `Canvas` for drawing. Tables = rectangles with column names. FKs = lines with arrows. Layout algorithm: simple grid or force-directed. Zoom/pan support.
  
  **Agent**: `visual-engineering` | **Wave**: 5 | **Blocks**: — | **Blocked By**: 28
  
  **QA**: Render ER diagram, verify tables drawn, FK lines connect correct tables. Evidence: task-29-er-render.png
  
  **Commit**: `feat(er): implement ER diagram renderer with Compose Canvas`

- [ ] 30. Multi-Connection Session Management

  **What to do**: Support multiple active connections simultaneously. Tab per connection in UI. Connection context stored in ViewModel. Switch between connections updates editor/schema/results context. Close connection tab → close pool.
  
  **Agent**: `deep` | **Wave**: 5 | **Blocks**: — | **Blocked By**: 8, 11, 21
  
  **QA**: Open 3 connections (2 Postgres, 1 SQLite), switch tabs, verify each has independent query context. Evidence: task-30-multi-connection.txt
  
  **Commit**: `feat(session): add multi-connection session management`

- [ ] 31. Error Handling + User Feedback (Toasts, Dialogs)

  **What to do**: Create `ErrorHandler` utility displaying user-friendly errors. Connection failures → dialog with retry button. Query errors → toast with SQL error message. Use Compose `SnackbarHost` for toasts. Log errors to file for debugging.
  
  **Agent**: `unspecified-high` | **Wave**: 5 | **Blocks**: — | **Blocked By**: 21, 22, 23
  
  **QA**: Trigger connection failure (wrong password), verify error dialog appears. Execute invalid SQL, verify error toast shows. Evidence: task-31-error-dialog.png
  
  **Commit**: `feat(ux): add error handling with toasts and dialogs`

- [ ] 32. Application Settings (Theme, Limits, Timeouts)

  **What to do**: Create settings screen: result limit (default 1000), query timeout (60s), connection timeout (30s), max connections (10). Persist to Java Preferences. Settings button in TopAppBar.
  
  **Agent**: `unspecified-high` | **Wave**: 5 | **Blocks**: — | **Blocked By**: 5
  
  **QA**: Change result limit to 500, execute query, verify only 500 rows load. Evidence: task-32-settings.txt
  
  **Commit**: `feat(settings): implement application settings (limits, timeouts)`

- [ ] 33. Connection Pooling Optimization + Leak Detection

  **What to do**: Tune HikariCP settings: `leakDetectionThreshold=30000`, `idleTimeout=600000`, `maxLifetime=1800000`. Add pool health check on status bar. Log warnings for leaked connections. Add pool stats to debug menu.
  
  **Agent**: `deep` | **Wave**: 6 | **Blocks**: — | **Blocked By**: 8, 30
  
  **QA**: Open 10 connections without closing, verify leak detection logs warnings. Evidence: task-33-leak-detection.txt
  
  **Commit**: `perf(pool): optimize connection pooling and add leak detection`

- [ ] 34. UI Responsiveness (Async Loading, Progress Indicators)

  **What to do**: Move all DB calls to coroutines (Dispatchers.IO). Show `CircularProgressIndicator` during: connection, query execution, schema loading. Prevent UI freeze on large operations. Cancel coroutines on user cancel action.
  
  **Agent**: `visual-engineering` | **Wave**: 6 | **Blocks**: — | **Blocked By**: 21, 22, 23
  
  **QA**: Execute slow query (5 sec), verify UI remains responsive, spinner visible. Cancel query mid-execution, verify stops. Evidence: task-34-async-loading.txt
  
  **Commit**: `perf(ui): improve UI responsiveness with async loading`

- [ ] 35. Keyboard Shortcuts + Accessibility Basics

  **What to do**: Add shortcuts: Cmd/Ctrl+Enter = run query, Cmd/Ctrl+N = new connection, Cmd/Ctrl+W = close tab, Cmd/Ctrl+, = settings. Use Compose `onKeyEvent`. Add focus indicators for keyboard navigation.
  
  **Agent**: `visual-engineering` | **Wave**: 6 | **Blocks**: — | **Blocked By**: All UI tasks
  
  **QA**: Press Cmd+Enter in editor, verify query executes. Navigate UI with Tab key, verify focus indicators visible. Evidence: task-35-shortcuts.txt
  
  **Commit**: `feat(a11y): add keyboard shortcuts and accessibility basics`

- [ ] 36. Memory Profiling + Large Result Set Optimization

  **What to do**: Profile app with 10k row result set. Optimize: use `LazyColumn` virtualization, limit cell string length (truncate at 500 chars), release old result sets on new query. Add memory usage indicator in status bar.
  
  **Agent**: `deep` | **Wave**: 6 | **Blocks**: — | **Blocked By**: 22, 24
  
  **QA**: Execute query returning 10k rows, verify memory <500MB, scroll performance >30fps. Evidence: task-36-memory-profile.txt
  
  **Commit**: `perf(memory): optimize large result set handling`

- [ ] 37. Application Icon + Branding Assets

  **What to do**: Design db-eagle icon (eagle + database symbol). Create icon variants: 512x512, 256x256, 128x128, 64x64, 32x32. Add to app resources. Update macOS Info.plist, Windows manifest, Linux .desktop file with icon paths. Add splash screen (optional).
  
  **Agent**: `visual-engineering` | **Wave**: 6 | **Blocks**: 39-41 | **Blocked By**: 1
  
  **QA**: Launch app, verify custom icon in dock/taskbar. Evidence: task-37-app-icon.png
  
  **Commit**: `feat(brand): add application icon and branding assets`

- [ ] 38. Crash Reporting + Logging Infrastructure

  **What to do**: Add SLF4J + Logback for logging. Log levels: INFO (user actions), WARN (recoverable errors), ERROR (crashes). Uncaught exception handler writes to `~/.dbeagle/crash.log`. Add "Report Issue" button copying logs to clipboard.
  
  **Agent**: `unspecified-high` | **Wave**: 6 | **Blocks**: — | **Blocked By**: 31
  
  **QA**: Trigger uncaught exception, verify crash.log created with stack trace. Evidence: task-38-crash-log.txt
  
  **Commit**: `feat(observability): add crash reporting and logging`

- [ ] 39. macOS Packaging (DMG + Signing)

  **What to do**: Configure Compose Gradle plugin `nativeDistributions.macOS`. Generate DMG with drag-to-Applications background. Code sign with Apple Developer ID (if available, else skip). Notarize (if signing enabled). Test on macOS 12+.
  
  **Agent**: `unspecified-high` | **Wave**: 7 | **Blocks**: 42 | **Blocked By**: 1, 7, 37
  
  **QA**: Run `./gradlew packageDmg`, verify DMG opens, drag to Applications, launch app. Evidence: task-39-dmg-install.txt
  
  **Commit**: `build(macos): configure DMG packaging and signing`

- [ ] 40. Windows Packaging (MSI + Signing)

  **What to do**: Configure Compose Gradle plugin `nativeDistributions.windows`. Generate MSI installer. Add to Windows PATH (optional). Code sign with certificate (if available). Test on Windows 10+.
  
  **Agent**: `unspecified-high` | **Wave**: 7 | **Blocks**: 42 | **Blocked By**: 1, 7, 37
  
  **QA**: Run `./gradlew packageMsi`, install MSI, launch from Start Menu. Evidence: task-40-msi-install.txt
  
  **Commit**: `build(windows): configure MSI packaging and signing`

- [ ] 41. Linux Packaging (DEB + RPM)

  **What to do**: Configure Compose Gradle plugin `nativeDistributions.linux`. Generate DEB (Debian/Ubuntu) and RPM (Fedora/RHEL). Add desktop entry with icon. Test on Ubuntu 20.04+.
  
  **Agent**: `unspecified-high` | **Wave**: 7 | **Blocks**: 42 | **Blocked By**: 1, 7, 37
  
  **QA**: Run `./gradlew packageDeb`, install with `sudo dpkg -i`, launch from app menu. Evidence: task-41-deb-install.txt
  
  **Commit**: `build(linux): configure DEB and RPM packaging`

- [ ] 42. GitHub Release Automation

  **What to do**: Extend `.github/workflows/build.yml` with release job (trigger: tags matching `v*`). Upload artifacts (DMG, MSI, DEB) to GitHub Releases. Generate release notes from git log. Require manual tag creation (no auto-release).
  
  **Agent**: `git` | **Wave**: 7 | **Blocks**: 43 | **Blocked By**: 39, 40, 41, 7
  
  **QA**: Create tag `v0.1.0`, push, verify GitHub Release created with all 3 installers attached. Evidence: task-42-github-release.txt
  
  **Commit**: `ci: automate GitHub release creation`

- [ ] 43. Landing Page + Download Links

  **What to do**: Create simple HTML landing page: hero section (DB Eagle logo + tagline), features list, download buttons (macOS, Windows, Linux), GitHub link, screenshot gallery. Host on GitHub Pages or Vercel. Update README with landing page link.
  
  **Agent**: `writing` | **Wave**: 7 | **Blocks**: — | **Blocked By**: 42
  
  **QA**: Visit landing page, click download links, verify redirect to GitHub Releases. Evidence: task-43-landing-page.png
  
  **Commit**: `docs: create landing page with download links`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Rejection → fix → re-run.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, run gradle task, execute query). For each "Must NOT Have": search codebase for forbidden patterns (visual query builder UI, import features, SSH tunnel code) — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan (DMG/MSI/DEB built, PostgreSQL + SQLite working, features implemented).
  
  **Acceptance**: Run `find . -name "*.kt" | xargs grep -l "VisualQueryBuilder\|ImportWizard\|SSHTunnel\|MigrationTool"` → empty result. Run `ls -la .sisyphus/evidence/task-*/` → all evidence files present. Run `./gradlew packageDmg packageMsi packageDeb` → all succeed. Run `./gradlew test` → >80% coverage.
  
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | Evidence [N files] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  
  Run `./gradlew build test detekt ktlintCheck`. Review all changed files for: hardcoded credentials, `println` in production code, empty catch blocks, `!!` null assertions, `TODO`/`FIXME` comments, unused imports, magic numbers. Check AI slop: excessive comments, over-abstraction (5+ interface layers), generic names (data/result/item/temp/manager/service without context).
  
  **Acceptance**: Run `./gradlew detekt ktlintCheck` → zero errors. Run `grep -r "password.*=.*\"" src/` → no hardcoded credentials. Run `grep -r "println\\|TODO\\|FIXME" src/` → zero occurrences. Run `./gradlew test` → all pass.
  
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT: APPROVE/REJECT`

- [ ] F3. **Real Manual QA** — `unspecified-high` (+ `playwright` skill if UI automation possible)
  
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration: create connection → browse schema → execute query → edit data → export → save to favorites → reload → verify history. Test edge cases: empty database, invalid credentials, 10k row result set, rapid connection open/close, schema with 100+ tables. Save to `.sisyphus/evidence/final-qa/`.
  
  **Acceptance**: Run application via `./gradlew run`. Complete workflow: create PostgreSQL connection to test DB → browse public schema → execute `SELECT * FROM users` → edit row → export CSV → verify file contents → save query to favorites → restart app → verify favorite persists → check history. Capture screenshots for each step.
  
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | Evidence [N screenshots] | VERDICT: APPROVE/REJECT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  
  For each task: read "What to do", read actual diff (`git log --all --oneline | head -50`, `git diff --stat origin/main`). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance: search for visual query builder code, import features, SSH tunneling, theme switching, localization files. Detect cross-task contamination: Task N touching Task M's files without dependency. Flag unaccounted changes.
  
  **Acceptance**: Run `git log --oneline --all | wc -l` → verify commit count reasonable. Run `find src/ -name "*QueryBuilder.kt" -o -name "*Import*.kt" -o -name "*SSH*.kt" -o -name "*i18n*"` → empty result. Run `git diff --stat main` → verify all changes map to planned tasks.
  
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | Forbidden [CLEAN/N violations] | VERDICT: APPROVE/REJECT`

---

## Commit Strategy

- **Wave 1**: Each task commits separately
  - T1: `feat(project): initialize Compose Desktop project with Gradle config`
  - T2: `feat(domain): add core domain models (Connection, Query, Schema)`
  - T3: `feat(driver): define database driver abstraction interface`
  - T4: `feat(security): implement AES-GCM credential encryption`
  - T5: `feat(di): setup Koin dependency injection structure`
  - T6: `feat(test): add kotlin.test infrastructure with TestContainers`
  - T7: `ci: configure GitHub Actions for multi-OS builds`

- **Wave 2**: Each task commits separately
  - T8: `feat(pool): implement HikariCP connection pool wrapper`
  - T9: `feat(driver): add PostgreSQL driver with Exposed ORM`
  - T10: `feat(driver): add SQLite driver with Exposed ORM`
  - T11: `feat(registry): implement driver registry and plugin loading`
  - T12: `feat(profile): add connection profile persistence with encryption`
  - T13: `feat(query): implement query execution engine with pagination`

- **Wave 3**: Group UI commits by component type (2-3 tasks each)
  - T14-15: `feat(ui): add main window scaffold and navigation structure`
  - T16: `feat(ui): add connection manager UI components`
  - T17: `feat(ui): add SQL editor with syntax highlighting`
  - T18: `feat(ui): add result grid component with pagination`
  - T19: `feat(ui): add schema browser tree component`
  - T20: `feat(ui): add export dialog with format selection`

- **Wave 4**: Each integration task commits separately
  - T21: `feat(integration): connect connection manager UI to DB layer`
  - T22: `feat(integration): integrate SQL editor with query execution`
  - T23: `feat(integration): integrate schema browser with DB metadata`
  - T24: `feat(edit): implement inline data editing with SQL UPDATE`
  - T25: `feat(history): add query history persistence and UI`
  - T26: `feat(favorites): implement favorites system with tagging`
  - T27: `feat(export): integrate export functionality with file output`

- **Wave 5**: Each feature commits separately
  - T28: `feat(er): add ER diagram generator for FK relationships`
  - T29: `feat(er): implement ER diagram renderer with Compose Canvas`
  - T30: `feat(session): add multi-connection session management`
  - T31: `feat(ux): add error handling with toasts and dialogs`
  - T32: `feat(settings): implement application settings (limits, timeouts)`

- **Wave 6**: Group polish commits
  - T33: `perf(pool): optimize connection pooling and add leak detection`
  - T34: `perf(ui): improve UI responsiveness with async loading`
  - T35: `feat(a11y): add keyboard shortcuts and accessibility basics`
  - T36: `perf(memory): optimize large result set handling`
  - T37: `feat(brand): add application icon and branding assets`
  - T38: `feat(observability): add crash reporting and logging`

- **Wave 7**: Each packaging task commits separately
  - T39: `build(macos): configure DMG packaging and signing`
  - T40: `build(windows): configure MSI packaging and signing`
  - T41: `build(linux): configure DEB and RPM packaging`
  - T42: `ci: automate GitHub release creation`
  - T43: `docs: create landing page with download links`

---

## Success Criteria

### Verification Commands
```bash
# Build all platform packages
./gradlew packageDmg packageMsi packageDeb
# Expected: BUILD SUCCESSFUL, installers in build/compose/binaries/main/

# Run all tests
./gradlew test
# Expected: Tests pass, >80% coverage

# Verify PostgreSQL connection
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=test postgres:15
# Launch app, create connection (localhost:5432, user: postgres, pass: test)
# Expected: Connection succeeds, schema visible

# Execute query
# In app: run "SELECT version();"
# Expected: Results display PostgreSQL version

# Export results
# In app: export results to CSV
# Expected: File downloads to ~/Downloads/query_results.csv

# Check package sizes
ls -lh build/compose/binaries/main/
# Expected: DMG ~120MB, MSI ~110MB, DEB ~115MB
```

### Final Checklist
- [ ] All "Must Have" features implemented and verified
- [ ] All "Must NOT Have" features confirmed absent (grep codebase)
- [ ] All tests pass (`./gradlew test`)
- [ ] Code coverage >80% on core modules
- [ ] DMG installer works on macOS 12+
- [ ] MSI installer works on Windows 10+
- [ ] DEB package works on Ubuntu 20.04+
- [ ] PostgreSQL connection succeeds to local/remote databases
- [ ] SQLite connection opens `.db` files successfully
- [ ] Query execution displays results in <2 seconds for 1000 rows
- [ ] Schema browser loads 50-table database in <3 seconds
- [ ] Data editing commits changes to database
- [ ] Query history persists across application restarts
- [ ] Favorites system saves and loads queries
- [ ] ER diagram renders FK relationships correctly
- [ ] CSV/JSON/SQL export produces valid output files
- [ ] Application memory usage <500MB with 5 connections
- [ ] Application starts in <5 seconds cold boot
- [ ] No hardcoded credentials in codebase
- [ ] No `TODO`/`FIXME` comments remaining
- [ ] GitHub Actions builds pass on all platforms
- [ ] Landing page hosted with download links
