import { useEffect, useState } from 'react'
import { getAccessToken, getAccount, handleRedirect, renewSilently, signOut, tokenExpiresAt } from '../auth/session'
import { clearCalendarCache } from '../repo/calendar'
import { recordError, syncAll } from '../repo/sync'
import { Main } from './Main'
import { SignIn } from './SignIn'

type Phase = { kind: 'loading' } | { kind: 'signedOut'; error: string | null; reauth: boolean } | { kind: 'ready' }

/** Token due for renewal soon (or already expired). */
const needsRenewal = () => {
  const exp = tokenExpiresAt()
  return !exp || exp - Date.now() < 5 * 60_000
}

export function App() {
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' })

  useEffect(() => {
    void (async () => {
      const r = await handleRedirect()
      if (r.kind === 'error') {
        // A failed silent renewal needs one interactive sign-in; local notes stay.
        setPhase({ kind: 'signedOut', error: r.error === 'interaction_required' || r.error === 'login_required' || r.error === 'consent_required' ? null : r.error, reauth: !!getAccount() })
        return
      }
      if (!getAccount()) {
        setPhase({ kind: 'signedOut', error: null, reauth: false })
        return
      }
      if (!getAccessToken() && renewSilently()) return // redirecting
      setPhase({ kind: 'ready' })
      void syncAll()
    })().catch((e) => {
      recordError(e)
      setPhase({ kind: 'signedOut', error: String(e), reauth: false })
    })
  }, [])

  // Sync when coming back to the app; renew the ~1 h session first when needed.
  useEffect(() => {
    if (phase.kind !== 'ready') return
    const onVisible = () => {
      if (document.visibilityState !== 'visible') return
      if (needsRenewal() && renewSilently()) return
      void syncAll()
    }
    document.addEventListener('visibilitychange', onVisible)
    const id = setInterval(() => document.visibilityState === 'visible' && getAccessToken() && void syncAll(), 5 * 60_000)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      clearInterval(id)
    }
  }, [phase.kind])

  useEffect(() => {
    const onError = (e: ErrorEvent) => recordError(e.error ?? e.message)
    const onRejection = (e: PromiseRejectionEvent) => recordError(e.reason)
    window.addEventListener('error', onError)
    window.addEventListener('unhandledrejection', onRejection)
    return () => {
      window.removeEventListener('error', onError)
      window.removeEventListener('unhandledrejection', onRejection)
    }
  }, [])

  if (phase.kind === 'loading') return null
  if (phase.kind === 'signedOut') return <SignIn error={phase.error} reauth={phase.reauth} />
  return (
    <Main onSignOut={async () => {
      await signOut()
      await clearCalendarCache()
      setPhase({ kind: 'signedOut', error: null, reauth: false })
    }} />
  )
}
