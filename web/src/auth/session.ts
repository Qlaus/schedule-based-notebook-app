import { GOOGLE_CLIENT_ID, SCOPES, redirectUri } from './config'

/**
 * Browser sign-in with Google's OAuth 2.0 redirect flow (response_type=token). No popups, because
 * popups are unreliable in iOS home-screen apps. Google gives browser apps no refresh token, so the
 * ~1 hour access token is renewed with a silent redirect (prompt=none) when the app is opened or
 * comes back to the foreground. Everything is autosaved locally, so the short reload loses nothing.
 */
export interface Account { sub: string; email: string | null }

interface StoredToken { accessToken: string; expiresAt: number; scope: string }

const TOKEN_KEY = 'calnotes.token'
const ACCOUNT_KEY = 'calnotes.account'
const STATE_KEY = 'calnotes.oauthState'
const SILENT_KEY = 'calnotes.lastSilent'

export type RedirectResult =
  | { kind: 'none' }
  | { kind: 'signedIn' }
  | { kind: 'error'; error: string }

export function getAccount(): Account | null {
  try {
    return JSON.parse(localStorage.getItem(ACCOUNT_KEY) ?? 'null')
  } catch {
    return null
  }
}

function getToken(): StoredToken | null {
  try {
    return JSON.parse(localStorage.getItem(TOKEN_KEY) ?? 'null')
  } catch {
    return null
  }
}

/** A token valid for at least another minute, or null. Never logged. */
export function getAccessToken(): string | null {
  const t = getToken()
  return t && t.expiresAt - 60_000 > Date.now() ? t.accessToken : null
}

export function tokenExpiresAt(): number | null {
  return getToken()?.expiresAt ?? null
}

export function hasDriveScope(): boolean {
  return (getToken()?.scope ?? '').includes('drive.appdata')
}

export function startSignIn(opts: { silent?: boolean } = {}) {
  const state = crypto.randomUUID()
  sessionStorage.setItem(STATE_KEY, state)
  if (opts.silent) sessionStorage.setItem(SILENT_KEY, String(Date.now()))
  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: redirectUri(),
    response_type: 'token',
    scope: SCOPES.join(' '),
    include_granted_scopes: 'true',
    state,
  })
  const account = getAccount()
  if (account?.email) params.set('login_hint', account.email)
  if (opts.silent) params.set('prompt', 'none')
  window.location.assign(`https://accounts.google.com/o/oauth2/v2/auth?${params}`)
}

/** Renews the token silently, at most once a minute (avoids redirect loops). Returns false if skipped. */
export function renewSilently(): boolean {
  const last = Number(sessionStorage.getItem(SILENT_KEY) ?? 0)
  if (Date.now() - last < 60_000) return false
  startSignIn({ silent: true })
  return true
}

/** Reads Google's answer from the URL fragment after the redirect back to the app. */
export async function handleRedirect(): Promise<RedirectResult> {
  const hash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : ''
  const params = new URLSearchParams(hash)
  if (!params.has('access_token') && !params.has('error')) return { kind: 'none' }
  // Remove the token from the address bar and history right away.
  history.replaceState(null, '', window.location.pathname + window.location.search)

  const expected = sessionStorage.getItem(STATE_KEY)
  sessionStorage.removeItem(STATE_KEY)
  if (!expected || params.get('state') !== expected) return { kind: 'error', error: 'state_mismatch' }
  const error = params.get('error')
  if (error) return { kind: 'error', error }

  const token: StoredToken = {
    accessToken: params.get('access_token')!,
    expiresAt: Date.now() + Number(params.get('expires_in') ?? 3600) * 1000,
    scope: params.get('scope') ?? '',
  }
  localStorage.setItem(TOKEN_KEY, JSON.stringify(token))
  try {
    const res = await fetch('https://openidconnect.googleapis.com/v1/userinfo', {
      headers: { Authorization: `Bearer ${token.accessToken}` },
    })
    if (res.ok) {
      const info = (await res.json()) as { sub: string; email?: string }
      localStorage.setItem(ACCOUNT_KEY, JSON.stringify({ sub: info.sub, email: info.email ?? null }))
    }
  } catch {
    // Offline right after sign-in: keep the token, account info is fetched next time.
  }
  return { kind: 'signedIn' }
}

export async function signOut() {
  const token = getToken()?.accessToken
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ACCOUNT_KEY)
  if (token) {
    try {
      await fetch(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(token)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      })
    } catch {
      // ignore
    }
  }
}

export const accountId = () => getAccount()?.sub ?? 'local'
