import { useState } from 'react'
import { t } from '../../i18n/tr'
import type { Notebook } from '../../data/db'
import { NOTEBOOK_PALETTE } from '../../repo/notebooks'
import { Dialog } from '../common/Dialogs'

export function NotebookEditDialog({ title, initialName = '', initialColor = NOTEBOOK_PALETTE[0], onSave, onClose }: {
  title: string; initialName?: string; initialColor?: string | null; onSave: (name: string, color: string | null) => void; onClose: () => void
}) {
  const [name, setName] = useState(initialName)
  const [color, setColor] = useState<string | null>(initialColor)
  return (
    <Dialog title={title} onClose={onClose}
      actions={<button className="btn text" disabled={!name.trim()} onClick={() => onSave(name, color)}>{t.save}</button>}>
      <input className="input" autoFocus placeholder={t.notebookName} value={name} onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && name.trim() && onSave(name, color)} />
      <div className="row wrap">
        {NOTEBOOK_PALETTE.map((c) => (
          <button key={c} aria-label={c} className={`swatch ${c === color ? 'sel' : ''}`} style={{ background: c }} onClick={() => setColor(c)} />
        ))}
      </div>
    </Dialog>
  )
}

export function NotebookPickerDialog({ notebooks, onPick, onCreate, onClose }: {
  notebooks: Notebook[]; onPick: (id: string) => void; onCreate: (title: string) => void; onClose: () => void
}) {
  const [name, setName] = useState('')
  return (
    <Dialog title={t.pickNotebook} onClose={onClose}>
      <div>
        {notebooks.map((n) => (
          <div key={n.id} className="list-row" onClick={() => onPick(n.id)}>
            <span className="dot" style={{ background: n.color ?? 'var(--accent)' }} />
            <span>{n.title}</span>
          </div>
        ))}
      </div>
      <div className="row">
        <input className="input grow" placeholder={t.notebookNew} value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn tonal" disabled={!name.trim()} onClick={() => onCreate(name)}>{t.notebookCreate}</button>
      </div>
    </Dialog>
  )
}
