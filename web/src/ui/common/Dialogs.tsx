import { useEffect, type ReactNode } from 'react'
import { t } from '../../i18n/tr'

export function Dialog({ title, children, onClose, actions }: { title: string; children?: ReactNode; onClose: () => void; actions?: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="backdrop" onClick={onClose}>
      <div className="dialog" role="dialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h3>{title}</h3>
        {children}
        <div className="dialog-actions">
          {actions}
          <button className="btn text" onClick={onClose}>{t.cancel}</button>
        </div>
      </div>
    </div>
  )
}

/** "Only this occurrence" vs "whole series" for recurring events. */
export function ScopeDialog({ title, hint, onInstance, onSeries, onClose }: {
  title: string; hint?: string; onInstance: () => void; onSeries: () => void; onClose: () => void
}) {
  return (
    <Dialog title={title} onClose={onClose}>
      <button className="btn primary block" onClick={onInstance}>{t.scopeInstance}</button>
      <button className="btn tonal block" onClick={onSeries}>{t.scopeSeries}</button>
      {hint && <div className="muted small">{hint}</div>}
    </Dialog>
  )
}

export function ConfirmDialog({ title, body, confirm, onConfirm, onClose }: {
  title: string; body: string; confirm: string; onConfirm: () => void; onClose: () => void
}) {
  return (
    <Dialog title={title} onClose={onClose} actions={<button className="btn text danger" onClick={onConfirm}>{confirm}</button>}>
      <div>{body}</div>
    </Dialog>
  )
}
