# Contributing to DB Eagle

Thank you for your interest in contributing to DB Eagle! This document outlines the coding standards and practices we follow.

## Coding Standards

### Import Statements

**❌ NO WILDCARD IMPORTS** - Always use explicit imports to maintain code clarity and prevent namespace pollution.

**Good:**
```kotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
```

**Bad:**
```kotlin
import kotlin.test.*
import org.junit.jupiter.api.Assertions.*
```

**Enforcement:** This rule is enforced by both Detekt and Spotless (ktlint), and CI builds will fail if wildcard imports are detected.

### Code Quality

We use the following tools to maintain code quality:

- **Detekt**: Static code analysis for Kotlin
- **Spotless**: Code formatting with ktlint
- **Kover**: Code coverage reporting

### Running Quality Checks Locally

Before submitting a pull request, ensure all quality checks pass:

```bash
# Run Detekt static analysis
./gradlew detekt

# Run Spotless formatting check
./gradlew spotlessCheck

# Apply Spotless formatting automatically
./gradlew spotlessApply

# Run all tests
./gradlew test

# Run full build (includes all checks)
./gradlew build
```

### Continuous Integration

Our GitHub Actions CI pipeline runs the following checks on every push and pull request:

1. `detekt` - Static code analysis
2. `spotlessCheck` - Code formatting verification
3. `build` - Compilation and tests

All checks must pass before code can be merged.

## Development Setup

### Prerequisites

- JDK 17 or higher
- Docker (optional, for TestContainers tests)

### Running the Application

```bash
./gradlew run
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :core:test
./gradlew :data:test
./gradlew :app:test
```

### Building Distribution Packages

```bash
# Package for your platform
./gradlew package

# Or use platform-specific tasks:
./gradlew packageDmg    # macOS
./gradlew packageMsi    # Windows
./gradlew packageDeb    # Linux
```

## Pull Request Guidelines

1. **Create a feature branch** from `main`
2. **Follow coding standards** (especially no wildcard imports!)
3. **Run quality checks locally** before pushing
4. **Write clear commit messages** describing the changes
5. **Ensure all tests pass** in CI
6. **Update documentation** if adding new features

## Questions?

If you have any questions about contributing, feel free to open an issue for discussion.

---

Thank you for helping make DB Eagle better!
