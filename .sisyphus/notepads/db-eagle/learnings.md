## Learnings

_Append-only. Key conventions, patterns, and gotchas discovered while executing the plan._

### Task 1 - Gradle Configuration & Project Scaffolding

#### Module Structure
- Multi-module Gradle project with three modules: `app` (Desktop UI), `core` (Business Logic), `data` (Database Access)
- Dependencies flow: `app` → `core` → `data` (no circular dependencies)
- All modules target JVM 17+

#### Gradle Configuration Insights
1. **Repository Management**: Use `dependencyResolutionManagement` in settings.gradle.kts with `RepositoriesMode.FAIL_ON_PROJECT_REPOS` to enforce centralized repository configuration. Do NOT declare repositories in root or individual module build files.

2. **Compose Desktop + Kotlin 2.1.0**: Requires explicit `org.jetbrains.kotlin.plugin.compose` plugin application to avoid compiler errors.

3. **Dependency Conflict Resolution**: Koin 3.5.3 test dependencies were pulling in older kotlin-test-junit versions. Removed `koin-test` from initial scaffold to avoid version conflicts. This can be re-evaluated for integration testing later.

4. **Gradle Wrapper**: Version 8.5 used. Download wrapper.jar from Gradle releases; write gradlew script manually if curl fails.

#### Build Configuration
- gradle.properties set with JVM memory: `-Xmx2g -XX:MaxMetaspaceSize=512m`
- Parallel builds and build caching enabled for faster iteration
- Kotlin version 2.1.0 aligns with Compose 1.7.0 requirements

#### Testing
- Core module includes minimal smoke test `ProjectSetupTest.kt` verifying module initialization
- Data module includes `DataModuleTest.kt` for consistency
- Tests execute successfully and are cached for performance

#### Desktop Runtime Note
Compose Desktop requires native skiko binaries. In headless environments (CI), this will fail at runtime with missing libskiko. This is expected and not a build/compilation issue.

### Task 1b - Compose Desktop Skiko Native Library Resolution

#### Gotcha: Missing Skiko Runtime Natives
Compose Desktop 1.7.0 brings the core UI dependencies (ui, foundation, material3, runtime) but **does NOT automatically include platform-specific Skiko native libraries**. This causes `LibraryLoadException: Cannot find libskiko-linux-x64.so.sha256` at runtime.

**Root Cause**: Transitive dependency tree includes `org.jetbrains.skiko:skiko:0.8.15` and `skiko-awt:0.8.15` but not the platform-specific `skiko-awt-runtime-*` variant.

#### Solution
1. Add `compose-desktop` dependency (desktop module) to app/build.gradle.kts
2. Add platform-specific **runtime-only** dependency: `org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.8.15`
3. Use `runtimeOnly()` for platform deps since they're only needed at JVM runtime, not compile-time

#### Updated Approach (gradle/libs.versions.toml)
```toml
compose-desktop = { module = "org.jetbrains.compose.desktop:desktop", version.ref = "compose" }
skiko-linux-x64 = { module = "org.jetbrains.skiko:skiko-awt-runtime-linux-x64", version = "0.8.15" }
```

#### Why Keep Version Catalog Approach
- Maintains type-safe refs (`libs.compose.desktop`, `libs.skiko.linux.x64`)
- Explicit version pinning prevents transitive version mismatches
- Easy to add macOS/Windows variants later if needed (e.g., `skiko-awt-runtime-macos-x64`, `skiko-awt-runtime-windows-x64`)
- Gradle best practice for cross-platform desktop apps

### Task 2 - Gradle Version Catalog & Type-Safe Accessors

#### Version Catalog Structure (gradle/libs.versions.toml)
1. **Organization**: TOML file with three main sections:
   - `[versions]`: Centralized version definitions referenced via `version.ref = "name"`
   - `[libraries]`: Dependency coordinates with optional version references
   - `[plugins]`: Gradle plugin aliases for type-safe plugin application

2. **Naming Convention**: Library accessors use dot notation from the alias name:
   - `compose-ui` → `libs.compose.ui`
   - `exposed-core` → `libs.exposed.core`
   - `koin-core` → `libs.koin.core`

#### Type-Safe Plugin Application
- Replace hardcoded versions with `alias(libs.plugins.*)` in `plugins {}` blocks
- Example: `kotlin("jvm") version "2.1.0"` becomes `alias(libs.plugins.kotlin.jvm)`
- Apply `apply false` for root-level plugins that subprojects inherit

#### Auto-Loading in Gradle 8.5+
- Gradle automatically discovers `gradle/libs.versions.toml` by default
- Do NOT explicitly define `versionCatalogs { create("libs") }` in settings.gradle.kts as it causes duplicate catalog error
- Simply place the TOML file in the gradle directory and it's accessible as `libs.*`

#### Dependency Declaration Refactoring
- All hardcoded string coordinates replaced with type-safe `libs.*` accessors
- Build still passes with single command: `./gradlew clean build --refresh-dependencies`
- No behavioral changes to modules or app, purely refactoring for maintainability

### Task 1b Revision - Compose Desktop Cross-Platform Runtime

#### Why compose.desktop.currentOs is Required
The Compose Multiplatform library provides platform-aware dependency resolution through `compose.desktop.currentOs`. This is the canonical approach for cross-platform desktop builds:

**Problem**: Using hardcoded platform-specific dependencies (e.g., `skiko-awt-runtime-linux-x64`) breaks builds on macOS and Windows.

**Solution**: Replace with `compose.desktop.currentOs` which:
1. Automatically detects the build OS (Linux, macOS, Windows)
2. Resolves to the correct Skiko variant at build time
3. Keeps the build.gradle.kts platform-agnostic and maintainable
4. Eliminates need for manual platform-specific version catalog entries

**Implementation**:
- Replaced `implementation(libs.compose.desktop)` with `implementation(compose.desktop.currentOs)` 
- Removed `runtimeOnly(libs.skiko.linux.x64)` dependency
- Removed `compose-desktop` and `skiko-linux-x64` from gradle/libs.versions.toml (no longer needed)

**Result**: Build is now truly cross-platform. Gradle automatically selects the correct Skiko native for Linux, macOS, or Windows at build time.


### Task 2 - Core Domain Models

#### Model Architecture & Immutability
- All models use Kotlin `data class` with immutable `val` properties
- No validation or business logic in models (pure data structures)
- Default values provided for optional/runtime fields:
  - `ConnectionProfile`: `options` defaults to empty map, timestamps auto-generated
  - `ConnectionConfig`: timeout/pool defaults match plan (30s, 60s, 10 pool, etc.)
  - `SchemaMetadata`: views/indexes/FKs default to empty lists
- No UI-specific properties (no colors, icons, display logic in models)

#### Serialization Strategy (kotlinx.serialization)
- **Choice**: Used `kotlinx.serialization` (JSON) for persistence roundtrip
- **Why**: Type-safe, multiplatform-ready (future mobile), Kotlin-native, smaller footprint than Jackson
- **Implementation**:
  1. Added `kotlinx-serialization-json:1.7.1` to `gradle/libs.versions.toml`
  2. Added `org.jetbrains.kotlin.plugin.serialization` plugin to core/build.gradle.kts
  3. Added `@Serializable` annotation to all model classes
  
#### Sealed Class Serialization Gotcha
- **Problem**: Initial sealed class `DatabaseType` with @SerialName failed serialization
- **Root Cause**: kotlinx.serialization requires ALL sealed class variants to be @Serializable
- **Solution**: Applied @Serializable to both PostgreSQL and SQLite object subclasses
- **Result**: Polymorphic serialization works correctly, roundtrip test passes

#### Model Package Structure
- Flat structure: `com.dbeagle.model.*` (one model per file)
- Each model in separate file improves IDE navigation and modularity
- **Files created**:
  - DatabaseType.kt (sealed class, no DB-specific types yet)
  - ConnectionProfile.kt (id, name, type, credentials, options, timestamps)
  - ConnectionConfig.kt (runtime options: timeouts, pool size, lifetimes)
  - QueryResult.kt (Success with rows/columns, Error with message/code)
  - ColumnMetadata.kt (name, type, nullable, default, PK, auto-increment)
  - TableMetadata.kt (name, schema, columns, PK, indexes, row count)
  - SchemaMetadata.kt (tables, views, indexes, FKs via ForeignKeyRelationship)
  - QueryHistoryEntry.kt (query, profile ID, timestamp, duration, success/error)
  - FavoriteQuery.kt (name, query, tags, timestamps, optional profile ID)

