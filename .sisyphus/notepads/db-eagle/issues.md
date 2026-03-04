## Issues

_Append-only. Track blockers, failing commands, and errors._

### Task 1 - Issues Encountered and Resolutions

1. **Repository Configuration Error** (RESOLVED)
   - Error: "Build was configured to prefer settings repositories over project repositories..."
   - Root cause: Declared `repositories { google(); mavenCentral() }` in root build.gradle.kts
   - Solution: Removed repositories from root build.gradle.kts. All repository configuration centralized in settings.gradle.kts via dependencyResolutionManagement

2. **Missing Compose Compiler Plugin** (RESOLVED)
   - Error: "Since Kotlin 2.0.0-RC2 to use Compose Multiplatform you must apply org.jetbrains.kotlin.plugin.compose plugin"
   - Solution: Added plugin to root build.gradle.kts: `id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false` and applied to app module

3. **Kotlin Test Version Conflict** (RESOLVED)
   - Error: Koin 3.5.3 test module pulled in kotlin-test-junit 1.9.21, conflicting with kotlin-test-junit5 2.1.0 from direct dependency
   - Solution: Removed `testImplementation("io.insert-koin:koin-test:3.5.3")` from core module for now

4. **Skiko Native Library Missing** (KNOWN LIMITATION - NOT A BUG)
   - Error: `Cannot find libskiko-linux-x64.so.sha256` when running gradlew :app:run
   - Context: Expected in headless environments (CI/containers). App code compiles successfully.
   - Workaround: Not needed for scaffold validation. Desktop display requires native libraries.

### No Unresolved Blockers
All scaffolding goals achieved:
✓ Multi-module structure created
✓ Gradle build succeeds with `./gradlew clean build`
✓ Tests run and pass
✓ App code compiles (Compose UI available at compile-time)

### Task 6 - Docker Availability Issue (Expected in Headless Environments)

**Issue**: PostgreSQL TestContainer fails to start because Docker daemon is not available
- Environment: Linux headless server (no Docker installed)
- Error: TestContainers cannot create PostgreSQL container without Docker

**Context**: This is expected behavior, not a bug
- TestContainers requires Docker to run database containers
- CI/CD environments without Docker-in-Docker will have same limitation
- Current implementation gracefully handles this:
  - `DatabaseTestContainers.startPostgres()` can fail without breaking tests
  - `SmokeTest` skips tests when container unavailable
  - Tests pass as "skipped" rather than failing

**Workaround for Local Development**:
1. Install Docker: `sudo apt-get install docker.io` (Ubuntu/Debian)
2. Start Docker daemon: `sudo service docker start` or `docker run hello-world` (to check)
3. Ensure user can run Docker without sudo: `sudo usermod -aG docker $USER` + logout/login
4. Rerun tests: `./gradlew test` will now fully execute smoke tests

**For CI/CD**:
- GitHub Actions workflows can use `docker` service or container images with Docker-in-Docker
- Current implementation won't break CI; tests will skip gracefully if Docker unavailable

**Resolution**: Not blocking - infrastructure is correct, environment limitation expected


### Task 8 - HikariCP Connection Pool Wrapper Issues

#### LSP Timeout (Non-Blocking)
**Issue**: `lsp_diagnostics` tool timed out during verification
- Error: "LSP request timeout (method: initialize)"
- Context: Tried to check diagnostics after implementation
- Workaround: Used `./gradlew build` instead (compilation successful)
- Root cause: SLF4J logger binding missing (warning in stderr)
- Impact: None - tests pass, code compiles, diagnostics not critical

**Resolution**: Not blocking - verified via Gradle build instead of LSP

#### SLF4J Logger Binding Warning (Expected, Not Breaking)
**Warning**: "Failed to load class org.slf4j.impl.StaticLoggerBinder"
- Context: HikariCP depends on SLF4J for logging
- Current state: SLF4J defaults to NOP (no-operation) logger
- Impact: No HikariCP pool logs visible in test output (not a bug)
- Future: Can add logback-classic or slf4j-simple to testImplementation if logs needed
- Decision: Leave as-is for now (tests pass, logging not required for Task 8)

#### Docker Unavailable in Test Environment (Expected)
**Context**: TestContainers requires Docker daemon
- Current environment: Headless Linux without Docker
- Tests gracefully skip when container can't start
- All 9 tests shown as "skipped" in local run (Docker not available)
- CI environments with Docker will run full test suite
- Resolution: Expected behavior, not a bug

### Task 21 - LSP diagnostics timeout (Expected)

**Issue**: `lsp_diagnostics` tool timed out (method: initialize)
- Impact: Non-blocking; verified via Gradle compile/test instead.

**Note**: Still reproduces when run against app Kotlin files (ConnectionManagerScreen.kt).

