# PocketCraft App – Agent Instructions

## 🚨 Core Stability & Navigation Fixes

### 1. Back Navigation Behavior
- Fix the back button behavior across the entire app.
- ❌ Current issue: Pressing back exits the app unexpectedly.
- ✅ Required behavior:
  - From any screen → navigate to **Home Screen**
  - Only exit app if user is already on Home Screen
  - Show confirmation dialog before exiting:
    - Message: "Are you sure you want to exit?"
    - Options: Yes / No

---

### 2. Navigation Lock During Server Start
- When server is **starting or running**:
  - Disable access to:
    - Version Selector Screen
  - Prevent navigation until:
    - Server is fully started OR stopped

---

## 🎨 UI/UX Overhaul (Duolingo Style)

### Requirements:
- Completely remove **Dark Mode**
- Use:
  - Bright colors
  - Rounded UI elements
  - Friendly and clean layout
  - Smooth animations

### Design Guidelines:
- Large buttons
- Minimal text per screen
- Clear hierarchy
- Card-based UI
- Consistent spacing and padding

---

## ⚙️ Functional Fixes

### 1. Server Controls Stability
Fix crashes when:
- Stop button is pressed
- Restart button is pressed

Ensure:
- Proper state handling
- No null crashes
- Safe thread handling
- UI updates correctly after state change

---

### 2. Resource Fetching Fix
Fix issues where app is NOT fetching:
- Plugins
- Mods
- Resource Packs

Ensure:
- Proper API calls
- Handle:
  - Loading state
  - Errors
  - Empty results
- Add retry mechanism if fetch fails

---

## 📦 Plugins Section Upgrade

### Add New Screen:
**Downloaded Plugins Screen**

Features:
- Show all downloaded plugins
- Display:
  - Plugin name
  - Version
  - Status (Enabled/Disabled)
- Options:
  - Enable / Disable
  - Delete plugin

Navigation:
- Plugins Screen → "Downloaded Plugins" button → New Screen

---

## 👥 Player Details Feature

### Add Player Details Page

Trigger:
- Opens when clicking on a player

Display:
- Player Name
- UUID
- Online Status
- Ping
- Health (if available)
- Server Stats (optional)

UI:
- Card-based layout
- Clean and readable

---

## 🔒 State Management Improvements

Ensure:
- Server state is preserved across:
  - Screen switches
  - Navigation
- UI reflects actual server state
- No false "server not started" messages

---

## 🧠 General Requirements

- Prevent crashes at all costs
- Add proper error handling everywhere
- Ensure smooth navigation transitions
- Maintain consistent UI design
- Optimize performance where needed

---

## ✅ Expected Outcome

- Smooth navigation (no accidental exits)
- Stable server controls (no crashes)
- Fully working plugin/mod/resource fetching
- Clean Duolingo-style UI
- Improved user experience
- New functional screens (Plugins + Player Details)

---

## ⚠️ Priority Order

1. Fix navigation (back button issue)
2. Fix crashes (stop/restart)
3. Lock navigation during server start
4. Fix fetching (plugins/mods/resources)
5. Add new screens (plugins + player details)
6. UI overhaul

---

End of Instructions