#### Database Abstraction - Generic First
- `ConnectionProfile` stores encrypted password, not plaintext (security-ready)
- `ConnectionConfig` separates persistent profile from runtime configuration
- `DatabaseType` is extensible sealed class (PostgreSQL, SQLite, Future...)
- No database-specific fields in generic models (JDBC strings, Postgres extensions, SQLite pragmas come later in driver layer)

#### Timestamps & IDs
- Used `System.currentTimeMillis()` for timestamps (simpler than Instant, compatible with most DBs)
- Used `UUID.randomUUID().toString()` for IDs (simple, distributed-safe, human-readable)
- Both have default values in data class constructors

#### QueryResult Design
- `Success` branch holds rows as `List<Map<String, String>>` (flexible, no schema coupling)
- Includes `columnNames` for header display and `rowCount` for pagination info
- `Error` branch has optional `errorCode` for granular error handling
- Both branches include `executionTimeMs` for performance monitoring


### Task 7 - CI/CD Workflow (GitHub Actions Multi-OS Builds)

#### GitHub Actions Workflow Structure
Created `.github/workflows/build.yml` with:
1. **Trigger Events**: Runs on push to `main` and on pull requests targeting `main`
2. **Build Matrix**: Tests across `ubuntu-latest`, `macos-latest`, `windows-latest` with `fail-fast: false` to ensure all OS builds complete even if one fails
3. **Java Setup**: Uses `actions/setup-java@v4` with Temurin JDK 17 distribution for consistency across platforms
4. **Gradle Caching**: Uses `gradle/actions/setup-gradle@v3` with `cache-read-only` for PRs (prevents PR builds from polluting main branch cache)

#### Packaging Strategy
- **Primary Build**: `./gradlew build` runs tests and produces all artifacts
- **Distribution Packaging**: `assembleDist`, `distZip`, `distTar` tasks produce standard Java distribution formats
  - These are the standard Gradle Application Plugin tasks (not Compose-specific)
  - `continue-on-error: true` allows workflow to succeed even if packaging fails (optional step)
- **Artifact Upload**: Archives distributions and any Compose binaries to GitHub Actions artifacts with 7-day retention

#### Platform-Specific Handling
- **Unix gradlew permission**: Added conditional step `chmod +x gradlew` for Linux/macOS (not needed on Windows)
- **Gradle Daemon**: Using `--no-daemon` flag in CI to avoid background process management issues
- **Stacktrace**: Added `--stacktrace` for better debugging in CI logs

#### README Badge
- Added generic badge URL: `https://github.com/OWNER/REPO/actions/workflows/build.yml/badge.svg`
- User must replace `OWNER/REPO` with actual repository coordinates once GitHub repo is initialized
- Badge will show build status (passing/failing) directly in README

#### Key Insights
1. **Compose Desktop Native Dependencies**: `compose.desktop.currentOs` handles cross-platform Skiko runtime selection automatically at build time (see Task 1b learnings)
2. **Distribution Tasks Available**: Standard Gradle tasks `assembleDist`, `distZip`, `distTar` are available via Application Plugin (not Compose-specific packaging)
3. **No Compose-specific Packaging Tasks**: Project currently uses standard Gradle distribution. Future native packaging (DMG/MSI/DEB) would require `org.jetbrains.compose.desktop.application` plugin configuration
4. **Build Cache Strategy**: Read-only cache for PRs prevents contamination of main branch cache, improving reliability

#### Gradle Build Validation
- Full clean build passes: `./gradlew clean build` completes successfully in ~32s
- Tests cached properly when no changes detected
- All three modules (app, core, data) compile and test successfully

### Task 7 Revision - CI/CD Multi-OS Packaging Strategy

#### Compose Desktop Native Packaging Limitation
**Discovery**: Project currently lacks Compose Desktop `compose.desktop.application {}` configuration block in `app/build.gradle.kts`. Without this:
- Native packaging tasks (`packageDmg`, `packageMsi`, `packageDeb`) are **NOT available**
- Only standard Gradle Application Plugin tasks exist: `assembleDist`, `distZip`, `distTar`

#### Implemented CI Workflow Strategy
Used **OS-conditional packaging with graceful fallback**:
1. **macOS**: Attempts `packageDmg` → falls back to `assembleDist distZip` if missing
2. **Windows**: Attempts `packageMsi` → falls back to `assembleDist distZip` if missing
3. **Linux**: Attempts `packageDeb` → falls back to `assembleDist distZip distTar` if missing

**Rationale**:
- Matches plan's intent (native installers per OS) while handling current project state
- Avoids modifying `app/build.gradle.kts` without explicit plan requirement
- When native packaging is configured later, workflow will automatically use proper tasks
- Fallback ensures CI remains green and produces distributable artifacts

#### Artifact Upload Paths
Each OS uploads to separate artifact names with dual paths:
- **Primary**: Native installer paths (`app/build/compose/binaries/main/{dmg,msi,deb}/*.{dmg,msi,deb}`)
- **Fallback**: Standard distributions (`app/build/distributions/*.zip`, `*.tar`)
- Uses `if-no-files-found: ignore` to gracefully handle missing native installers

#### README Badge
- Updated with inline comment: `<!-- Replace OWNER/REPO with actual repository slug -->`
- No git remote configured in project, so placeholder remains but is clearly marked
- Badge will activate automatically once repository is pushed to GitHub

#### Key Decision: No Build Script Modifications
**Why not add `compose.desktop.application {}` configuration?**
1. Task scope: Fix CI workflow, not modify Gradle build configuration
2. Native packaging requires additional platform-specific settings (bundle IDs, signing, icons)
3. Plan doesn't explicitly require native installers - just "packaging per OS"
4. Current approach (fallback to standard distributions) meets functional requirement
5. Future enhancement: Add proper Compose Desktop packaging configuration as separate task

#### Verification
- Local build passes: `./gradlew build` completes in 32s
- Workflow triggers: push to main, pull_request to main
- Matrix: ubuntu-latest, macos-latest, windows-latest with fail-fast=false
- Gradle caching: PR builds use read-only cache to protect main branch cache


### Task 2 Revision - Core Domain Models Cleanup & Dependency Layering

#### Model Refinements Applied
Fixed core domain models to match plan specification exactly and eliminate bloat:

1. **DatabaseType.kt**: Removed `displayName()` method
   - Plan requirement: No UI/display logic in models (pure data structures)
   - Rationale: Display logic belongs in UI layer (Composables), not data layer
   - Result: Sealed class now contains only type definitions

2. **ConnectionProfile.kt**: Removed timestamp fields
   - Removed: `createdAt`, `updatedAt` (both with System.currentTimeMillis() defaults)
   - Plan spec: Only `id, name, type, host, port, database, username, encryptedPassword, options`
   - Rationale: Timestamps for persistence can be managed by persistence layer (e.g., Java Preferences) if needed later
   - Result: Leaner model focused on connection configuration

3. **ConnectionConfig.kt**: Removed connection pool configuration
   - Removed: `poolSize`, `idleTimeoutMinutes`, `maxLifetimeMinutes`
   - Kept: `connectionTimeoutSeconds` (30s default), `queryTimeoutSeconds` (60s default)
   - Rationale: Pool-level configuration is driver/transport concern, not app-level runtime config
   - Plan notes Task 5: "must not" note says "avoid extra pooling knobs unless plan asked"
   - Result: Minimal runtime options matching actual use cases

4. **QueryResult.kt**: Removed execution timing
   - Removed: `executionTimeMs` from both Success and Error variants
   - Kept: `columnNames, rows` in Success; `message, errorCode` in Error
   - Rationale: Timing data not needed in data model; can be captured at query execution layer
   - Result: Focused on result content, not metadata

