# Note storage format

**Format version: `1`** (used since Phase 3). Implemented in `:core` → `core/format/NoteFormat.kt`.
Phase 5 (ink + rich text) will add fields or bump the version; readers must **skip** (never
overwrite) files whose `formatVersion` is higher than they understand.

## Location

Google Drive **appDataFolder** (OAuth scope `https://www.googleapis.com/auth/drive.appdata`), a hidden
per-app folder in the user's Drive. Any client using the same Google Cloud project (Android, a future
web/PC client) can read it.

## Files (flat layout)

| File | Content |
|---|---|
| `note-<noteId>.md` | Note body, UTF-8 Markdown (CommonMark). Plain text in v1. |
| `note-<noteId>.json` | Note metadata (`NoteFileV1`). Written **after** the body. |
| `notebook-<notebookId>.json` | Notebook (`NotebookFileV1`), including its event links. |

IDs are UUID strings generated on the device that created the item.

### Drive `appProperties` (on every file)

Used to detect changes from a single file listing without downloading anything.

| Key | Value |
|---|---|
| `kind` | `note` or `notebook` |
| `entityId` | note / notebook id |
| `version` | integer, increases on every change |
| `updatedAt` | epoch milliseconds of the last edit |
| `deleted` | `true` for tombstones |
| `formatVersion` | `1` |

If several files share a name (e.g. after an interrupted retry), readers use the one with the highest
`version`.

## `note-<id>.json` (v1)

```json
{
  "formatVersion": 1,
  "id": "6f1c0c1e-…",
  "notebookId": null,
  "title": "Fizik · 12 Kas",
  "createdAt": "2026-11-12T09:00:00Z",
  "updatedAt": "2026-11-12T09:42:10Z",
  "version": 7,
  "deletedAt": null,
  "bodySha256": "<hex sha-256 of the UTF-8 body>",
  "links": [
    {
      "id": "…",
      "calendarId": "primary",
      "eventId": "abc_20261112T090000Z",
      "recurringEventId": "abc",
      "instanceStartTime": "2026-11-12T09:00:00Z",
      "scope": "INSTANCE",
      "eventTitleSnapshot": "Fizik",
      "eventStartSnapshot": "2026-11-12T09:00:00Z",
      "eventDeleted": false,
      "createdAt": "…",
      "updatedAt": "…",
      "deletedAt": null
    }
  ]
}
```

- Timestamps: ISO-8601 UTC. `scope`: `INSTANCE` (one occurrence; `eventId` is the instance id) or
  `SERIES` (whole recurring series; `eventId` is the series id).
- `bodySha256` must match the body file; on mismatch the reader keeps its current copy and retries
  later (the writer has not finished).
- `deletedAt != null` marks a tombstone; the body is empty. Tombstones are kept so other devices learn
  about the deletion.

## `notebook-<id>.json` (v1)

```json
{
  "formatVersion": 1,
  "id": "…",
  "title": "Fizik",
  "color": "#3B6FE0",
  "createdAt": "…",
  "updatedAt": "…",
  "version": 2,
  "deletedAt": null,
  "eventLinks": [
    { "id": "…", "calendarId": "primary", "eventId": "abc", "recurringEventId": "abc",
      "scope": "SERIES", "eventTitleSnapshot": "Fizik", "createdAt": "…", "deletedAt": null }
  ]
}
```

`eventLinks`: events/series whose new notes are placed in this notebook.

## Sync rules (v1)

Per item, comparing local `version`/`dirty`/`remoteVersion` with the remote `version`/`updatedAt`
(`core/sync/SyncDecision.kt`):

| Local | Remote | Action |
|---|---|---|
| unchanged | unchanged | nothing |
| changed | unchanged | upload |
| unchanged | changed | download |
| changed | changed | **conflict**: later `updatedAt` wins (tie: higher version, then remote). The losing version is kept on the device as a backup (note editor → "Çakışma yedekleri"). The winner is uploaded with `version = max(local, remote) + 1` when local wins. |

Calendar markers (title prefix, description block, `extendedProperties`) are not part of this format;
each device derives them from the synced note links.
