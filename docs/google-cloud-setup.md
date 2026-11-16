# Google Cloud setup (one-time)

Console: https://console.cloud.google.com

1. **Create project** – project picker → *New project* → name `calnotes` → *Create*. Select it.
2. **Enable APIs** – *APIs & Services → Library*: enable **Google Calendar API** and **Google Drive API**.
3. **Branding / consent** – *Google Auth Platform* (APIs & Services → OAuth consent screen) → *Get started*:
   app name `Not Takvimi`, support email, audience **External**, contact email, accept policy → *Create*.
4. **Data access (scopes)** – *Data Access → Add or remove scopes*, add:
   - `openid`, `.../auth/userinfo.email`
   - `https://www.googleapis.com/auth/calendar.events`
   - `https://www.googleapis.com/auth/calendar.calendarlist.readonly`
   - `https://www.googleapis.com/auth/drive.appdata`
   → *Update* → *Save*.
5. **Audience** – add your Google account under *Test users*, then **Publish app → In production**.
   Do not submit for verification. Sign-in will show "Google hasn't verified this app" once:
   *Advanced → Go to Not Takvimi (unsafe)*. Limit: 100 users. Refresh tokens no longer expire after 7 days.
6. **Client** – *Clients → Create client*:
   - Application type: **Android**
   - Package name: `io.github.qlaus.calnotes`
   - SHA-1: `39:94:B2:B1:7A:ED:53:62:3A:05:24:FE:80:04:E1:C3:C2:93:80:80`
   - *Create*, then open the client → *Advanced settings* → enable **Custom URI scheme** → *Save*.
7. Copy the **Client ID** (`…apps.googleusercontent.com`) into `gradle.properties` →
   `calnotes.googleClientId`.

Redirect URI used by the app: `io.github.qlaus.calnotes:/oauth2redirect`.
