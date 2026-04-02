# Server Customization & UI Polish Plan

## Overview
Enhance server details UI with Duolingo aesthetics, add customization options (photo upload, description), optimize chunk loading, and refactor redundant settings.

---

## 📋 Tasks Overview

### 1. Server Details Card Redesign
**Goal**: Match Duolingo's playful, cohesive design language

#### Changes Required:
- **Photo Upload Button** (NEW)
  - Replace: Static "Server photo URL" field
  - Add: Upload button with Duolingo green (#7FE620) styling
  - Behavior: Accepts photos from phone gallery/camera
  - Storage: Save to server's local config directory
  - UI: Shows current photo preview or placeholder emoji

- **Server Description Field** (NEW)
  - Position: Below server name
  - Styling: Green border matching input fields (Duolingo theme)
  - Functionality: Multi-line text input for server description
  - Persistence: Saved in ServerConfig model
  - Placeholder: "Describe your server..."

#### Files to Modify:
- `data/model/ServerConfig.kt` - Add `photoUri` and `description` fields
- `ui/screens/ServerDetailsScreen.kt` - Create new screen or replace old one
- `ui/components/ServerPhotoUpload.kt` (NEW) - Photo upload component
- `ui/components/ServerDescriptionField.kt` (NEW) - Description input component

---

### 2. Remove Duplicate Settings
**Goal**: Consolidate server photo URL and description options

#### Settings to Remove:
- Settings tab: "Server Photo URL" field
- Settings tab: "Server Description" field (if present)

#### Why:
- Avoids duplication and confusion
- Consolidates customization in one place (Server Details)
- Cleaner, more focused Settings tab

#### Files to Modify:
- `ui/screens/SettingsScreen.kt` - Remove photo URL and description sections

---

### 3. App Icon Top-Left Enhancement
**Goal**: Bigger, more visible with better rounded corners

#### Current:
- Size: 40dp
- Corner radius: Standard

#### New:
- Size: **56dp** (Material Design recommended for prominent icons)
- Corner radius: **16dp** (more rounded for Duolingo playfulness)
- Animation: Slight pulsing on hover/tap (optional enhancement)

#### Files to Modify:
- `ui/navigation/PocketNavigation.kt` - Update icon size and shape
- `ui/components/TopAppBar.kt` - If custom implementation exists

---

### 4. Chunk Loading Optimization
**Goal**: Fix slow chunk loading on new servers while preserving backup restore performance

#### Current Issues:
- ❌ New servers: Chunks load too slowly
- ✅ Backup restore: Works well (good rendering performance)

#### Root Cause Analysis Needed:
- Check `ServerLauncher.kt` for initial world generation settings
- Compare: New server startup vs backup restore server startup
- Server properties affecting chunk preloading

#### Solution Approach:
1. **Identify bottleneck**: Is it JRE rendering or world generation?
2. **Apply optimization**:
   - Increase available heap memory for new servers
   - Pre-generate chunks on startup if not present
   - Reduce view distance on initial load, increase after stable
   - Or: Enable async chunk loading

3. **Preserve backup behavior**: Ensure existing optimization doesn't regress

#### Files to Investigate:
- `server/ServerLauncher.kt` - Server startup logic
- `service/ServerFileManager.kt` - File/world management
- `service/ServerPropertiesHelper.kt` - server.properties configuration
- `ui/screens/ConsoleScreen.kt` - Monitor chunk loading logs

---

## 🎨 Duolingo Design Integration

### Color Palette (Already Established):
- Primary Green: #7FE620
- Dark Background: #1a1a1a (or theme-based)
- White Text: #FFFFFF
- Light Gray: #F5F5F5

### Components to Match:
```
✅ DuoButton - Already implemented (3D shadow, green styling)
✅ PlayerActionButton - Color-coded actions
✅ Input Fields - Green borders/underlines
✅ RadioButtons - Custom styled
❌ Photo Upload - NEEDS GREEN BUTTON STYLING
❌ Description Field - NEEDS GREEN BORDER
```

### Button Styling for Photo Upload:
- Background: #7FE620
- Text: Black or brand-specific
- Shape: Rounded corners (12dp)
- Icon: Camera or image icon
- Hover: Slightly darker green or subtle animation
- Size: 48dp height (standard button)

---

## 📁 File Modification Summary

| File | Change Type | Priority |
|------|------------|----------|
| `data/model/ServerConfig.kt` | ADD fields (photoUri, description) | HIGH |
| `ui/screens/ServerDetailsScreen.kt` | CREATE/REDESIGN | HIGH |
| `ui/components/ServerPhotoUpload.kt` | CREATE | HIGH |
| `ui/components/ServerDescriptionField.kt` | CREATE | HIGH |
| `ui/screens/SettingsScreen.kt` | REMOVE duplicates | MEDIUM |
| `ui/navigation/PocketNavigation.kt` | UPDATE icon | MEDIUM |
| `server/ServerLauncher.kt` | INVESTIGATE chunk loading | HIGH |
| `service/ServerFileManager.kt` | CHECK for optimization | MEDIUM |
| `service/ServerPropertiesHelper.kt` | CHECK properties | MEDIUM |

---

## 🔄 Data Model Changes

### ServerConfig.kt Updates:
```kotlin
data class ServerConfig(
    val name: String,
    val description: String = "", // NEW
    val photoUri: String? = null, // NEW - local file path or URI
    val seed: Long = System.currentTimeMillis(),
    // ... existing fields
)
```

---

## ✨ UI Component Structure

### Server Details Screen Flow:
```
┌─────────────────────────────────┐
│  ServerDetailsScreen            │
├─────────────────────────────────┤
│ [Upload Photo]  [Photo Preview] │  ← ServerPhotoUpload
├─────────────────────────────────┤
│        Server Name              │  ← TextField
├─────────────────────────────────┤
│      Server Description         │  ← ServerDescriptionField
│ (Multi-line text input)         │
├─────────────────────────────────┤
│  [CANCEL]  [SAVE]               │
└─────────────────────────────────┘
```

---

## 🧪 Testing Checklist

- [ ] Photo upload works on new servers
- [ ] Photo persists after app restart
- [ ] Description field accepts multi-line text
- [ ] Removed settings fields no longer appear
- [ ] App icon displays at 56dp with 16dp corners
- [ ] Chunk loading on new servers performs better
- [ ] Chunk loading on restored backups still performs well
- [ ] Settings screen no longer has duplicate photo/description options
- [ ] Server details card matches Duolingo green aesthetic

---

## 🚀 Implementation Order

1. **Phase 1** (Data Layer):
   - Update `ServerConfig.kt` with new fields
   - Migrate existing servers (null→default values)

2. **Phase 2** (Components):
   - Create `ServerPhotoUpload.kt` component
   - Create `ServerDescriptionField.kt` component
   - Create `ServerDetailsScreen.kt` with Duolingo styling

3. **Phase 3** (Cleanup):
   - Remove duplicates from `SettingsScreen.kt`

4. **Phase 4** (Cosmetics):
   - Update app icon size to 56dp
   - Increase border radius to 16dp

5. **Phase 5** (Performance):
   - Investigate chunk loading bottleneck
   - Apply optimization to `ServerLauncher.kt` or related files

---

## 📌 Notes

- All components should use `MaterialTheme.colorScheme.primary` (#7FE620) for consistency
- Photo upload should validate file type (JPG, PNG, WEBP)
- Description field should have reasonable character limit (e.g., 500 chars)
- Icon corner radius increase is non-breaking and can be deployed anytime
- Chunk loading fix requires performance testing before/after
