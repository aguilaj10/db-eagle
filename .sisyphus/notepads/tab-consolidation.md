# Tab Consolidation - Nested Tabs Removal

## Problem
Currently DB Eagle has TWO nested tab rows:
1. **Connection tabs** (parent) - Shows active database connections (PostgreSQL Dev, SQLite Local, etc.)
2. **Navigation tabs** (child) - Shows features (Query Editor, Favorites, History, Table Editor, etc.)

This creates visual clutter and complexity.

## Goal
Consolidate into ONE tab row with connection indicators embedded in each tab.

## Current Architecture (from code exploration)

### Connection Tabs (Parent - TO REMOVE)
- **Location**: App.kt lines 465-483
- **Data source**: `sessionOrder` (List<String>) from SessionViewModel
- **Rendering**: `PrimaryScrollableTabRow` iterating over `sessionOrder`
- **Label**: `sessionStates[profileId]?.profileName ?: profileId.take(8)`
- **Selection**: `activeProfileId` determines selected connection
- **Actions**: Click calls `sessionViewModel.setActiveProfile(profileId)`

### Navigation Tabs (Child - TO KEEP & ENHANCE)
- **Location**: App.kt lines 486-514
- **Data source**: `tabManager.tabs` (List<TabItem>)
- **Rendering**: `PrimaryScrollableTabRow` iterating over tabs
- **Model**: TabItem already has `connectionId: String?` field
- **Selection**: `tabManager.selectedTabId` determines selected tab
- **Actions**: Click calls `tabManager.selectTab(tab.id)`, close button calls `tabManager.closeTab(tab.id)`

### TabItem Model
```kotlin
data class TabItem(
    val id: String = UUID.randomUUID().toString(),
    val type: TabType,
    val title: String,
    val connectionId: String? = null,  // Already exists!
    val tableName: String? = null,
)
```

## UX Research - Connection Indicators

From ui-ux-pro-max skill search:
- **Accessibility Rule**: Don't convey information by color alone - use icons/text in addition
- **Best practice**: Color + text/icon for redundancy

## Design Options

### Option 1: Colored Dot + Connection Name Suffix ✅ RECOMMENDED
```
● Query Editor - PostgreSQL Dev
● Table: users - SQLite Local
◯ Favorites (no connection - gray dot)
```

**Pros**:
- Clear connection identification
- Accessible (color + text)
- Works for colorblind users
- Self-documenting

**Cons**:
- Longer tab titles (may need horizontal scrolling)

### Option 2: Colored Dot Only
```
● Query Editor
● Table: users
◯ Favorites
```

**Pros**:
- Compact
- Clean visual

**Cons**:
- Violates accessibility (color only)
- Requires memorizing connection colors
- Fails for colorblind users

### Option 3: Connection Initial Badge
```
[PD] Query Editor
[SL] Table: users
Favorites
```

**Pros**:
- Compact
- No color dependency

**Cons**:
- Initials unclear for new users
- Requires mental mapping

### Option 4: Hybrid - Dot + Tooltip
```
● Query Editor (hover shows "PostgreSQL Dev")
```

**Pros**:
- Compact
- Full info on demand

**Cons**:
- Hidden information (bad UX)
- Not discoverable
- Requires hover (bad for touch)

## Decision: Option 1 (Colored Dot + Name Suffix)

**Rationale**:
1. **Accessibility**: Passes WCAG guidelines (color + text)
2. **Clarity**: No ambiguity about which connection
3. **Discoverability**: New users understand immediately
4. **Precedent**: VS Code uses similar pattern for workspace indicators

**Implementation**:
- TabItem already has `connectionId` field
- Need to look up connection name from `sessionStates[connectionId]?.profileName`
- Assign deterministic color to each connection (hash profileId to color palette)
- Render: `Canvas` to draw colored circle + connection name suffix in tab title

## Implementation Plan

1. **Create connection color mapper**
   - Function: `getConnectionColor(profileId: String): Color`
   - Use hash of profileId to deterministically assign color from palette
   - Palette: 8 distinct colors (Material3 primary variants)

2. **Update tab rendering logic**
   - Read `tab.connectionId`
   - Look up `sessionStates[connectionId]?.profileName`
   - Render colored dot (Canvas) + title + connection name suffix
   - Global tabs (Favorites, History) get gray dot + no suffix

3. **Remove parent connection tabs**
   - Delete lines 465-483 in App.kt (first PrimaryScrollableTabRow)
   - Remove `activeProfileId` usage for tab selection
   - Keep `activeProfileId` for passing context to screens

4. **Handle connection switching**
   - When tab is clicked, set `sessionViewModel.setActiveProfile(tab.connectionId)`
   - This maintains connection context for screens that need it

5. **Update tab creation logic**
   - When creating QueryEditor tab, include `connectionId = activeProfileId`
   - When creating TableEditor tab, already includes connectionId
   - Favorites/History/QueryLog have `connectionId = null`

## Color Palette for Connections
```kotlin
val connectionColors = listOf(
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFFF44336), // Red
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFE91E63), // Pink
)

fun getConnectionColor(profileId: String): Color {
    val index = profileId.hashCode().absoluteValue % connectionColors.size
    return connectionColors[index]
}
```

## Files to Modify
1. `/app/src/main/kotlin/com/dbeagle/App.kt` - Remove parent tab row, update child tab rendering
2. Create new file: `/app/src/main/kotlin/com/dbeagle/ui/ConnectionIndicator.kt` - Colored dot composable
3. Update toolbar icon clicks to include current connectionId when creating tabs

## Testing Scenarios
1. Open 2 connections, create tabs for each - verify different colored dots
2. Create global tab (Favorites) - verify gray dot, no suffix
3. Switch between tabs - verify activeProfileId updates correctly
4. Close all tabs from one connection - verify connection remains in session
5. Close connection - verify tabs from that connection remain but show "disconnected" state
