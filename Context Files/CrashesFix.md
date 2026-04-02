# App Improvements & Fixes

## 1. UI & Layout Improvements

* Ensure the server address/IP fits properly inside its UI container:

  * Reduce text size dynamically if needed
  * Prevent overflow or clipping
* Fix footer behavior:

  * Footer must NOT move up when the keyboard appears
* Improve Plugins page UI:

  * Remove unnecessary gaps and spacing issues
* Change "Start Server" button color:

  * Use a lighter, more vibrant color (Duolingo-style green)
* Make overall UI more minimalist and clean
* Remove dark mode completely (only light theme)

---

## 2. Navigation & Page Structure

* Create a **separate "Downloaded" page**:

  * Show all downloaded mods, plugins, and resource packs here
* Remove:

  * Refresh button
  * Add button from current pages
* Add:

  * "Add" button ONLY inside the Downloaded page

---

## 3. Whitelist Feature

* Add a **search bar input** in whitelist page:

  * User can type a player name
  * Allow adding player directly from search input

---

## 4. Server State & Stability

* Fix crashes when clicking:

  * Stop button
  * Restart button
  * Other controls while server is running
* Ensure all server actions are safe and handled properly

---

## 5. Server State Persistence

* Preserve server running state across navigation
* UI must reflect actual server state at all times

---

## 6. World & Backup System

* Calculate total world size including:

  * Overworld
  * Nether
  * End
* Backup system improvements:

  * Backup entire server directory:

    * Worlds
    * Plugins
    * Mods
    * Configs
* Show **backup progress** when user clicks backup:

  * Progress bar or percentage indicator

---

## 7. Live Reload / Hot Reload Features

* Server should support **real-time updates without restart**:

  * Adding mods
  * Adding resource packs
  * Changing render distance
* Avoid requiring server restart for these changes

---

## 8. Data Fetching Fixes

* Ensure app properly fetches and displays:

  * Mods
  * Plugins
  * Resource packs
* Display them correctly inside the UI

---

## 9. Keyboard & Layout Behavior

* Ensure keyboard does not break layout:

  * Footer stays fixed
  * Input fields remain accessible

---

## 10. Code Quality & Architecture

* Fix all crashes and unstable behaviors
* Ensure clean and modular code structure
* Use proper state management
* Prevent unnecessary API calls (add caching where needed)

---

## Goal

Make the app stable, user-friendly, visually clean, and production-ready with proper server handling and UI consistency.
