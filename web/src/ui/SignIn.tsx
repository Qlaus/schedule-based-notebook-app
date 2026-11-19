import { t } from '../i18n/tr'
import { isClientConfigured } from '../auth/config'
import { startSignIn } from '../auth/session'

export function SignIn({ error, reauth }: { error: string | null; reauth: boolean }) {
  return (
    <div style={{ minHeight: '100%', display: 'grid', placeItems: 'center', padding: 24 }}>
      <div style={{ maxWidth: 440, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <img src={`${import.meta.env.BASE_URL}icon-192.png`} alt="" width={72} height={72} style={{ borderRadius: 18 }} />
        <h1 style={{ fontSize: 36 }}>{t.appName}</h1>
        <div className="muted" style={{ fontSize: 17 }}>{reauth ? t.signinReauth : t.signinSubtitle}</div>
        <button className="btn primary block" style={{ minHeight: 54, fontSize: 17 }} disabled={!isClientConfigured()} onClick={() => startSignIn()}>
          {t.signinButton}
        </button>
        {!isClientConfigured() && <div className="error">{t.signinClientMissing}</div>}
        {error && <div className="error">{t.signinFailed(error)}</div>}
        <div className="muted small">{t.signinIosHint}</div>
      </div>
    </div>
  )
}
