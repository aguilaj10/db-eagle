# Add Detekt for Static Code Analysis

## TL;DR

> **Quick Summary**: Add Detekt plugin for Kotlin static code analysis to complement Spotless/ktlint formatting.
> 
> **Deliverables**:
> - Detekt plugin configured in version catalog and build.gradle.kts
> - detekt.yml config file with Compose-friendly rules
> - `./gradlew detekt` runs successfully
> 
> **Estimated Effort**: Quick (15 minutes)
> **Parallel Execution**: NO - single task

---

## Context

### Current State
- Spotless with ktlint already configured (commit fb70cfd)
- Code is already formatted
- Need static analysis for code quality checks

### Files to Modify
- `gradle/libs.versions.toml` - Add detekt version and plugin
- `build.gradle.kts` - Add detekt plugin and configuration
- `detekt.yml` (new) - Detekt rules configuration

---

## TODOs

- [ ] 1. Add Detekt plugin and configuration

  **What to do**:
  
  1. Edit `gradle/libs.versions.toml`:
     - Add in [versions] section after spotless: `detekt = "1.23.7"`
     - Add in [plugins] section after spotless: `detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }`

  2. Edit `build.gradle.kts`:
     - Add to plugins block: `alias(libs.plugins.detekt)`
     - Add after spotless block:
       ```kotlin
       detekt {
           buildUponDefaultConfig = true
           allRules = false
           config.setFrom(files("$rootDir/detekt.yml"))
           parallel = true
       }

       tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
           reports {
               html.required.set(true)
               xml.required.set(false)
               txt.required.set(false)
           }
       }
       ```

  3. Create `detekt.yml` in project root:
     ```yaml
     build:
       maxIssues: 10

     complexity:
       LongMethod:
         threshold: 80
       LongParameterList:
         functionThreshold: 12
         constructorThreshold: 12
       TooManyFunctions:
         thresholdInFiles: 30
         thresholdInClasses: 30

     style:
       MaxLineLength:
         maxLineLength: 120
       WildcardImport:
         active: false
       MagicNumber:
         ignoreNumbers:
           - '-1'
           - '0'
           - '1'
           - '2'
           - '100'

     naming:
       FunctionNaming:
         functionPattern: '[a-zA-Z][a-zA-Z0-9]*'
     ```

  4. Run: `./gradlew detekt`

  5. Commit:
     ```bash
     git add gradle/libs.versions.toml build.gradle.kts detekt.yml
     git commit -m "build: add Detekt for static code analysis"
     ```

  **Must NOT do**:
  - Do NOT run `./gradlew build` or `./gradlew test`
  - Do NOT run spotlessApply again

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **References**:
  - `gradle/libs.versions.toml:15-16` - Where to add detekt version
  - `gradle/libs.versions.toml:76` - Where to add detekt plugin
  - `build.gradle.kts:8` - Where to add plugin alias
  - `build.gradle.kts:69` - After spotless block, add detekt config

  **Acceptance Criteria**:
  - [ ] `gradle/libs.versions.toml` contains `detekt = "1.23.7"` in versions
  - [ ] `gradle/libs.versions.toml` contains detekt plugin definition
  - [ ] `build.gradle.kts` has detekt plugin and configuration
  - [ ] `detekt.yml` exists in project root
  - [ ] `./gradlew detekt` runs without build failure
  - [ ] Changes committed

---

## Commit Strategy

- `build: add Detekt for static code analysis`

---

## Success Criteria

### Verification Commands
```bash
./gradlew detekt  # Should complete (may have warnings but not fail)
```

### Final Checklist
- [ ] Detekt plugin added to version catalog
- [ ] Detekt configured in build.gradle.kts
- [ ] detekt.yml created with Compose-friendly rules
- [ ] `./gradlew detekt` executes successfully
