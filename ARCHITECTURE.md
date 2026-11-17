# Architecture

## Modules

| Module | Type | Purpose |
|---|---|---|
| `:core` | Pure Kotlin/JVM | Domain models, event-link matching, note file format, conflict rules. No Android dependency, so a future desktop client can reuse it and tests run fast. |
| `:app` | Android app | UI (Compose), Room, AppAuth, Retrofit APIs, WorkManager, DI (Hilt). |

## Layers (`:app`)

```
ui (Compose screens + ViewModels)  ──observes──▶  Room (Flow)
        │ actions
        ▼
repositories (NoteRepository, CalendarRepository, ...)
        │                          │
        ▼                          ▼
   Room DAOs                Remote APIs (Retrofit: Calendar v3, Drive v3)
                                   │
                            AuthInterceptor ── TokenStore (AppAuth AuthState, Tink-encrypted)
```

- **Offline-first:** every write goes to Room first; the UI only observes Room. Network work
  (calendar refresh, event outbox, Drive sync) runs on top and updates Room.
- **MVVM + Repository**, Hilt for DI, Coroutines + Flow.
- **Adaptive layout:** list–detail two-pane on wide screens (calendar left, note right), single pane
  on narrow windows.

## Packages (`io.github.qlaus.calnotes`)

`auth/` · `data/local/` · `data/remote/calendar/` · `data/remote/drive/` · `data/repo/` · `sync/` ·
`di/` · `ui/{theme,adaptive,calendar,note,notebook,search,settings}/`

## Data model (Room)

```
Notebook 1───* Note 1───* EventLink
                 │
                 └──* NoteBackup        (versions overwritten by sync conflicts)

Notebook 1───* NotebookLink             (event occurrence or series → notebook; new notes go there)

CalendarEntity 1───* EventEntity        (offline cache of Google Calendar)
EventMarker                             (what we last wrote to a Google event: prefix / block / note ids)
PendingEventOp                          (outbox for event create/update/delete)
SyncState                               (key/value: Drive page token, cached ranges, ...)
```

- IDs are client-generated UUIDs.
- Sync bookkeeping on Note/Notebook: `version`, `updatedAt`, `dirty`, `remoteFileId`, `remoteVersion`.
- Deletes are soft (`deletedAt` tombstone) and propagate through sync.
- `EventLink(scope = INSTANCE | SERIES, calendarId, eventId, recurringEventId, instanceStartTime)`;
  matching logic lives in `:core` (`EventLinkMatcher`). Title/start snapshots keep notes readable
  after the event is deleted (`eventDeleted = true`; the note is never deleted).
- `accountId` (Google `sub`) is stored on rows for future multi-account support; the UI is single-account.

## Auth (no Google Play Services)

1. AppAuth opens Google's authorization endpoint in a Custom Tab (or the default browser).
2. Google redirects to `io.github.qlaus.calnotes:/oauth2redirect`, caught by AppAuth's
   `RedirectUriReceiverActivity`.
3. The code is exchanged with PKCE (no client secret).
4. `AuthState` JSON is encrypted with Tink AEAD (key in Android Keystore) and stored in DataStore.
5. `AuthInterceptor` calls `performActionWithFreshTokens` under a mutex; a 401 forces one refresh and
   one retry; `invalid_grant` moves the app into a "sign in again" state without touching local data.

Scopes: `openid email`, `calendar.events`, `calendar.calendarlist.readonly`, `drive.appdata`.

Tokens and personal data are never logged. HTTP logging exists only in debug builds and redacts
`Authorization`.

## Calendar cache and outbox (Phase 1)

- `events.list` with `singleEvents=true` per visible calendar and date range; the cached range is
  replaced (stale rows removed) on every refresh. Sync window: 92 days back, 366 days ahead, plus the
  range currently on screen.
- Every event write goes through `pending_event_ops` (outbox) and is applied to the cache
  optimistically; `flushOutbox()` sends ops in order. New events use a temporary `local…` id that is
  remapped (event row, note links, later ops) when Google returns the real id.
- Network/5xx errors keep the op for the next sync; 4xx rejections drop it (server state wins) and
  surface a message.
- PATCH payloads send explicit `null` for the unused of `date`/`dateTime`, so an event can switch
  between all-day and timed. "Whole series" edits change text fields only (on the series master).
- Linked events missing after a refresh are re-checked with `events.get`; 404/410/cancelled marks the
  link `eventDeleted` and the note stays.

## Sync triggers

App start, app foreground (`ProcessLifecycleOwner`), pull-to-refresh, and periodic WorkManager
(best-effort; Huawei may kill background work).

## Calendar write-back (Phase 2)

`MarkerSync.reconcile()` runs on every sync and right after note/settings changes:
- Desired state per Google event (instance id, or series master id for series links): the note ids
  of active links, plus title/description markers according to settings.
- It compares with `event_markers` (what it last wrote) and only PATCHes differences, after a fresh
  `events.get`, touching just `summary` / `description` / `extendedProperties.private.calnotesNoteIds`.
- Title prefix `📝 ` (default **on**); description block `📝 Notes attached (Not Takvimi)` appended at
  the end (default off). Both are added/removed only by `CalendarMarker` (`:core`), so the user's own
  text is never changed. The cache stores titles/descriptions without markers plus `markerIn*` flags;
  in-app edits re-add the markers when sending.
- Events the user cannot edit (attendee copies, birthdays, read-only calendars) are skipped once and
  remembered; the note still shows in the app.

## Notes sync with Google Drive (Phase 3)

`DriveSync` (two-way, `appDataFolder`, format in [docs/note-format.md](docs/note-format.md)):
1. One `files.list` with `appProperties` gives every remote item's id/version/updatedAt.
2. Notebooks, then notes, are resolved with `SyncDecision` (`:core`): upload, download, or conflict
   (last writer wins; loser saved in `note_backups`, restorable in the note editor).
3. Uploads write the body file before the metadata file; metadata holds the body's SHA-256, so a
   reader detects half-finished uploads and retries later. `markSynced` is compare-and-set on
   `version`, so edits made during an upload are not lost.
4. Link changes made outside the editor (event deleted, temporary id replaced, notebook linked)
   bump the owning note/notebook so they propagate.

Triggers: full sync (app start/foreground, pull-to-refresh, periodic worker), and 4 s after local
note changes. Status (`DriveStatus`) is shown in the calendar header, note editor and settings.

## Database migrations

Room schema version 3 (`MIGRATION_2_3` adds `notebooks.remoteVersion`). `AppDatabase.MIGRATION_1_2` adds `notebook_links`, `event_markers` and the
`events.markerInTitle/markerInDescription` columns. `MigrationTest` builds a v1 database with data and
opens it with the current schema, so Room validates the migration in CI.
