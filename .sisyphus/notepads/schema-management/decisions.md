
## TableEditorDialog Callback Design (2026-03-05)

### Decision: Extend callback signature with indexes
Chose **Option A**: Extend callback with second parameter
```kotlin
onSave: (TableDefinition, List<IndexDefinition>) -> Unit
```

**Rationale**:
- Simpler than wrapper data class
- Minimal changes to existing code
- Clear separation of table def vs indexes
- Follows Kotlin conventions for multiple return values

**Alternatives Considered**:
- Wrapper data class: Would add unnecessary indirection
- Modifying TableDefinition: Would break separation of concerns (indexes are external to table DDL)

## Constraint Name Handling (2026-03-05)

### Decision: Use naming conventions with documented limitations
For DROP operations:
- PK: `{table}_pkey` convention
- FK: Require explicit name (skip if unavailable)
- Unique: `{table}_unique_{idx}` fallback

**Rationale**:
- Current metadata lacks constraint names in many cases
- Conventions match typical PostgreSQL/SQLite naming
- Explicit limitation documentation prevents silent failures
- Better to document limitation than implement incomplete solution

**Future Improvement**:
Enhance metadata collection to include actual constraint names from database.
