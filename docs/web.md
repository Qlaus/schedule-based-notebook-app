# Web app (iPhone + desktop)

A PWA in `web/` (TypeScript, React, Vite, Dexie/IndexedDB). Same Google account, same calendar, same
notes as the Android app: it reads and writes the Drive `appDataFolder` in format v1
([note-format.md](note-format.md)) with the same sync rules.

Live URL (GitHub Pages): `https://qlaus.github.io/schedule-based-notebook-app/`

## Structure

| Path | Purpose |
|---|---|
| `src/core/` | Ports of the Kotlin `:core` module (format, sync decision, link matching, markers, search, overlap layout). Tests mirror the Kotlin tests, and one test checks byte-identical JSON with the Android writer. |
| `src/data/db.ts` | IndexedDB schema (mirrors the Room entities). |
| `src/api/` | Google Calendar / Drive REST over `fetch` (CORS, bearer token). |
| `src/auth/` | OAuth 2.0 redirect flow (`response_type=token`), token storage, silent renewal. |
| `src/repo/` | Repositories and sync: calendar cache + outbox, notes, notebooks, Drive sync, calendar markers, coordinator. |
| `src/ui/` | React UI: rail/tab bar navigation, calendar (day/week/month/agenda), event detail & editor, note editor, notebooks, search, settings. |

## Auth notes

- Browser apps get no refresh token from Google, so the ~1 h access token is renewed with a silent
  redirect (`prompt=none`) when the app opens or returns to the foreground. All edits are already in
  IndexedDB and the view is restored from `sessionStorage`, so the reload loses nothing.
- No popups (unreliable in iOS home-screen apps).
- Token in `localStorage`; never logged. The URL fragment with the token is removed immediately.

## Develop

```bash
cd web
npm ci
npm test          # vitest (core + repositories against a fake Google API)
npm run dev       # http://localhost:5173/schedule-based-notebook-app/
npm run build     # dist/ (PWA with service worker)
```

`VITE_GOOGLE_CLIENT_ID` (in `web/.env.production`) holds the Web OAuth client ID.
`VITE_BASE` overrides the base path (use `/` for a custom domain).

## Deploy

`.github/workflows/web.yml` runs tests, builds and deploys to GitHub Pages on every push that touches
`web/`.

## One-time setup (owner)

1. Repository → **Settings → General → Danger zone → Change visibility → Public**.
2. Repository → **Settings → Pages → Build and deployment → Source: GitHub Actions**.
3. Google Cloud Console → **Google Auth Platform → Clients → Create client**:
   - Application type: **Web application**, name `Not Takvimi Web`
   - Authorized JavaScript origins: `https://qlaus.github.io`
   - Authorized redirect URIs: `https://qlaus.github.io/schedule-based-notebook-app/`
4. **Branding → Authorized domains**: add `qlaus.github.io` (if asked).
5. Put the new client ID into `web/.env.production`.
