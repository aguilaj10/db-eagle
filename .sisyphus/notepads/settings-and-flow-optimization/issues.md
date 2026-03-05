# Issues - Settings Migration & Flow Optimization

## Known Issues
<!-- Document issues encountered and their solutions -->

## Gotchas
<!-- Add gotchas discovered during execution -->

## Pre-existing Compilation Issue (2026-03-05)

### Problem
- `./gradlew :core:compileKotlin` fails with exhaustive when expression error
- Location: `core/src/main/kotlin/com/dbeagle/profile/PreferencesBackedConnectionProfileRepository.kt:48:13`
- Error: `'when' expression must be exhaustive. Add the 'Oracle' branch or an 'else' branch.`

### Impact
- Blocks full project compilation verification
- ViewDDLBuilder.kt itself has no syntax errors (verified via file inspection)
- This is an unrelated codebase issue that needs fixing

### Status
- Not blocking ViewDDLBuilder task completion (file syntax is valid)
- Should be fixed in a separate task to unblock compilation checks