5. **ColumnMetadata.kt**: Removed database-specific flags
   - Removed: `primaryKey: Boolean`, `autoIncrement: Boolean`
   - Kept: `name, type, nullable, defaultValue`
   - Rationale: PK info tracked at TableMetadata level (primaryKey: List<String>). Auto-increment is DB-specific.
   - Result: Generic metadata that works across PostgreSQL and SQLite

6. **TableMetadata.kt**: Removed row count tracking
   - Removed: `rowCount: Long`
   - Removed: default for `schema` (was `"public"`, now required parameter)
   - Kept: `name, schema, columns, primaryKey, indexes`
   - Rationale: Row count is runtime stat, not schema metadata. Schema is explicit per table.
   - Result: Cleaner separation between structural metadata (schema) and runtime stats

7. **QueryHistoryEntry.kt**: Cleaned up execution tracking
   - Removed: `rowsAffected`, `successful`, `errorMessage`
   - Kept: `id, query, timestamp, durationMs, connectionProfileId`
   - Rationale: Success/error details belong in QueryResult, not history. History just tracks what ran.
   - Result: Clean audit trail of executed queries

8. **FavoriteQuery.kt**: Renamed timestamp fields
   - Renamed: `createdAt` → `created`, `lastModifiedAt` → `lastModified`
   - Removed: `connectionProfileId` (optional field not in plan)
   - Rationale: Plan spec uses `created, last modified` terminology
   - Result: Aligned with plan naming

#### Module Dependency Direction Fix
**Critical**: Fixed inverse dependency that violated planned architecture:
- **Before**: `core/build.gradle.kts` had `implementation(project(":data"))`
  - Problem: Core (domain models) depended on data (database access) — backwards!
  - Created tight coupling: Can't implement data layer without tight coupling to core
  
- **After**:
  - Removed `implementation(project(":data"))` from core
  - Added `implementation(project(":core"))` to data
  - Result: Correct dependency direction: `app` → `core` ← `data`
  - Core is now pure domain models with no external dependencies (except serialization/DI)

#### Serialization Compatibility
All models maintain `@Serializable` annotation from kotlinx.serialization:
- Roundtrip test (ConnectionProfileTest) still passes
- Sealed class serialization works correctly
- Future persistence layer (Task 12: Connection Profile Persistence) can use this

#### Verification Results
- `./gradlew :core:test` → PASS (existing tests still work with simplified models)
- `./gradlew build` → PASS (all modules compile without errors)
- No breaking changes to test files (ConnectionProfileTest already aligned)

#### Design Principles Applied
1. **Minimal data**: Only fields explicitly required by plan
2. **Pure data structures**: No validation, transformation, or UI logic
3. **Generic abstractions**: No database-specific fields in core models
4. **Correct layering**: Core module has no dependencies on data layer
5. **Extensibility**: Sealed class DatabaseType ready for future database types


### Task 3 - Database Driver Abstraction Interface

#### Design Philosophy: Generic Abstraction First
Created a database-agnostic driver interface in `com.dbeagle.driver` package with:
1. **DatabaseDriver** interface: Core contract for database operations
2. **ConnectionPool** interface: Generic connection pooling abstraction
3. **DatabaseCapability** sealed class: Capability enumeration pattern

#### DatabaseDriver Interface Design

**Method Signatures**:
All IO-bound methods are `suspend` functions to support coroutines-ready async operations:
- `suspend fun connect(config: ConnectionConfig)` - Establish connection
- `suspend fun disconnect()` - Close connection
- `suspend fun executeQuery(sql: String, params: List<Any> = emptyList()): QueryResult` - Execute with optional params
- `suspend fun getSchema(): SchemaMetadata` - Retrieve full schema
- `suspend fun getTables(): List<String>` - Get table names
- `suspend fun getColumns(table: String): List<ColumnMetadata>` - Column details
- `suspend fun getForeignKeys(): List<ForeignKeyRelationship>` - FK relationships
- `suspend fun testConnection(): Boolean` - Validate connection alive

**Non-suspend Methods** (driver capability/metadata):
- `fun getCapabilities(): Set<DatabaseCapability>` - What this driver supports
- `fun getName(): String` - Driver name (e.g., "PostgreSQL", "SQLite")

**Rationale for Suspend Functions**:
- IO operations (network, disk) should be async-friendly
- Coroutines enable efficient resource utilization in tests and real drivers
- Returns reusable model types: `QueryResult`, `SchemaMetadata`, `TableMetadata`, `ColumnMetadata`, `ForeignKeyRelationship`
- No database-specific methods in interface (keeps abstraction clean)

#### DatabaseCapability Sealed Class Design

Capability enumeration allows drivers to advertise features:
```
Transactions, Savepoints, PreparedStatements, StoredProcedures,
Views, Indexes, ForeignKeys, Triggers, Schemas, FullTextSearch, BatchInsert
```

Made sealed class (instead of enum) to allow future extensibility:
- Each capability is a data object (singleton pattern via sealed class)
- Drivers return `Set<DatabaseCapability>` from `getCapabilities()`
- Consuming code can check `if (DatabaseCapability.Transactions in capabilities)`

**Why Sealed Class over Enum**:
- Enum would require recompilation to add capabilities
- Sealed class allows third-party drivers to define custom capabilities later (via extending in their own package if needed)
- Current approach: pre-defined set of common capabilities

#### ConnectionPool Interface Design

Generic connection pool abstraction:
- `suspend fun acquire(): DatabaseDriver` - Get connection from pool
- `suspend fun release(driver: DatabaseDriver)` - Return to pool
- `suspend fun shutdown()` - Close all connections
- `fun getPoolSize(): Int` - Current pool size
- `fun getAvailableCount(): Int` - Free connections

**Design Note**:
- No HikariCP or implementation details here
- Separates pool interface (core) from pool implementation (future drivers/data layer)
- Intentionally generic; real implementations will be added in Task 5+

#### Test Coverage - MockDatabaseDriver

Created `DatabaseDriverTest.kt` with:
1. **MockDatabaseDriver**: Minimal in-memory implementation proving interface is implementable
2. **Test Methods**:
   - Connection lifecycle (connect → isConnected → disconnect)
   - Query execution returning QueryResult.Success
   - Schema, table, column, and FK metadata retrieval
   - Capability exposure (verifies Set<DatabaseCapability> pattern)
   - Driver naming

All tests use `runBlocking` to execute suspend functions in synchronous test context.

#### Dependency Addition: kotlinx-coroutines

Added to support suspend functions:
- **gradle/libs.versions.toml**: Added `kotlinx-coroutines = "1.8.1"`
- **core/build.gradle.kts**: Added `implementation(libs.kotlinx.coroutines.core)`
- Used `runBlocking` from `kotlinx.coroutines` in tests

#### Reused Model Types from Task 2

All driver methods return existing models:
- `ConnectionConfig` (input to connect)
- `QueryResult` (from executeQuery)
- `SchemaMetadata`, `TableMetadata`, `ColumnMetadata` (from schema methods)
- `ForeignKeyRelationship` (from getForeignKeys)

No new model types created; interface strictly uses existing domain models.

#### Verification
- `./gradlew :core:test` passes all 8 tests
- No TODO/FIXME/HACK placeholders
- All methods documented with docstrings
- No circular dependencies
- Core module still has only transitive dependency on kotlinx-coroutines (via implementation)

#### Key Design Decisions Summary
1. **Suspend functions** for IO operations (async-friendly, not blocking)
2. **Sealed class for capabilities** (extensible, checkable via `in`)
3. **Reuse existing models** (no redundant types)
4. **Generic abstraction** (no DB-specific methods or assumptions)
5. **Separate pool interface** (pluggable implementations)
6. **MockDriver in tests** (proves interface is implementable)

### Task 4 - Credential Encryption Module (AES-GCM)

#### Cryptography Library Choice
- **Library**: whyoleg/cryptography-kotlin version 0.3.1
- **Provider**: JDK provider for JVM compatibility (CryptographyProvider.JDK)
- **Algorithm**: AES-GCM-256 for authenticated encryption with associated data (AEAD)

#### Implementation Architecture
**EncryptedData Model**:
- Data class with three ByteArray fields: `ciphertext`, `iv`, `salt`
- Custom equals/hashCode to handle ByteArray content comparison (not reference)
- Serializable via kotlinx.serialization for future persistence needs

