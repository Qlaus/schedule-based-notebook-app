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

CalendarEntity 1───* EventEntity        (offline cache of Google Calendar)
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

## Sync triggers

App start, app foreground (`ProcessLifecycleOwner`), pull-to-refresh, and periodic WorkManager
(best-effort; Huawei may kill background work).

## Calendar write-back (Phase 2)

- `extendedProperties.private.calnotesNoteIds` stores attached note IDs.
- Title prefix `📝 ` is added (default **on**) only where the user may edit the event
  (organizer or `guestsCanModify`). The app strips the prefix when displaying titles and removes it
  when the last note is detached. The existing description is never modified unless the optional
  description marker setting is enabled.
