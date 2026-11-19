import { useEffect, useRef, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { t } from '../../i18n/tr'
import { db, type NoteBackup } from '../../data/db'
import { backupsFor, deleteBackup, deleteNote, moveToNotebook, restoreBackup, updateContent } from '../../repo/notes'
import { activeNotebooks } from '../../repo/notebooks'
import { pushChangesSoon } from '../../repo/sync'
import { dayShort, time } from '../format'
import { Icon } from '../common/Icons'
import { ConfirmDialog, Dialog } from '../common/Dialogs'
import { PaneHead } from '../common/PaneHead'

/** Plain-text note editor; autosaves 600 ms after typing stops and flushes on close. */
export function NoteEditor({ noteId, canGoBack, onBack, onOpenEvent, onDeleted }: {
  noteId: string; canGoBack: boolean; onBack: () => void
  onOpenEvent: (calendarId: string, eventId: string) => void; onDeleted: () => void
}) {
  const live = useLiveQuery(async () => {
    const note = await db.notes.get(noteId)
    const links = (await db.eventLinks.where('noteId').equals(noteId).toArray()).filter((l) => l.deletedAt == null)
    return { note, links, notebooks: await activeNotebooks(), backups: await backupsFor(noteId) }
  }, [noteId])

  const [title, setTitle] = useState<string | null>(null)
  const [body, setBody] = useState<string | null>(null)
  const [saved, setSaved] = useState(true)
  const [menu, setMenu] = useState(false)
  const [ask, setAsk] = useState<null | 'delete' | 'backups'>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const latest = useRef({ title: '', body: '', dirty: false })
  const bodyRef = useRef<HTMLTextAreaElement>(null)

  // Load text once per note (later live updates must not overwrite what the user types).
  useEffect(() => {
    setTitle(null)
    setBody(null)
  }, [noteId])
  useEffect(() => {
    if (live?.note && title === null) {
      setTitle(live.note.title)
      setBody(live.note.body)
      latest.current = { title: live.note.title, body: live.note.body, dirty: false }
    }
  }, [live, title])

  const flush = async () => {
    clearTimeout(timer.current)
    if (!latest.current.dirty) return
    latest.current.dirty = false
    await updateContent(noteId, latest.current.title, latest.current.body)
    setSaved(true)
    pushChangesSoon(4000)
  }
  useEffect(() => () => void flush(), [noteId]) // save pending edits when leaving

  // Auto-grow the body field.
  useEffect(() => {
    const el = bodyRef.current
    if (el) {
      el.style.height = 'auto'
      el.style.height = `${el.scrollHeight}px`
    }
  }, [body])

  const edit = (patch: { title?: string; body?: string }) => {
    if (patch.title !== undefined) setTitle(patch.title)
    if (patch.body !== undefined) setBody(patch.body)
    latest.current = { ...latest.current, ...patch, dirty: true }
    setSaved(false)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => void flush(), 600)
  }

  if (!live) return <PaneHead canGoBack={canGoBack} onBack={onBack} />
  if (!live.note || live.note.deletedAt != null) {
    return (<><PaneHead canGoBack={canGoBack} onBack={onBack} /><div className="empty-hint">{t.eventNotFound}</div></>)
  }
  const note = live.note
  const current = live.notebooks.find((n) => n.id === note.notebookId)

  const restore = async (b: NoteBackup) => {
    setAsk(null)
    await flush()
    await restoreBackup(b)
    setTitle(b.title)
    setBody(b.body)
    latest.current = { title: b.title, body: b.body, dirty: false }
    pushChangesSoon()
  }

  return (
    <>
      <PaneHead canGoBack={canGoBack} onBack={() => void flush().then(onBack)}>
        <span className="muted small" style={{ marginRight: 8 }}>
          {!saved ? t.noteSaving : note.dirty ? t.noteCloudPending : t.noteCloudSynced}
        </span>
        <button className="icon-btn error" aria-label={t.delete} onClick={() => setAsk('delete')}><Icon name="trash" /></button>
      </PaneHead>
      <div className="scroll section-pad" style={{ paddingTop: 0, paddingLeft: 28, paddingRight: 28 }}>
        <div className="row wrap" style={{ position: 'relative' }}>
          <button className="chip" onClick={() => setMenu(!menu)}>
            <span className="dot" style={{ background: current?.color ?? 'var(--line)' }} />
            {current?.title ?? t.noteNotebookNone}
          </button>
          {live.links.map((l) => (
            <button key={l.id} className={`chip ${l.eventDeleted ? 'danger' : ''}`}
              onClick={() => !l.eventDeleted && l.scope === 'INSTANCE' && onOpenEvent(l.calendarId, l.eventId)}>
              {(l.eventTitleSnapshot || '—') + ' · ' + dayShort(l.eventStartSnapshot)}
              {l.eventDeleted ? ` · ${t.noteEventDeleted}` : l.scope === 'SERIES' ? ` · ${t.noteSeries}` : ''}
            </button>
          ))}
          {menu && (
            <div className="menu" style={{ left: 0, right: 'auto', top: 44 }} onMouseLeave={() => setMenu(false)}>
              {live.notebooks.map((n) => (
                <button key={n.id} onClick={() => { setMenu(false); void moveToNotebook(noteId, n.id).then(() => pushChangesSoon()) }}>
                  <span className="dot" style={{ background: n.color ?? 'var(--accent)' }} /> {n.title}
                </button>
              ))}
              {current && <button onClick={() => { setMenu(false); void moveToNotebook(noteId, null).then(() => pushChangesSoon()) }}>{t.noteRemoveFromNotebook}</button>}
            </div>
          )}
        </div>
        {live.backups.length > 0 && (
          <button className="btn text error" style={{ alignSelf: 'flex-start' }} onClick={() => setAsk('backups')}>{t.backupsButton(live.backups.length)}</button>
        )}
        <input className="note-title" placeholder={t.noteTitleHint} value={title ?? ''} onChange={(e) => edit({ title: e.target.value })} />
        <textarea ref={bodyRef} className="note-body" placeholder={t.noteBodyHint} value={body ?? ''} onChange={(e) => edit({ body: e.target.value })} />
      </div>

      {ask === 'delete' && (
        <ConfirmDialog title={t.noteDeleteTitle} body={t.noteDeleteBody} confirm={t.delete} onClose={() => setAsk(null)}
          onConfirm={async () => {
            setAsk(null)
            clearTimeout(timer.current)
            latest.current.dirty = false
            await deleteNote(noteId)
            pushChangesSoon(500)
            onDeleted()
          }} />
      )}
      {ask === 'backups' && (
        <Dialog title={t.backupsTitle} onClose={() => setAsk(null)}>
          <div className="muted small">{t.backupsHint}</div>
          {live.backups.map((b) => (
            <div key={b.id} className="card flat">
              <div className="small" style={{ fontWeight: 600 }}>
                {(b.reason === 'conflict-remote' ? t.backupOther : t.backupThis) + ' · ' + dayShort(b.createdAt) + ' ' + time(b.createdAt)}
              </div>
              <div style={{ fontWeight: 600, marginTop: 4 }}>{b.title}</div>
              <div className="small" style={{ whiteSpace: 'pre-wrap', maxHeight: 120, overflow: 'hidden' }}>{b.body}</div>
              <div className="row">
                <button className="btn text" onClick={() => void restore(b)}>{t.backupRestore}</button>
                <button className="btn text danger" onClick={() => void deleteBackup(b.id)}>{t.backupDelete}</button>
              </div>
            </div>
          ))}
        </Dialog>
      )}
    </>
  )
}
