# Not Takvimi (CalNotes)

A calendar-based note-taking app for Android tablets (target: Huawei MatePad Pro, HarmonyOS 4.2,
**no Google Play Services**). Every Google Calendar event can get a note page or be linked to a
notebook. Notes are stored offline-first and synced to the user's Google Drive `appDataFolder`.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Project skeleton, theme, CI-built APK, Google Cloud setup | ✅ done |
| 1 | Google sign-in, calendar views, event CRUD, local plain-text notes, two-pane | ✅ done (verified on MatePad) |
| 2 | Notebooks, search, calendar title marker | 🧪 in device test |
| 3 | Drive `appDataFolder` sync | 🧪 in device test |
| 4 | Local reminders, Markdown/PDF export | — |
| 5 | Stylus ink (androidx.ink) and rich text | — |

See [docs/PLAN.md](docs/PLAN.md) for the full plan and decisions.

## Key constraints

- No `play-services-*`, Firebase or Google Sign-In SDK. Auth is OAuth 2.0 + PKCE via
  [AppAuth-Android](https://github.com/openid/AppAuth-Android); Calendar and Drive are called over REST.
  `./gradlew checkNoGms` fails the build if a GMS/Firebase artifact appears on the classpath.
- Background work is unreliable on Huawei, so sync runs on app start, on foreground,
  on pull-to-refresh and (best-effort) via WorkManager.

## Getting an APK

Easiest (no PC): on the tablet open the repo's **Releases** page → **Development build (latest)** → tap the `.apk`.

Every push also builds APKs on GitHub Actions (workflow **Android build**). Open the run → *Artifacts* →
`calnotes-debug-apk`. Install with `adb install -r app-debug.apk`. Details: [docs/device-setup.md](docs/device-setup.md).

## Building locally

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew checkNoGms :core:test :app:testDebugUnitTest :app:assembleDebug
```

## Configuration

`gradle.properties` → `calnotes.googleClientId` holds the OAuth client ID of the Android client
(see [docs/google-cloud-setup.md](docs/google-cloud-setup.md)). It is not a secret: native apps are
public OAuth clients without a client secret.

All builds are signed with `keystore/dev.keystore` so the SHA-1 registered in Google Cloud stays the
same on every machine and in CI. This key is for personal/development distribution only.

SHA-1: `39:94:B2:B1:7A:ED:53:62:3A:05:24:FE:80:04:E1:C3:C2:93:80:80`

## Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) – modules, layers, data model, auth and sync design
- [docs/PLAN.md](docs/PLAN.md) – phased plan and product decisions
- [docs/google-cloud-setup.md](docs/google-cloud-setup.md) – one-time Google Cloud setup
- [docs/device-setup.md](docs/device-setup.md) – installing on the MatePad / emulator
- [docs/note-format.md](docs/note-format.md) – platform-independent note storage format