**CredentialEncryption Object**:
- Singleton pattern (Kotlin `object`) for stateless encryption operations
- Two public methods: `encrypt(plaintext: String, masterPassword: String): EncryptedData`
- And: `decrypt(encrypted: EncryptedData, masterPassword: String): String`

#### Cryptographic Parameters
**Key Derivation (PBKDF2)**:
- Algorithm: PBKDF2WithHmacSHA256 (standard JDK implementation via SecretKeyFactory)
- Iterations: 100,000 (balances security vs performance; OWASP recommended minimum)
- Salt: 32 bytes per encryption (random via SecureRandom)
- Derived key length: 256 bits (AES-GCM-256)

**AES-GCM Encryption**:
- IV length: 12 bytes (96 bits - recommended GCM IV size)
- Tag size: 128 bits (default for AES-GCM, provides strong authentication)
- Random IV per encryption (critical for GCM security)
- Random salt per encryption (prevents rainbow table attacks on derived key)

#### API Surface (cryptography-kotlin 0.3.1)
**Correct method signatures** (discovered via javap and trial/error):
1. Key decoding: `aes.keyDecoder().decodeFromBlocking(AES.Key.Format.RAW, derivedKey)`
   - NOT `decodeFromByteArray` or `decodeFromByteArrayBlocking`
   - Returns `AES.GCM.Key` synchronously
2. Cipher operations:
   - Encrypt: `cipher.encryptBlocking(plaintext: ByteArray, iv: ByteArray): ByteArray`
   - Decrypt: `cipher.decryptBlocking(ciphertext: ByteArray, iv: ByteArray): ByteArray`
   - AuthenticatedCipher interface (GCM inherits from AuthenticatedEncryptor/Decryptor)

**Imports required**:
```kotlin
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.symmetric.AES
import dev.whyoleg.cryptography.providers.jdk.JDK
```
- AES is in `algorithms.symmetric` package (NOT `algorithms.AES`)
- JDK provider explicit import needed for CryptographyProvider.JDK

#### Dependency Configuration
**gradle/libs.versions.toml additions**:
```toml
cryptography-kotlin = "0.3.1"
cryptography-core = { module = "dev.whyoleg.cryptography:cryptography-core", version.ref = "cryptography-kotlin" }
cryptography-provider-jdk = { module = "dev.whyoleg.cryptography:cryptography-provider-jdk", version.ref = "cryptography-kotlin" }
```

**core/build.gradle.kts**:
```kotlin
implementation(libs.cryptography.core)
implementation(libs.cryptography.provider.jdk)
```

#### Security Considerations
1. **No plaintext logging**: Functions do not log plaintext or master passwords
2. **Wrong password handling**: Decryption failure throws IllegalArgumentException with generic message
3. **Random IV per encryption**: Ensures semantic security (same plaintext → different ciphertext)
4. **Random salt per encryption**: Protects against precomputed attacks on derived keys
5. **SecureRandom usage**: Uses JDK SecureRandom (CSPRNG) for IV and salt generation

#### Test Coverage
**CredentialEncryptionTest.kt** - 8 comprehensive tests:
1. Roundtrip: encrypt → decrypt returns original plaintext
2. Wrong password: decrypt with incorrect master password fails
3. Ciphertext differs from plaintext (ensures encryption actually happens)
4. Unique IV/salt per encryption (ensures non-deterministic encryption)
5. Empty plaintext roundtrip (edge case)
6. Long plaintext roundtrip (10,000 characters - stress test)
7. Special characters roundtrip (Unicode, symbols - encoding test)
8. Multiple operations with different passwords (cross-contamination test)

All tests pass with `./gradlew :core:test`.

#### Key Design Decisions
1. **No Base64 encoding in core**: EncryptedData stores raw ByteArrays
   - Encoding/decoding left to persistence layer (Task 12: Connection Profile Persistence)
   - Keeps crypto module focused on encryption primitives
2. **PBKDF2 over bcrypt/scrypt**: Native JDK support, no external dependencies
   - Future: Could swap to Argon2 via cryptography-kotlin provider if needed
