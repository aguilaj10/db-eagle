## Decisions

_Append-only. Record decisions that affect implementation approach._

### Task 2 - Core Domain Models

#### Decision: Use kotlinx.serialization for Model Persistence
- **Chosen**: kotlinx.serialization JSON
- **Alternatives**: Jackson (verbose Gradle config), GSON (not Kotlin-first), Protocol Buffers (overkill for MVP)
- **Rationale**: Type-safe via @Serializable, multiplatform-ready, Kotlin standard, smaller than Jackson
- **Impact**: Enables easy roundtrip testing and future persistence layer

#### Decision: Sealed Class for DatabaseType vs Enum
- **Chosen**: Sealed class (extensible)
- **Rationale**: Allows future plugins to register custom database types without modifying core enum
- **Trade-off**: Slightly more verbose syntax vs enum, but architecture-ready for plugin system

#### Decision: Flat Package Structure (com.dbeagle.model.*)
- **Chosen**: One file per model class
- **Rationale**: IDE navigation, single responsibility, easier to discover/find models
- **Impact**: Slightly more files, but cleaner dependencies and test organization

#### Decision: Immutable Models (val properties only)
- **Chosen**: All properties val, no setters
- **Rationale**: Pure data structures, thread-safe, matches plan requirement
- **Trade-off**: Copy pattern or builders needed for modifications (acceptable post-MVP)

#### Decision: Generic QueryResult vs Database-Specific
- **Chosen**: Generic rows as List<Map<String, String>>
- **Rationale**: Decoupled from database-specific type systems, supports multiple DB types
- **Trade-off**: Type information lost (stored in columnNames), acceptable for MVP grid display


### Task 8 - HikariCP Connection Pool Wrapper Design Decisions

#### Decision 1: Singleton Object Pattern for Pool Manager
**Chosen**: Kotlin `object DatabaseConnectionPool` (singleton)
**Alternatives Considered**:
- DI-injected service class
- Companion object in driver implementation
- Global function with internal state

**Rationale**:
- Stateless utility managing pools (no per-instance state needed)
- Single shared pool registry across entire application
- Thread-safe via ConcurrentHashMap (no manual synchronization)
- Simpler than DI injection for stateless manager
- Immediate availability without initialization ceremony

#### Decision 2: Plaintext Password as Function Parameter
**Chosen**: `getConnection(profile, decryptedPassword: String)`
**Alternatives Considered**:
- Accept encrypted password + decrypt internally
- Accept EncryptedData + masterPassword + decrypt internally
- Store decrypted password in ConnectionProfile

**Rationale**:
- Separation of concerns: Pool doesn't handle encryption/decryption
- Caller controls when/where decryption happens
- Reduces plaintext credential lifetime (not stored in pool)
- HikariCP requires plaintext for JDBC connection anyway
- Avoids coupling pool layer to encryption module

**Security Note**: Caller must handle decryption; pool receives plaintext just before creating HikariConfig, then forgets it (not retained after datasource creation).

#### Decision 3: ConcurrentHashMap for Pool Storage
**Chosen**: `ConcurrentHashMap<String, HikariDataSource>`
**Alternatives Considered**:
- `HashMap` with synchronized methods
- `Collections.synchronizedMap()`
- Guava Cache with expiration

**Rationale**:
- Thread-safe without manual synchronization
- Lock-free reads (better concurrency than synchronized)
- computeIfAbsent() provides atomic lazy initialization
- Standard Java concurrent collections pattern
- No external dependencies (Guava)

#### Decision 4: Fixed Pool Configuration (No Per-Profile Customization)
**Chosen**: Hardcoded defaults in constants
**Alternatives Considered**:
- Read from ConnectionConfig model
- Accept config parameters in getConnection()
- Allow per-profile pool size overrides

**Rationale**:
- Task spec: "Defaults: maxPoolSize=10, connectionTimeout=30s..." (explicit defaults required)
- Task spec: "must not" section warns against extra pooling knobs unless asked
- ConnectionConfig model doesn't have pool-level settings (removed in Task 2 cleanup)
- Desktop application use case: Conservative defaults sufficient
- Future enhancement: Can add customization layer if needed

**Default Values Chosen**:
- maxPoolSize=10: Desktop app, single user, prevents resource exhaustion
- connectionTimeout=30s: Reasonable wait for busy pool
- idleTimeout=10min: Keep connections alive but not indefinitely
- maxLifetime=30min: Force refresh to handle DB restarts gracefully
- leakDetectionThreshold=60s: Catch unreleased connections early in development

#### Decision 5: Pool Exhaustion Error with Retry Guidance
**Chosen**: IllegalStateException with pool stats + retry suggestion
**Alternatives Considered**:
- Re-throw original SQLException
- Custom PoolExhaustedException
- Return null + log error

