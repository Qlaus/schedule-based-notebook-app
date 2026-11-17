# Plan

## Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | OAuth publishing status | **In production, unverified.** One-time "unverified app" warning; refresh tokens do not expire after 7 days. |
| 2 | APK delivery | **GitHub Actions** builds debug + release APKs on every push (artifacts). |
| 3 | Accent color | Calm blue `#3B6FE0` (dark theme `#7FA3F2`). |
| 4 | Tapping an event | Always open the **event detail** first (with "Create note" / attached notes). |
| 6 | Series ↔ notebook | Linking a recurring event's series to a notebook places every new occurrence note in it. |
| 5 | Calendar marker | **`📝 ` title prefix, on by default** (editable events only). Description marker optional, off by default. `extendedProperties.private` always stores note IDs. |
| – | Week start / clock | Monday, 24-hour. |
| – | Calendar cache window | 3 months back, 12 months ahead, plus on-demand ranges. |
| – | Token storage | Tink AEAD + Android Keystore in DataStore (EncryptedSharedPreferences is deprecated). |
| – | Calendar UI | Custom Compose views (no third-party calendar library). |

## Phase 0 — Setup ✅
- Gradle skeleton with version catalog, `:core` + `:app`, Hilt, Compose theme.
- Shared dev signing key (stable SHA-1 for the OAuth Android client).
- CI: `checkNoGms`, unit tests, debug/release APK artifacts.
- Docs: Google Cloud setup, device setup.

## Phase 1 — Calendar + local notes ✅
1. AppAuth sign-in, encrypted token storage, sign-out, re-auth state.
2. Calendar list with visibility toggles; ranged event fetch cached in Room.
3. Month, week, day (time grid, overlapping events side by side) and agenda views; pull-to-refresh.
4. Event create/edit/delete (title, date/time, all-day, location, description, calendar); offline outbox.
5. Event detail → create plain-text note or open existing ones; recurring: "only this occurrence / whole series"; "add another note"; autosave.
6. Two-pane adaptive layout, light/dark theme, all strings in resources.
7. Sync triggers: start, foreground, pull-to-refresh, periodic WorkManager.
8. Unit tests: repositories, link matching, outbox, token refresh.

## Phase 2 — Notebooks, search, calendar marker 🧪
- Navigation rail (tablet) / bottom bar (narrow): Calendar · Notebooks · Search · Settings.
- Notebooks CRUD with colors; deleting keeps notes (moved to "unfiled"). Standalone notes.
- Event detail: link occurrence/series to a notebook; note editor: move between notebooks.
- Search over notes and cached events by title, notebook, event name and date spellings.
- Calendar markers via `MarkerSync` (title prefix on by default, description block optional).
## Phase 3 — Drive `appDataFolder` sync 🧪
- Flat file layout with appProperties for change detection; Markdown body + JSON metadata (format v1).
- Last-writer-wins with conflict backups (restorable), tombstones, compare-and-set uploads.
- Status in header / note editor / settings ("Şimdi eşitle"); push 4 s after edits.
- Second-device test: reinstall (clear data) or another Android device, same Google account.
## Phase 4 — Local reminders, Markdown/PDF export
## Phase 5 — Stylus ink (androidx.ink) and rich text; revisit note/sync format