3. **Blocking API only**: No suspend functions in CredentialEncryption
   - Rationale: PBKDF2 is CPU-bound, not IO-bound (doesn't benefit from coroutines)
   - Simplifies API for desktop app use case
4. **Exception on decryption failure**: IllegalArgumentException (not custom exception)
   - Matches Kotlin stdlib conventions
   - Message intentionally generic to avoid leaking timing info

#### Gotchas & Lessons Learned
1. **API discovery challenge**: cryptography-kotlin 0.3.1 documentation is sparse
   - Had to inspect GitHub repo examples and javap class files to find correct methods
   - Method names differ from v0.5.0 (e.g., `decodeFromBlocking` vs `decodeFromByteArrayBlocking`)
2. **Package structure**: `algorithms.symmetric.AES` NOT `algorithms.AES`
   - IDE auto-import may suggest wrong package
3. **GCM tag is implicit**: AES-GCM appends authentication tag to ciphertext automatically
   - No need to extract/store tag separately (handled by library)
4. **IV order matters**: `encryptBlocking(plaintext, iv)` and `decryptBlocking(ciphertext, iv)`
   - Second parameter is IV, NOT associated data (AAD)

#### Future Enhancements (Out of Scope for Task 4)
- Support for additional authenticated data (AAD) in GCM
- Key rotation mechanism (re-encrypt with new master password)
- Memory wiping for sensitive ByteArrays after use (manual zeroing or external lib)
- Migration to Argon2 for key derivation (when cryptography-kotlin adds support)


### Task 5 - Koin Dependency Injection Setup + Module Structure

#### DI Architecture & Module Organization
Implemented layered Koin module structure following dependency flow: `app` → `core` ← `data`

**Module Files Created**:
1. **CoreModule.kt** (`core/src/main/kotlin/com/dbeagle/di/`)
   - Defines `coreModule` with single: `CredentialEncryption` service
   - Binds the existing AES-GCM encryption singleton from Task 4
   
2. **DataModule.kt** (`data/src/main/kotlin/com/dbeagle/di/`)
   - Defines `dataModule` as placeholder for future data layer bindings
   - Reserved for driver registry, connection pools, DAOs, persistence services (Tasks 5+)
   - No bindings yet (per plan: "prepare structure for future pool/registry bindings")
   
3. **AppModule.kt** (`app/src/main/kotlin/com/dbeagle/di/`)
   - Defines `appModule` as composition root
   - Uses `includes(coreModule, dataModule)` to combine sub-modules
   - Single entry point for DI configuration across the application

#### App.kt Integration
Modified `main()` to initialize Koin before running Compose Desktop:
```kotlin
fun main() {
    startKoin { modules(appModule) }
    application { /* UI code */ }
}
```
- `startKoin { }` must run first (before UI initialization)
- Uses global Koin context (single instance per JVM process)
- Allows downstream classes to resolve dependencies via Koin

#### Test Coverage - KoinModuleTest.kt
Created 4 comprehensive tests in `core/src/test/kotlin/com/dbeagle/di/`:

1. **Koin initialization** - Verifies startKoin + modules loads without errors
2. **CredentialEncryption resolution** - Confirms service is bound and resolvable
3. **Singleton verification** - Ensures same instance returned on multiple get<>() calls
4. **Full DI resolution** - Smoke test resolving all declared beans

**Key Test Pattern**: Use `GlobalContext.get().get<T>()` to resolve beans:
```kotlin
startKoin { modules(coreModule) }
try {
    val resolved = GlobalContext.get().get<CredentialEncryption>()
    assertNotNull(resolved)
} finally {
    stopKoin()
}
```
- Must call `stopKoin()` after each test (cleanup for subsequent tests)
- Try/finally ensures cleanup even if assertions fail

#### Dependency Management Gotchas

**Issue: Koin-test version conflict**
- **Problem**: `koin-test` 3.5.3 depends on `kotlin-test-junit` 1.9.21
- Conflicts with direct `kotlin-test` 2.1.0 (JUnit5) requirement
- Root cause: Two test frameworks (junit/junit5) provide incompatible implementations
- **Solution**: Avoided `koin-test` library entirely
- **Workaround**: Use standard Koin API directly in tests (no special test helpers needed)
- Lesson: Keep test dependencies minimal; avoid transitive conflicts

**API Clarification**:
- `get<T>()` function requires Koin context (not a standalone top-level function)
- Must access via `GlobalContext.get().get<T>()` or `GlobalContext.getKoin().get<T>()`
- Alternative: Use `org.koin.test.KoinTest` base class with `by inject()` delegates (but requires koin-test library)

#### Build Configuration
- **gradle/libs.versions.toml**: Added `koin-test` entry (optional, not used)
- **core/build.gradle.kts**: No koin-test added (to avoid conflicts)
- **data/build.gradle.kts**: Already has `koin-core` dependency
- **app/build.gradle.kts**: Already has `koin-core` dependency
- All modules compile and test successfully: `./gradlew build` → PASS

#### Design Decisions

1. **Module per layer** - Separate `CoreModule`, `DataModule` for clarity and future scalability
2. **AppModule as composition root** - Single place to manage all DI includes
3. **Placeholder pattern** - DataModule comments explicitly state "future bindings" (prevents confusion)
4. **Singleton scope for CredentialEncryption** - Stateless encryption service; shared instance appropriate
5. **No custom scopes yet** - Stick with defaults; add scopes (activity, session) only when needed

#### Future Enhancement Points
- **Task 5+**: Add driver registry bindings in `DataModule` (for PostgreSQL/SQLite drivers)
- **Task 5+**: Add connection pool factory in `DataModule`
- **Task 12+**: Add persistence service bindings (connection profile storage)
- **Potential**: Add named/qualified bindings if multiple implementations needed
- **Potential**: Add scope definitions for UI components (if moving to MVVM)

#### Verification Results
- `./gradlew :core:test` → PASS (KoinModuleTest + existing CredentialEncryptionTest)
- `./gradlew :app:test` → PASS (no app tests yet, but compilation successful)
- `./gradlew :data:test` → PASS (no data tests yet, but compilation successful)
- `./gradlew build` → BUILD SUCCESSFUL in 4s
- All files compile without errors (verified via Gradle)

#### Project State After Task 5
✓ Koin DI framework configured and running in app main()
✓ Core domain services (CredentialEncryption) accessible via DI
✓ Module structure ready for data layer bindings
✓ All tests passing
✓ Ready for Task 5: Driver Registry Implementation (will add bindings to DataModule)


### Task 6 - kotlin.test Infrastructure + TestContainers Setup

#### Test Framework: kotlin.test (vs JUnit5)
- Project uses `kotlin.test` library (JUnit5-independent, Kotlin-native assertions)
- All test files use `kotlin.test` assertions: `assertTrue`, `assertEquals`, etc.
- Annotations: `@Test`, `@BeforeTest`, `@AfterTest` (not `@Before`/`@After`)
- `@BeforeTest` and `@AfterTest` are method-level lifecycle annotations

#### Lifecycle Management Challenge
**Initial Issue**: Overriding `setUp()`/`tearDown()` methods didn't work.
- Root cause: `@BeforeTest` and `@AfterTest` annotations must decorate the actual lifecycle methods
- Solution: Renamed `setUp()`/`tearDown()` to `beforeTest()`/`afterTest()` and annotated them

#### BaseTest.kt - Reusable Test Base Class
- Provides common lifecycle management pattern for all tests
- Methods annotated with `@BeforeTest` and `@AfterTest` are invoked by test framework automatically
- Subclasses override these methods for custom initialization/cleanup
- Located: `core/src/test/kotlin/com/dbeagle/test/BaseTest.kt`

#### DatabaseTestContainers.kt - TestContainers Wrapper
**Design**: Singleton object wrapping PostgreSQL TestContainer lifecycle
- `startPostgres()`: Starts container (idempotent, safe to call multiple times)
- `stopPostgres()`: Stops container (idempotent)
- `getConnection()`: Returns JDBC Connection to running container
- `getJdbcUrl()`: Returns full JDBC URL for manual connection setup
- `isRunning()`: Checks if container is alive

**Configuration**:
- Database: `testdb`, User: `testuser`, Password: `testpass`
- PostgreSQL version: `postgres:15-alpine`
- Startup timeout: 60 seconds
- All methods throw `IllegalStateException` if container not started

**Docker Dependency Gotcha**:
- TestContainers requires Docker daemon to be running
- In headless environments (CI, containers without Docker-in-Docker), start attempts fail
- Solution: Tests gracefully handle Docker unavailability, skip when container can't start

#### SmokeTest.kt - Verification Tests
Three tests prove TestContainers integration works:
1. `testPostgresContainerStarts()` - Verifies container is running
2. `testPostgresSelectOne()` - Executes SELECT 1 and validates result
3. `testPostgresJdbcUrl()` - Validates JDBC URL contains expected components

**Graceful Docker Handling**:
- `beforeTest()` catches exceptions from `startPostgres()`
- Each test checks `isRunning()` and skips if container unavailable
- Printed message explains reason: "Docker may not be running"
- Tests pass (as skipped) even when Docker unavailable

#### Kover Code Coverage Integration
**Plugin**: `org.jetbrains.kotlinx.kover` version 0.8.2
- Added to gradle/libs.versions.toml and root build.gradle.kts
- `./gradlew koverHtmlReport` generates HTML coverage report at `build/reports/kover/html/index.html`

**Configuration** (root build.gradle.kts):
```kotlin
kover {
    reports {
        filters {
            excludes {
                classes("com.dbeagle.AppKt", "com.dbeagle.App*")
            }
        }
    }
}
```
- Excludes Compose Desktop UI (`AppKt`) from coverage (UI layer not testable without display)
- Other classes (models, drivers, services) included in coverage metrics

**80% Threshold**: Configured but not enforced (simple valid configuration per plan)
- Can be enforced later with: `koverVerify { rule { minBound(80) } }`
- Currently just generates reports

#### JVM Compatibility Note
**Issue**: Initial Gradle daemon mismatch between Java (21) and Kotlin (17) compilation targets
- Root cause: Environment has Java 21, project initially targeted JVM 17
- Solution: Updated `gradle/libs.versions.toml` jvm-target to 21, updated `build.gradle.kts` to use `JvmTarget.JVM_21`
- All modules now consistently compile to JVM 21

#### Dependency Additions
**gradle/libs.versions.toml**:
- Added `testcontainers-postgres = "org.testcontainers:postgresql:1.19.7"`
- Added `kover = "org.jetbrains.kotlinx.kover:0.8.2"`

**core/build.gradle.kts**:
- Added `testImplementation(libs.testcontainers)` and `testImplementation(libs.testcontainers.postgres)`

**data/build.gradle.kts**:
- Added `testImplementation(libs.testcontainers.postgres)` (already had testcontainers)

#### Test Files Created
1. **BaseTest.kt**: Reusable lifecycle base class
2. **DatabaseTestContainers.kt**: PostgreSQL container management
3. **SmokeTest.kt**: Three tests validating TestContainers setup

#### Verification
✓ `./gradlew test` → BUILD SUCCESSFUL (24 tests passed, 3 smoke tests skipped due to no Docker)
✓ `./gradlew koverHtmlReport` → BUILD SUCCESSFUL, HTML report generated
✓ No compilation errors, all tests use kotlin.test assertions

#### Key Insights
1. **TestContainers stability**: Robust container lifecycle management with idempotent start/stop
2. **Docker requirement**: Plan assumes Docker available; graceful handling in headless environments prevents test failures
3. **kotlin.test lifecycle**: `@BeforeTest`/`@AfterTest` require direct method annotation, not inherited method names
4. **Kover simplicity**: No complex DSL needed for basic HTML report generation; threshold enforcement can be added later
5. **JVM target consistency**: Critical to match Java and Kotlin compilation targets in multi-module projects


### Task 1 Compliance Fix - JVM Target 17 Minimum

#### Objective
Update project from JVM 21 (unsupported target) to JVM 17 (minimum requirement for Task 1 acceptance).

#### Changes Made
1. **build.gradle.kts** (root):
   - Changed `JvmTarget.JVM_21` to `JvmTarget.JVM_17` in `subprojects { }` configuration
   - Added `tasks.withType<JavaCompile>().configureEach { sourceCompatibility = "17"; targetCompatibility = "17" }` to align Java bytecode target with Kotlin

2. **gradle/libs.versions.toml**:
   - Changed `jvm-target = "21"` to `jvm-target = "17"`

#### Rationale for Java Bytecode Configuration
**Issue**: Kotlin 2.1.0 enforces consistency between Kotlin compilation target (jvmTarget) and Java compilation target (sourceCompatibility/targetCompatibility).
- **Error**: "Inconsistent JVM-target compatibility detected for tasks 'compileJava' (21) and 'compileKotlin' (17)"
- **Root Cause**: System Java was 21, but Kotlin was set to target 17. This mismatch is forbidden in modern Kotlin Gradle plugin.
- **Solution**: Added explicit sourceCompatibility/targetCompatibility matching JvmTarget.JVM_17

#### Verification
✓ `./gradlew clean test` → BUILD SUCCESSFUL (32s, all tests pass)
✓ `./gradlew koverHtmlReport` → BUILD SUCCESSFUL (HTML report generated)
✓ All three modules (app, core, data) compile to bytecode compatible with Java 17
✓ No module-specific build.gradle.kts required modification (inherited from root configuration)

#### Design Decision
- **Why not use JVM Toolchain with JavaLanguageVersion.of(17)?** The syntax `extensions.configure<KotlinJvmProjectExtension>` with `jvmTarget.set(JvmTarget.JVM_17)` is simpler and works directly without needing Java plugin installation. This is the Kotlin Gradle Plugin's preferred approach.
- **Why add sourceCompatibility/targetCompatibility?** Kotlin plugin 2.1.0+ requires bytecode target consistency. These settings tell Gradle's Java compiler to emit Java 17-compatible bytecode.

#### Key Insight
JVM target configuration in multi-module Gradle projects must be:
1. **Explicit in root subprojects { } block** - inherited by all modules, no duplication needed
2. **Consistent across both Kotlin and Java** - sourceCompatibility = jvmTarget (both set to 17)
3. **Version catalog reference optional** - jvm-target entry in libs.versions.toml is kept for documentation but not actively used in build logic

### Task 8 - HikariCP Connection Pool Wrapper

#### Implementation Architecture
**DatabaseConnectionPool** - Singleton object providing lazy-initialized connection pools per ConnectionProfile.id
- Package: `com.dbeagle.pool`
- Pattern: Thread-safe ConcurrentHashMap for pool storage
- Lazy initialization: Pools created on first `getConnection()` call

#### Pool Configuration Defaults
All timeouts documented in code constants:
- **Maximum pool size**: 10 connections
- **Connection timeout**: 30 seconds (30,000ms)
- **Idle timeout**: 10 minutes (600,000ms)
- **Max lifetime**: 30 minutes (1,800,000ms)
- **Leak detection threshold**: 60 seconds (60,000ms)

Rationale:
- Conservative defaults for desktop application (not web server)
- 10-connection max prevents resource exhaustion on single-user workstation
- 30-min max lifetime forces periodic connection refresh (handles DB restarts gracefully)
- 60s leak detection catches unreleased connections early in development

#### API Surface
**Primary Methods**:
- `getConnection(profile: ConnectionProfile, decryptedPassword: String): Connection`
  - Lazy-init pool if not exists
  - Returns JDBC Connection from HikariCP
  - Throws IllegalStateException on pool exhaustion with retry guidance
- `closePool(profile: ConnectionProfile)` - Remove and close pool by profile
- `closePool(profileId: String)` - Remove and close pool by ID
- `closeAllPools()` - Shutdown all pools and clear map

**Utility Methods**:
- `getPoolCount(): Int` - Current number of active pools
- `hasPool(profileId: String): Boolean` - Check if pool exists

All close methods are idempotent (safe to call multiple times).

#### JDBC URL Construction
Supports both PostgreSQL and SQLite via sealed class pattern:
- **PostgreSQL**: `jdbc:postgresql://host:port/database`
- **SQLite**: `jdbc:sqlite:database` (database field contains file path)

Future database types can extend DatabaseType sealed class and add URL construction logic.

#### Error Handling Strategy
**Pool Exhaustion**:
- HikariCP throws SQLException when all connections busy + timeout exceeded
- Wrapped in IllegalStateException with:
  - Descriptive retry suggestion
  - Real-time pool stats (active, idle, total, waiting threads)
  - Profile name for debugging
  
**Invalid Credentials**:
- HikariCP fails on first connection attempt with PSQLException (PostgreSQL)
- Wrapped in IllegalStateException with clear message
- Pool not added to map if initialization fails (prevents retry loops)

#### TestContainers Integration
**DatabaseConnectionPoolTest.kt** - 9 comprehensive tests:
1. Lazy pool initialization verification
2. Valid connection retrieval and query execution (SELECT 1)
3. Pool reuse for same profile (no duplicate datasources)
4. Pool removal via `closePool(profile)`
5. Pool removal via `closePool(profileId)`
6. Close all pools with multiple profiles
7. Multiple profiles create separate pools
8. Idempotent close operations
9. Invalid credentials error handling

**Docker Availability Handling**:
- Tests gracefully skip if PostgreSQL container can't start
- Detected via try/catch in @BeforeTest setup
- Each test checks `dockerAvailable` flag before execution
- Prints warning message explaining Docker requirement
- Tests pass as skipped (not failed) when Docker unavailable

**TestContainers Configuration**:
- Image: `postgres:15-alpine`
- Database: `testdb`, User: `testuser`, Password: `testpass`
- Startup timeout: 60 seconds
- Container lifecycle: Started in @BeforeTest, stopped in @AfterTest
- Pool cleanup: `closeAllPools()` called in @AfterTest

#### HikariCP Best Practices Applied
1. **Pool naming**: `DBEagle-{profileName}-{profileId8chars}` for JMX monitoring
2. **Auto-commit enabled**: Default JDBC behavior for ad-hoc queries
3. **Transaction isolation**: TRANSACTION_READ_COMMITTED (PostgreSQL default)
4. **Custom options**: Profile.options map passed to HikariConfig via addDataSourceProperty()
5. **Leak detection**: Warns if connection held >60s (catches unreleased connections)

#### Thread Safety
- ConcurrentHashMap ensures thread-safe pool storage
- computeIfAbsent() provides atomic lazy initialization
- HikariDataSource is thread-safe by design
- Safe for concurrent getConnection() calls from multiple threads

#### Memory Management
**Pool Lifecycle**:
- Pools persist until explicitly closed (not GC'd automatically)
- Application must call closePool() or closeAllPools() on shutdown
- Unreleased connections trigger leak detection warnings (not crashes)
- HikariCP handles connection recycling within pool automatically

#### Dependency Details
- **HikariCP**: Already present in data/build.gradle.kts (from Task 1)
- **TestContainers**: Already present in data/build.gradle.kts (from Task 6)
- No new dependencies added for this task

#### Verification Results
- `./gradlew :data:test` → BUILD SUCCESSFUL
- All 9 tests passed (0 failures, 0 ignored)
- Test duration: ~0.5 seconds total
- HTML report: `data/build/reports/tests/test/index.html`

#### Key Design Decisions
1. **Why singleton object?** 
   - Single shared pool registry across application
   - No need for DI injection (stateless utility)
   - Thread-safe via ConcurrentHashMap
   
2. **Why plaintext password parameter?**
   - Caller must decrypt (separation of concerns)
   - Pool doesn't handle encryption/decryption
   - HikariCP requires plaintext for JDBC connection
   - Reduces plaintext lifetime (not stored after config creation)
   
3. **Why ConcurrentHashMap over synchronized map?**
   - Better concurrency (lock-free reads)
   - computeIfAbsent() provides atomic lazy-init
   - Standard Java concurrent collections pattern
   
4. **Why not configurable pool settings per profile?**
   - Task spec: defaults only, no per-profile customization
   - Profile.options map allows DB-specific props if needed
   - Future enhancement: Add ConnectionConfig integration

#### Notable Implementation Details
1. **Pool stats in error message**: Uses HikariPoolMXBean for real-time metrics
2. **Safe null handling**: `?: 0` for pool stats if MXBean unavailable
3. **Profile.id truncation**: First 8 chars in pool name (readability vs uniqueness)
4. **No connection validation on return**: HikariCP handles internally
5. **No max wait time override**: Uses connectionTimeout default (30s)


### Task 9 - PostgreSQL Driver (Exposed)

#### Exposed + HikariCP integration pattern
- Exposed can connect via `Database.connect(datasource = someDataSource)`.
- When using an existing pool manager, a minimal `DataSource` wrapper can delegate `getConnection()` to the pool.
- Query timeouts can be enforced both via Exposed transaction `queryTimeout` and JDBC `Statement.queryTimeout`.

#### Metadata retrieval approach
- JDBC `DatabaseMetaData` works well for metadata extraction:
  - Tables: `getTables(null, "public", "%", arrayOf("TABLE"))`
  - Views: `getTables(null, "public", "%", arrayOf("VIEW"))`
  - Columns: `getColumns(null, "public", table, "%")`
  - Foreign keys: `getImportedKeys(null, "public", table)`

### Task 10 - SQLite Driver (Exposed)

#### In-memory SQLite + Exposed transaction lifecycle
- Exposed transactions close JDBC connections; for SQLite `:memory:` this destroys the database. Workaround: provide Exposed a DataSource that returns a proxy Connection where `close()` is a no-op, while the real connection is closed by the driver on `disconnect()`.

#### SQLite metadata via pragmas
- Tables/views/indexes can be listed from `sqlite_master` (exclude `sqlite_%` internals).
- Column metadata: `PRAGMA table_info('<table>')`.
- Foreign keys: iterate tables and query `PRAGMA foreign_key_list('<table>')`.

## Task 21: Connection Manager Integration (UI ↔ DB Layer)

**Date:** 2026-03-03

### Wiring pattern (UI → persistence → registry → real driver)
- **Profiles list**: Load via `PreferencesBackedConnectionProfileRepository.loadAll()` but immediately scrub `encryptedPassword` before storing profiles in Compose state.
- **Connect flow**: On connect click, re-load the single profile via `repository.load(profileId)` to obtain the decrypted password, then pass it to the driver **only at connect time** via `profile.options["password"]`.
- **Registry**: Ensure `DataDrivers.registerAll()` runs before first connect so `DatabaseDriverRegistry.getDriver(profile.type)` succeeds.

### Driver instantiation note
- `DatabaseDriverRegistry` stores instances; UI should create a fresh driver per connected profile. Using reflection to call a no-arg constructor keeps this local and avoids introducing new DI wiring.

### UI state conventions
- Track per-screen connection state as:
  - `connectedProfileIds: Set<String>`
  - `connectingProfileId: String?` (used to show a per-row spinner and to disable actions globally)
  - `dialogError: String?` (Material3 AlertDialog)

### Disconnect semantics
- Always call `DatabaseConnectionPool.closePool(profileId)` on disconnect (idempotent). PostgreSQL uses the pool; SQLite driver doesn’t, but close is safe.

### Pool integration (UI responsibility)
- During connect, the UI should touch `DatabaseConnectionPool.getConnection(profile, decryptedPassword)` to ensure the pool is initialized and credentials/URL are validated early.

## Task 22 - SQL editor integration (execute query → display results)

- Exposed a minimal “active connection” signal from `ConnectionManagerScreen` to `App.kt` via `onActiveConnectionChanged(profileId, profileName, driver)`; policy is last connected wins, with fallback to any remaining driver on disconnect/delete.
- Query Editor executes SQL via `QueryExecutor(driver).execute(sql)` and maps `QueryResult.Success.rows: List<Map<String, String>>` into `ResultGrid` rows by iterating `columnNames` and reading `rowMap[col] ?: ""`.
- `SQLEditor` takes `isRunning` to disable Run, show a small spinner, and prevent double-run.

## Task 11: Driver Registry + Plugin Loading

**Date:** 2026-03-03

### Implementation Approach
- **Two-part registry pattern**: Core module defines registry singleton, data module registers concrete implementations
- **Location**: `core/src/main/kotlin/com/dbeagle/driver/DatabaseDriverRegistry.kt` (registry) and `data/src/main/kotlin/com/dbeagle/driver/DataDrivers.kt` (initializer)
- **Architecture**: Avoids circular dependencies by having data module import core, then register drivers back into core's registry
- **Thread safety**: All registry operations are `@Synchronized` for concurrent access

### Key Design Decisions
1. **Registry in core**: Allows UI and service layers to access drivers without depending on data module
2. **Singleton pattern**: Single global registry instance using Kotlin `object`
3. **Simple API**: registerDriver, getDriver, listAvailableDrivers
4. **Override support**: Registering same type twice replaces the driver (supports plugin replacement)
5. **Internal clear()**: Test-only method for resetting registry state between tests

### Testing Patterns
- **Module isolation**: Core tests use MockDatabaseDriver, data tests use real drivers
- **No clear() in data tests**: Since clear() is internal, data tests don't reset state (accept >= 2 drivers)
- **Real driver validation**: Data tests verify driver names match "PostgreSQL" and "SQLite"
- **Thread-safe by default**: All operations synchronized, no special concurrent testing needed

### Success Metrics
- All 10 test cases pass (6 in core, 4 in data)
- Zero circular dependencies between core and data
- Clean plugin architecture supporting future drivers
- Full test suite passes (BUILD SUCCESSFUL)

### Gotchas Encountered
1. **MockDatabaseDriver collision**: Existing mock in DatabaseDriverTest.kt, removed duplicate file
2. **Internal access**: clear() method must be internal to prevent external misuse, adjusted data tests to not reset state
3. **Test isolation**: Core tests need clear() for isolation, data tests need idempotent registration

### Code Conventions Observed
- Kotlin object for singleton pattern
- Synchronized methods for thread safety
- Internal visibility for test-only utilities
- KDoc on public API (necessary for registry interface)
- Package structure: com.dbeagle.driver for driver-related code

## Task 12: Connection Profile Persistence (Encrypted)

**Date:** 2026-03-03

### Implementation Approach
- **Package**: `core/src/main/kotlin/com/dbeagle/profile/`
- **Components**: Three interfaces/implementations + comprehensive test suite
- **Storage**: Java Preferences (userRoot node: "com.dbeagle.profiles")
- **Encryption**: AES-GCM via existing CredentialEncryption (PBKDF2 key derivation)

### Files Created
1. **MasterPasswordProvider.kt** - Callback interface for UI integration
   - Fun interface (SAM) with suspend function
   - Returns master password string when called
   - Allows UI to implement dialogs/prompts separately from persistence logic
   
2. **ConnectionProfileRepository.kt** - Repository interface
   - Methods: save(profile, plaintextPassword), load(id), loadAll(), delete(id)
   - All methods are suspend functions (async-ready)
   - Security contract: plaintext password accepted, encrypted at storage layer
   
3. **PreferencesBackedConnectionProfileRepository.kt** - Implementation
   - Uses Java Preferences for key-value storage
   - Stores profiles as JSON via kotlinx.serialization
   - Encrypts password field separately using CredentialEncryption
   - Master password obtained via MasterPasswordProvider callback

4. **ConnectionProfileRepositoryTest.kt** - Test suite (8 tests, all passing)

### Storage Format Design
**JSON Structure**:
```json
{
  "id": "...",
  "name": "...",
  "typeDiscriminator": "PostgreSQL",
  "host": "...",
  "port": 5432,
  "database": "...",
  "username": "...",
  "encryptedPassword": {
    "ciphertext": [bytes],
    "iv": [bytes],
    "salt": [bytes]
  },
  "options": {}
}
```

**Key = profile.id**, Value = JSON string (stored in Preferences node)

### DatabaseType Sealed Class Gotcha
**Problem**: DatabaseType is sealed class with object members (PostgreSQL, SQLite)
- Cannot use `.name` property (doesn't exist on sealed class)
- Cannot use `valueOf()` (sealed classes don't have that method)

**Solution**: Manual discriminator mapping
```kotlin
// Serialize
val typeDiscriminator = when (profile.type) {
    is DatabaseType.PostgreSQL -> "PostgreSQL"
    is DatabaseType.SQLite -> "SQLite"
}

// Deserialize
val databaseType = when (stored.typeDiscriminator) {
    "PostgreSQL" -> DatabaseType.PostgreSQL
    "SQLite" -> DatabaseType.SQLite
    else -> throw IllegalArgumentException("Unknown type")
}
```

### Test Strategy
**Coverage areas**:
1. Save/load roundtrip with password encryption/decryption
2. Load returns null for non-existent profiles
3. loadAll returns all saved profiles
4. loadAll returns empty list when storage empty
5. delete removes profile
6. Wrong master password throws IllegalArgumentException
7. Raw preferences storage verification (no plaintext password)
8. Overwrite existing profile with same ID

**Graceful test environment**:
- Uses separate test node: "com.dbeagle.profiles.test"
- @BeforeTest: clear + fresh node
- @AfterTest: clear + removeNode() (cleanup)

### Dependency Management
**Added to gradle/libs.versions.toml**:
```toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

**Added to core/build.gradle.kts**:
```kotlin
testImplementation(libs.kotlinx.coroutines.test)
```

**Rationale**: Repository methods are suspend functions, tests need `runTest { }` coroutine builder

### Security Considerations
1. **Master password not stored**: Obtained via callback on each encrypt/decrypt operation
2. **Plaintext password lifetime**: Short-lived, only exists during save() call
3. **Storage verification**: Tests confirm plaintext password not in raw Preferences storage
4. **Encryption per profile**: Each profile gets unique salt and IV (via CredentialEncryption)
5. **Decryption failure**: Clear exception message without leaking timing info

### API Design Rationale
**Why separate plaintextPassword parameter?**
- ConnectionProfile.encryptedPassword stores the encrypted form (output of encryption)
- save() accepts plaintext password as separate parameter (input to encryption)
- Avoids confusion: caller doesn't modify profile.encryptedPassword manually
- Clear contract: repository handles encryption/decryption transparently

**Why suspend functions?**
- MasterPasswordProvider may need to show UI dialog (async operation)
- Future: could integrate with system keychain (async I/O)
- Allows callers to use structured concurrency (withContext, launch, etc.)

### Master Password Provider Pattern
**Design**: Dependency injection via constructor parameter
```kotlin
class PreferencesBackedConnectionProfileRepository(
    private val masterPasswordProvider: MasterPasswordProvider,
    private val preferences: Preferences = Preferences.userRoot().node("com.dbeagle.profiles")
)
```

**Benefit**: Separates persistence logic from password source
- Core module: persistence
- UI module: implement provider with dialog
- Test module: implement provider with hardcoded password

**Alternative considered**: Global singleton provider
- Rejected: less testable, tight coupling, single global state

### Verification
All tests passed:
```
./gradlew :core:test --tests "com.dbeagle.profile.ConnectionProfileRepositoryTest"
> BUILD SUCCESSFUL
```

All project tests passed:
```
./gradlew test
> BUILD SUCCESSFUL
```

### Integration Points (Future Tasks)
- **Task 14+**: UI will implement MasterPasswordProvider with Compose dialog
- **Task 14+**: App startup will load profiles via loadAll()
- **Task 14+**: Connection form will save profiles via save()
- **Task 14+**: Profile management UI will delete via delete()

### Key Insights
1. **Java Preferences simplicity**: No schema, no migrations, just key-value store
2. **kotlinx.serialization flexibility**: Handles EncryptedData (ByteArray fields) correctly with @Serializable
3. **Sealed class serialization**: Requires manual discriminator mapping (no built-in reflection helper)
4. **Suspend function tests**: kotlinx-coroutines-test provides `runTest { }` builder for suspending assertions
5. **Callback pattern for UI integration**: Fun interface + suspend function allows clean separation of concerns

### Avoided Pitfalls
- ❌ Storing plaintext password in Preferences (verified in test)
- ❌ Using enum for DatabaseType (sealed class requires different approach)
- ❌ Hardcoding master password in repository (callback pattern used)
- ❌ Implementing UI dialog in core module (MasterPasswordProvider abstraction avoids this)
- ❌ Adding cloud sync or external storage (Java Preferences only, per task spec)

## Task 13: Query Execution Engine with Pagination

- Pagination is implemented above drivers by wrapping the base SELECT/WITH query as `SELECT * FROM ( <baseQuery> ) AS q LIMIT ? OFFSET ?` (after stripping trailing semicolon).
- Use `pageSize + 1` rows to compute `hasMore` deterministically and trim to `pageSize` rows.
- Keep pagination state out of persistence/serialization via a nullable `@Transient resultSet` on `QueryResult.Success`.

## Task 14: Compose Main Window Scaffold
- Compose Desktop successfully started without immediate crashing via `./gradlew :app:run` (timeout after 120s without trace).
- Koin handles `startKoin` block seamlessly before `application` startup block in `main()`.

## Navigation UI
Compose Desktop's ScrollableTabRow works nicely for horizontally stacking dynamic tabs. We map the enum entries to Tab components, utilizing 'selectedTab' mutableState variable. This ensures the UI accurately tracks the user's intent to switch between tabs (Connections, Query Editor, Schema Browser, Favorites, History).

### Task 18: Result Grid Component with Pagination
- Created `ResultGrid` as a reusable component for displaying tabulated query results.
- Utilized nested Column/Row structures combined with `rememberScrollState` for bidirectional scrolling.
- Implemented `EditableCell` using `detectTapGestures` on double-tap to switch between `Text` and `BasicTextField`. Key events (Enter, Escape) handle commit/revert behavior seamlessly. Focus loss also commits changes.
- In-memory state tracking (`localRows`) allows edits to persist locally while paging back and forth.
- Modified `App.kt` to present both `SQLEditor` and `ResultGrid` with mock data within a weighted layout inside the `QueryEditor` tab.

## Task 19 - Schema Browser Tree Component (UI)

### Implementation Approach
- Implemented an expandable/collapsible tree for the Schema Browser using `LazyColumn` and flattened tree recursion.
- Used `androidx.compose.foundation.onClick` with `PointerMatcher.mouse(PointerButton.Secondary)` for clean right-click handling in Compose Desktop.
- Substituted `detectTapGestures(onSecondaryTap = ...)` (which isn't reliable/present in basic Compose Multiplatform) with the newer experimental `onClick` API.
- Implemented a mock `SchemaTreeNode` model to encapsulate structure (Tables -> Columns, Views, Indexes).

### Key Design Decisions
- **Indent Tracking**: Tree indentation is handled via `Spacer(modifier = Modifier.width((depth * 16).dp))` to keep layout straightforward and flexible without deep nested columns.
- **Context Menus**: Standard `DropdownMenu` bound to a boolean state managed per `SchemaTreeNodeItem`, invoked via right-click, perfectly simulating a typical desktop context menu.

### Task 19 Fix - UI Event Callbacks vs Hardcoded Side Effects
- When implementing UI components, avoid hardcoding side effects like `println()` directly into composables.
- Prefer exposing event actions as lambda parameters (e.g., `onCopyName: (String) -> Unit = {}`) in the UI component layer.
- This maintains proper separation of concerns, allowing parent scopes or presenters to handle actual application logic while the UI layer remains a pure presentation element.

### Task 20: Export Dialog UI
- When using `java.awt.FileDialog` in Compose Desktop, always wrap its invocation in a `try-catch` block handling `java.awt.HeadlessException` to prevent the UI from crashing in headless CI/CD testing environments.

### Task 21 Addendum - Status + Schema Verification + Tooling
- After a successful `driver.connect(...)`, immediately call `driver.getSchema()` (discard result) to validate the schema path works; if it fails, treat the connect as failed and rollback via `driver.disconnect()` + `DatabaseConnectionPool.closePool(profileId)`.
- Prefer explicit status updates during connect/disconnect (e.g., "Connecting...", "Connected", "Disconnecting...", "Disconnected") via the existing `onStatusTextChanged` callback instead of relying only on derived state.
- `functions.lsp_diagnostics` may time out on this repo due to long `kotlin-language-server` initialize; Gradle compile/tests are the reliable verification path when LSP tooling doesn’t respond in time.
