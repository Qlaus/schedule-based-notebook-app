# Note storage format

**Status:** draft, format version `1`. Finalized in Phase 3 (Drive sync) and revisited in Phase 5
(ink + rich text). Readers must reject `formatVersion` values they do not know.

Location: Google Drive `appDataFolder` (scope `drive.appdata`), shared by all clients that use the
same Google Cloud project.

## Layout

```
appDataFolder/
  notes/<noteId>/
    note.json      # metadata (written last)
    body.md        # note body, UTF-8 Markdown (CommonMark)
  notebooks/<notebookId>.json
```

`note.json` is always written after `body.md`; its `bodySha256` lets a reader detect a half-finished
upload (hash mismatch → keep the previous local version and retry later).

## `note.json` (v1)

```json
{
  "formatVersion": 1,
  "id": "6f1c0c1e-…",
  "notebookId": null,
  "title": "Fizik – 12 Kasım",
  "createdAt": "2026-11-12T09:00:00Z",
  "updatedAt": "2026-11-12T09:42:10Z",
  "version": 7,
  "deletedAt": null,
  "bodySha256": "…",
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
      "eventDeleted": false
    }
  ]
}
```

- Timestamps: ISO-8601 UTC. IDs: UUID strings.
- `deletedAt != null` marks a tombstone; `body.md` may be empty.
- Conflict rule (v1): higher `version` wins, ties broken by later `updatedAt`; the losing version is
  kept as a local backup.

## `notebooks/<id>.json` (v1)

```json
{ "formatVersion": 1, "id": "…", "title": "Fizik", "color": "#3B6FE0",
  "createdAt": "…", "updatedAt": "…", "version": 2, "deletedAt": null }
```
