/**
 * Google OAuth for the web client ("Web application" OAuth client in Google Cloud).
 * Not secret: browser apps are public clients. Set via VITE_GOOGLE_CLIENT_ID at build time.
 */
export const GOOGLE_CLIENT_ID: string = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''

export const isClientConfigured = () => GOOGLE_CLIENT_ID.endsWith('.apps.googleusercontent.com')

export const SCOPES = [
  'openid',
  'email',
  'https://www.googleapis.com/auth/calendar.events',
  'https://www.googleapis.com/auth/calendar.calendarlist.readonly',
  'https://www.googleapis.com/auth/drive.appdata',
]

/** The app's own URL (also the OAuth redirect URI); works under a GitHub Pages sub-path. */
export const redirectUri = () => new URL(import.meta.env.BASE_URL, window.location.origin).toString()