**Rationale**:
- Clear actionable message for caller
- Includes real-time pool stats (active, idle, total, waiting)
- Suggests retry after active connections released
- Wrapped exception (original cause preserved)
- Kotlin idiomatic (exceptions for exceptional conditions)

**Error Message Format**:
```
Failed to acquire connection from pool for profile 'MyDB'. 
Pool may be exhausted (consider retrying after active connections are released). 
Current pool stats: active=10, idle=0, total=10, waiting=3
```

#### Decision 6: Idempotent Close Operations
**Chosen**: All close methods safe to call multiple times
**Alternatives Considered**:
- Throw exception if pool already closed
- Track closed state and skip silently
- Log warning on redundant close

**Rationale**:
- Application shutdown code often calls cleanup multiple times
- No side effects from closing non-existent pool
- ConcurrentHashMap.remove() returns null if key missing (no exception)
- HikariDataSource.close() is idempotent by design
- Simplifies caller code (no need to check hasPool() before close)

#### Decision 7: Build JDBC URL Internally (Not from ConnectionProfile)
**Chosen**: Construct URL from profile.type + host + port + database
**Alternatives Considered**:
- Store full JDBC URL in ConnectionProfile
- Accept pre-built JDBC URL in getConnection()
- Use driver-specific URL builders

**Rationale**:
- ConnectionProfile stores structured data (host, port, database), not URL strings
- URL construction logic centralized in pool manager
- Sealed class pattern (DatabaseType) allows clean when-expression
- Easy to add new database types (extend sealed class + add URL logic)
- Keeps model layer clean (no JDBC-specific strings)

**URL Patterns**:
- PostgreSQL: `jdbc:postgresql://{host}:{port}/{database}`
- SQLite: `jdbc:sqlite:{database}` (database field = file path)

#### Decision 8: TestContainers Direct Management (Not Shared Utility)
**Chosen**: Each test class manages own PostgreSQL container
**Alternatives Considered**:
- Reuse DatabaseTestContainers from core module
- Shared container across all data tests
- JUnit5 @Container extension

**Rationale**:
- Test isolation: Each test class controls container lifecycle
- No cross-module test dependencies (data module doesn't depend on core/test)
- Flexible container configuration per test class if needed
- Clear ownership: @BeforeTest starts, @AfterTest stops
- Avoids shared mutable state across test classes

**Future Enhancement**: Extract to shared test utility if multiple test classes need containers.

#### Decision 9: No DI Module Registration Yet
**Chosen**: DatabaseConnectionPool not added to DataModule (Koin)
**Alternatives Considered**:
- Add `single { DatabaseConnectionPool }` to DataModule
- Create factory for pool manager
- Inject ConnectionProfile → Connection factory

**Rationale**:
- Task scope: Implement pool wrapper (not full DI integration)
- Singleton object accessible directly without DI
- Future tasks (9/10: Driver implementations) will determine DI needs
- Avoids premature abstraction
- Can add DI binding later if needed


### Task 9 - PostgreSQL Driver (Exposed)

#### Decision: Provide plaintext password via ConnectionProfile.options["password"]
- **Chosen**: Driver reads plaintext password from `ConnectionProfile.options["password"]` during `connect()`.
- **Rationale**: Core models store `encryptedPassword` but encryption/decryption wiring is not implemented yet; this unblocks driver + integration tests without changing core.
- **Trade-off**: Plaintext in-memory configuration is less secure; temporary until credential storage/decryption task lands.

### Task 10 - SQLite Driver (Exposed)

#### Decision: Keep a single JDBC connection alive for :memory:
- **Chosen**: SQLiteDriver opens one JDBC connection on connect; Exposed uses a DataSource that returns a non-closing proxy over that connection.
- **Rationale**: SQLite `:memory:` database lifetime is tied to the connection; Exposed's default transaction lifecycle closes connections.
- **Trade-off**: Driver manages connection lifecycle explicitly; concurrency is limited to a single connection (acceptable for MVP and tests).

## Task 14: Compose Main Window Scaffold
- Kept the Scaffold structure contained in `App.kt` for now. As UI grows (Task 15+), `App.kt` might become bloated and should be refactored into `ui/` directories with separate composables.
- Opted for Material 3 `TopAppBar`, requiring `@OptIn(ExperimentalMaterial3Api::class)`.
- Used `Row` to split main content, and `weight(1f)` for center content, `width(250.dp)` for left sidebar, achieving the specified split.

## Navigation Structure
Decided to keep the sidebar and content area components locally in App.kt for now (as simple Column and Box structures) since we are still building out the skeleton. Introduced a NavigationTab enum to cleanly type check and represent the five required tabs, preventing string-based UI switching.
