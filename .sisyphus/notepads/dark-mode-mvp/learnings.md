## Dark Mode Toggle Implementation (SettingsScreen)

**Implementation pattern:**
- Added `Switch` from `androidx.compose.material3`
- Added `Alignment` from `androidx.compose.ui` for vertical centering
- Collected state: `val darkMode by viewModel.darkMode.collectAsState()`
- Row with `SpaceBetween` arrangement for label-switch layout
- Placed after error message, before first OutlinedTextField
- Used Material3 `bodyLarge` typography for label consistency
- Switch calls `viewModel.setDarkMode(it)` on toggle

**Spacing/Layout:**
- Follows existing 16.dp vertical spacing in Column
- Row fills width with SpaceBetween for natural toggle placement
- CenterVertically alignment keeps Switch and Text baseline-aligned

**Build verification:**
- `./gradlew compileKotlin` passed (unrelated deprecation warnings in App.kt)

