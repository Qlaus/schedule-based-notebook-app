import { useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { t } from '../../i18n/tr'
import { plainPreview } from '../../core/preview'
import { db } from '../../data/db'
import { createStandalone, notesIn, type NoteWithLinks } from '../../repo/notes'
import { createNotebook, deleteNotebook, notebooksWithCounts, updateNotebook } from '../../repo/notebooks'
import { pushChangesSoon } from '../../repo/sync'
import { dayShort } from '../format'
import { Icon } from '../common/Icons'
import { ConfirmDialog } from '../common/Dialogs'
import { nav, useNav } from '../store'
import { NotebookEditDialog } from './NotebookDialogs'
import { exportNotesFor, printNotes, shareMarkdown } from '../../export/exporter'

export function NotebooksPane() {
  const { notebookSel } = useNav()
  return notebookSel.kind === 'all' ? <NotebookList /> : <NotebookNotes notebookId={notebookSel.kind === 'one' ? notebookSel.id : null} />
}

function NotebookList() {
  const notebooks = useLiveQuery(notebooksWithCounts, []) ?? []
  const unfiled = useLiveQuery(() => db.notes.filter((n) => n.deletedAt == null && n.notebookId == null).count(), []) ?? 0
  const [creating, setCreating] = useState(false)
  return (
    <div className="scroll section-pad">
      <div className="row"><h1 className="grow">{t.notebooksTitle}</h1>
        <button className="icon-btn" aria-label={t.notebookNew} onClick={() => setCreating(true)}><Icon name="plus" /></button>
      </div>
      {notebooks.length === 0 && <div className="muted">{t.notebooksEmpty}</div>}
      {notebooks.map(({ notebook, noteCount }) => (
        <div key={notebook.id} className="card click row" style={{ padding: 0, overflow: 'hidden', minHeight: 76 }}
          onClick={() => nav.notebook({ kind: 'one', id: notebook.id })}>
          <div style={{ width: 8, alignSelf: 'stretch', background: notebook.color ?? 'var(--accent)' }} />
          <h3 className="grow ellipsis" style={{ padding: '0 10px' }}>{notebook.title}</h3>
          <span className="muted" style={{ paddingRight: 20 }}>{t.noteCount(noteCount)}</span>
        </div>
      ))}
      <div className="card flat click row" onClick={() => nav.notebook({ kind: 'unfiled' })}>
        <span className="muted"><Icon name="inbox" /></span>
        <span className="grow" style={{ fontWeight: 600 }}>{t.unfiled}</span>
        <span className="muted">{t.noteCount(unfiled)}</span>
      </div>
      {creating && (
        <NotebookEditDialog title={t.notebookNew} onClose={() => setCreating(false)}
          onSave={async (name, color) => {
            setCreating(false)
            const id = await createNotebook(name, color)
            pushChangesSoon()
            nav.notebook({ kind: 'one', id })
          }} />
      )}
    </div>
  )
}

function NotebookNotes({ notebookId }: { notebookId: string | null }) {
  const notes = useLiveQuery(() => notesIn(notebookId), [notebookId]) ?? []
  const notebook = useLiveQuery(async () => (notebookId ? await db.notebooks.get(notebookId) : undefined), [notebookId])
  const [menu, setMenu] = useState(false)
  const [ask, setAsk] = useState<null | 'edit' | 'delete'>(null)
  const title = notebookId ? notebook?.title ?? '' : t.unfiled
  const back = () => nav.notebook({ kind: 'all' })

  return (
    <>
      <div className="pane-head" style={{ position: 'relative' }}>
        <button className="icon-btn" aria-label={t.back} onClick={back}><Icon name="back" /></button>
        {notebook && <span className="dot" style={{ background: notebook.color ?? 'var(--accent)', width: 14, height: 14 }} />}
        <h2 className="grow ellipsis" style={{ marginLeft: 8 }}>{title}</h2>
        <button className="icon-btn" aria-label={t.more} onClick={() => setMenu(!menu)}><Icon name="more" /></button>
        {menu && (
          <div className="menu" onMouseLeave={() => setMenu(false)}>
            {/* Oldest first reads like a notebook. */}
            <button onClick={async () => { setMenu(false); await shareMarkdown(title, await exportNotesFor([...notes].reverse().map((n) => n.note.id))) }}>{t.exportNotebookMarkdown}</button>
            <button onClick={async () => { setMenu(false); printNotes(await exportNotesFor([...notes].reverse().map((n) => n.note.id))) }}>{t.exportNotebookPdf}</button>
            {notebook && <button onClick={() => { setMenu(false); setAsk('edit') }}>{t.notebookRename}</button>}
            {notebook && <button className="error" onClick={() => { setMenu(false); setAsk('delete') }}>{t.notebookDelete}</button>}
          </div>
        )}
      </div>
      <div className="scroll section-pad" style={{ paddingTop: 0 }}>
        {notes.length === 0 && <div className="muted">{t.notebookEmpty}</div>}
        {notes.map((n) => <NoteRow key={n.note.id} n={n} onClick={() => nav.show({ kind: 'note', noteId: n.note.id })} />)}
      </div>
      <button className="fab" onClick={async () => {
        const id = await createStandalone(notebookId)
        nav.show({ kind: 'note', noteId: id })
      }}><Icon name="plus" /> {t.newNote}</button>

      {ask === 'edit' && notebook && (
        <NotebookEditDialog title={t.notebookRename} initialName={notebook.title} initialColor={notebook.color} onClose={() => setAsk(null)}
          onSave={(name, color) => { setAsk(null); void updateNotebook(notebook.id, name, color).then(() => pushChangesSoon()) }} />
      )}
      {ask === 'delete' && notebook && (
        <ConfirmDialog title={t.notebookDelete} body={t.notebookDeleteBody} confirm={t.delete} onClose={() => setAsk(null)}
          onConfirm={() => { setAsk(null); void deleteNotebook(notebook.id).then(() => pushChangesSoon()); back() }} />
      )}
    </>
  )
}

export function NoteRow({ n, onClick }: { n: NoteWithLinks; onClick: () => void }) {
  const link = n.links[0]
  const meta = link
    ? `${link.eventTitleSnapshot} · ${dayShort(link.eventStartSnapshot)}${link.eventDeleted ? ' · ' + t.noteEventDeleted : ''}`
    : `${t.standaloneNote} · ${dayShort(n.note.createdAt)}`
  return (
    <div className="card flat click" onClick={onClick}>
      <div style={{ fontWeight: 600 }} className="ellipsis">{n.note.title || t.noTitle}</div>
      <div className="muted small ellipsis">{meta}</div>
      {n.note.body && <div className="muted small" style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{plainPreview(n.note.body)}</div>}
    </div>
  )
}
