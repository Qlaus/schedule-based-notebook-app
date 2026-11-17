# Privacy Policy – Not Takvimi (CalNotes)

_Last updated: 2026-11-17_

Not Takvimi is a personal, non-commercial note-taking app for Android that works with Google Calendar.

## What the app accesses
- **Google Calendar** (`calendar.events`, `calendar.calendarlist.readonly`): to show your calendars and
  events, and to create, edit or delete events you choose to change in the app. When you attach a
  note to an event, the app may add a `📝` prefix to the event title and store the note's ID in the
  event's private extended properties.
- **Google Drive app data folder** (`drive.appdata`): to store and sync your notes in a hidden,
  app-specific folder in your own Google Drive. The app cannot see any other files in your Drive.
- **Basic profile** (`openid`, `email`): to identify which Google account is signed in.

## Where data is stored
- On your device (local database), and in your own Google account (Calendar and Drive app data folder).
- The app has **no server of its own**. No data is sent to the developer or to any third party.
- No analytics, advertising or tracking SDKs are used.

## Security
OAuth tokens are stored encrypted on the device using the Android Keystore. Tokens and personal data
are never logged.

## Your control
You can sign out in the app, uninstall it, and revoke access at any time at
https://myaccount.google.com/permissions. Removing the app's access does not delete your notes from
your Drive app data folder; deleting the app's hidden data can be done from Google Drive settings
(Manage apps).

## Use of Google user data
The app's use of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements.

## Contact
Open an issue in the project repository: https://github.com/Qlaus/schedule-based-notebook-app