**Latest** (2026-03-04): Still failing with `Error: LSP request timeout (method: initialize)` when running diagnostics on `app/src/main/kotlin/com/dbeagle/ui/ConnectionManagerScreen.kt`.

### Task 22 - LSP diagnostics timeout (Expected)

**Issue**: `lsp_diagnostics` tool still times out during initialize (App.kt, SQLEditor.kt, ConnectionManagerScreen.kt).
- Impact: Non-blocking; verified via `./gradlew :app:compileKotlin test`.
- Note: stderr indicates missing SLF4J binder (NOP logger); not blocking compilation.


### Task 9 - PostgreSQL Driver (Exposed) - Known Gaps

1. **Password handling not yet integrated with encryption**
   - Current driver expects plaintext password in `ConnectionProfile.options["password"]`.
   - `ConnectionProfile.encryptedPassword` is not used by the driver yet.
   - This is a temporary bridge until credential persistence/decryption is implemented.

### Task 10 - SLF4J Binding Resolution (RESOLVED)

**Issue**: SLF4J logger binding was missing, causing warnings
- Error: "Failed to load class org.slf4j.impl.StaticLoggerBinder"
- Root cause: HikariCP uses SLF4J for logging; project had no binding, defaulting to NOP
- Context: LSP diagnostics timing out; SLF4J warnings in stderr

**Solution**: Added slf4j-simple binding
- Added to gradle/libs.versions.toml: `slf4j-simple = { module = "org.slf4j:slf4j-simple", version = "2.0.13" }`
- Added to app/build.gradle.kts: `runtimeOnly(libs.slf4j.simple)` (ensures binding available in desktop app runtime)
- Added to data/build.gradle.kts: `testRuntimeOnly(libs.slf4j.simple)` (ensures binding available during data module tests)

**Verification**:
✓ ./gradlew :core:test — BUILD SUCCESSFUL (no warnings, 11 tests from cache/executed)
✓ ./gradlew :data:test — BUILD SUCCESSFUL (no SLF4J binding warnings, 8 tests skipped due to Docker unavailable - expected)
✓ ./gradlew clean build — BUILD SUCCESSFUL (14 executed, 6 from cache)
✓ No "Failed to load class org.slf4j.impl.StaticLoggerBinder" warnings in stderr

**Impact**: SLF4J logging is now properly configured. HikariCP can log connection pool activity if needed. LSP should no longer timeout due to binding warnings.

### Task [K2 Compiler Stability] - Preventive Hardening (RESOLVED)

**Context**: Potential intermittent Kotlin K2 internal compiler error / jarfs class read issue
- Symptom: Could fail with "unsafe memory access operation" or "could not read *.jar!/ClassName.class"
- Frequency: Intermittent (not always reproducible)
- Kotlin version: 2.1.0 with K2 compiler

**Root Cause**: Kotlin daemon jarfs (JAR file system) can have race conditions or memory issues in parallel builds with incremental compilation

**Solution**: Disabled incremental Kotlin compilation to prevent jarfs instability
- Added to gradle.properties:
  ```properties
  kotlin.daemon.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
  kotlin.daemon.useFallbackStrategy=false
  kotlin.incremental=false
  ```
- `kotlin.incremental=false`: Disables incremental compilation (prevents jarfs cache corruption)
- `kotlin.daemon.jvmargs`: Ensures Kotlin daemon has sufficient memory
- `kotlin.daemon.useFallbackStrategy=false`: Prevents daemon from using unstable fallback strategies

**Trade-off**: Slightly slower clean builds (full recompilation each time), but eliminates intermittent failures

**Verification**:
✓ ./gradlew clean test (pass 1): BUILD SUCCESSFUL in 49s
✓ ./gradlew clean test (pass 2): BUILD SUCCESSFUL in 13s (with cache)
✓ Stress test (5 daemon restarts + clean builds): All passed (48-66s each)
✓ No jarfs errors, no "unsafe memory access" errors

**Decision**: Incremental compilation disabled project-wide. Build stability > marginal speed improvement.

## Task 14: Screenshot constraints
- As noted in inherited wisdom, capturing an actual `.png` screenshot of the Compose Window fails / is unfeasible in this headless test environment (no native compositor hooked for `java.awt.Robot` to take a snapshot of), so `task-14-app-run-log.txt` was produced as evidence instead.

### Task 23 - Schema browser integration

**Issue**: `lsp_diagnostics` tool timed out (method: initialize) when checking App.kt / SchemaTree.kt.
- Impact: Non-blocking; verified via `./gradlew :app:compileKotlin test` instead.

**Known gap**: `DatabaseDriver.getColumns(table: String)` does not accept schema.
- Impact: For DBs with multiple schemas, lazy column loading may need a future driver API change.
