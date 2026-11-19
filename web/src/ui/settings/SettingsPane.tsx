import { useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { t } from '../../i18n/tr'
import { getAccount } from '../../auth/session'
import { setSettings } from '../../repo/settings'
import { lastError, pendingNoteChanges, pushChangesSoon, syncNotes } from '../../repo/sync'
import { useSettings, useSyncState } from '../hooks'
import { driveLabel } from '../statusLabels'

export function SettingsPane({ onSignOut }: { onSignOut: () => void }) {
  const settings = useSettings()
  const sync = useSyncState()
  const pending = useLiveQuery(pendingNoteChanges, []) ?? 0
  const [copied, setCopied] = useState(false)
  const error = lastError()
  const toggle = (patch: Parameters<typeof setSettings>[0]) => {
    setSettings(patch)
    pushChangesSoon(300) // add/remove markers on Google right away
  }
  return (
    <div className="scroll section-pad">
      <h1>{t.settingsTitle}</h1>

      <div className="muted small" style={{ fontWeight: 600 }}>{t.driveSection}</div>
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div className="muted small">{t.driveHint}</div>
        <div className={sync.drive.kind === 'failed' || sync.drive.kind === 'noPermission' ? 'error' : ''}>{driveLabel(sync.drive, pending)}</div>
        <button className="btn outline" style={{ alignSelf: 'flex-start' }} disabled={sync.drive.kind === 'running'} onClick={() => void syncNotes()}>{t.driveSyncNow}</button>
      </div>

      <div className="muted small" style={{ fontWeight: 600 }}>{t.settingsCalendar}</div>
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Toggle title={t.titleMarker} hint={t.titleMarkerHint} checked={settings.titleMarker} onChange={(v) => toggle({ titleMarker: v })} />
        <hr style={{ border: 0, borderTop: '0.5px solid var(--line)', margin: 0 }} />
        <Toggle title={t.descriptionMarker} hint={t.descriptionMarkerHint} checked={settings.descriptionMarker} onChange={(v) => toggle({ descriptionMarker: v })} />
      </div>

      <div className="muted small" style={{ fontWeight: 600 }}>{t.account}</div>
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div>{getAccount()?.email}</div>
        <button className="btn outline" style={{ alignSelf: 'flex-start' }} onClick={onSignOut}>{t.signOut}</button>
      </div>

      <div className="muted small">{t.installHint}</div>
      <div className="muted small">{t.version(__APP_VERSION__)}</div>
      {error && (
        <div className="card flat" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div className="small" style={{ fontWeight: 600 }}>{t.lastError}</div>
          <pre className="report">{error}</pre>
          <button className="btn text" style={{ alignSelf: 'flex-start' }}
            onClick={() => void navigator.clipboard?.writeText(error).then(() => setCopied(true))}>{copied ? t.copied : t.copy}</button>
        </div>
      )}
    </div>
  )
}

function Toggle({ title, hint, checked, onChange }: { title: string; hint: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="row" style={{ alignItems: 'flex-start' }}>
      <div className="grow"><div>{title}</div><div className="muted small">{hint}</div></div>
      <span className="switch"><input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} /><span /></span>
    </label>
  )
}
