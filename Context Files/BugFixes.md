# App Bugs, Fixes & Improvements (Server Control + Player System)

## 1. Server Control UI Issues

* Fix button state inconsistencies:

  * When "Stop" is pressed, UI should ONLY show "Stopping..."
  * "Restarting" must NOT appear when Stop is clicked
* Fix Restart button UI glitch:

  * Prevent visual overlap or incorrect state rendering
* Improve button responsiveness:

  * Ensure proper state transitions (Start → Running → Stopping → Stopped)

---

## 2. Critical Server Stop Issue 🚨

* Server does NOT stop properly:

  * Console shows: "Stopping server..." and "Stop requested"
  * But server continues running
* Even after app is closed:

  * Server still runs in background
  * Users cannot connect
* Fix requirements:

  * Ensure proper process termination
  * Kill server process completely
  * Sync app lifecycle with server lifecycle
  * Remove stuck background execution
  * Ensure notification state matches actual server state

---

## 3. Performance Issue

* Rendering/UI has become slower
* Investigate:

  * Heavy UI operations on main thread
  * Unoptimized recompositions (if using Compose)
  * Blocking operations
* Fix:

  * Move heavy work to background threads
  * Optimize UI updates

---

## 4. Teleport System Fix

* Currently only teleports to:

  * Current position
* Add support for:

  * Respawn location
  * Last death location
* Ensure correct command handling

---

## 5. Player Actions Issues

* Heal button not working:

  * Fix healing logic
  * Ensure command executes properly
* Other buttons are working fine

---

## 6. Inventory Display Bug

* Inventory is not showing any data
* Fix:

  * Ensure proper data fetching
  * Display items correctly in UI

---

## 7. Playtime Formatting

* Current format is unclear
* Update to:

  * Hours and Minutes format
  * Example: "2h 35m"

---

## 8. Player Detail Card Improvements

* Add real-time player stats:

  * Health bar ❤️
  * Hunger bar 🍗
* Ensure:

  * Live updates
  * Accurate values from server

---

## 9. Player Kick Issue

* After kicking a player:

  * Game shows "Pinging..."
  * Player cannot rejoin
* Fix:

  * Ensure proper disconnection handling
  * Reset connection state correctly

---

## 10. Backup & Server Sync Issues

* Ensure:

  * No operations conflict while server is running
  * Proper sync between UI and server state

---

## 11. General Stability Fixes

* Fix crashes when:

  * Clicking Stop / Restart
  * Interacting while server is running
* Add proper error handling and logging

---

## Goal

Make server controls reliable, fix critical stop issue, improve performance, and ensure all player-related features work correctly with real-time updates.
