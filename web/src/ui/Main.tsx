import { useEffect } from 'react'
import { t } from '../i18n/tr'
import { Icon, type IconName } from './common/Icons'
import { useWide } from './hooks'
import { nav, useNav, type Detail, type Section } from './store'
import { CalendarPane } from './calendar/CalendarPane'
import { EventDetail } from './event/EventDetail'
import { EventEditor } from './event/EventEditor'
import { NoteEditor } from './note/NoteEditor'
import { NotebooksPane } from './notebooks/NotebooksPane'
import { SearchPane } from './search/SearchPane'
import { SettingsPane } from './settings/SettingsPane'

const ITEMS: [Section, string, IconName][] = [
  ['calendar', t.navCalendar, 'calendar'],
  ['notebooks', t.navNotebooks, 'book'],
  ['search', t.navSearch, 'search'],
  ['settings', t.navSettings, 'settings'],
]

/** Wide: rail | section | detail. Narrow (phone): section + tab bar, detail opens full screen. */
export function Main({ onSignOut }: { onSignOut: () => void }) {
  const { section, stack, noteFullscreen } = useNav()
  const wide = useWide()
  const detail = stack[stack.length - 1]
  const fullscreenNote = wide && noteFullscreen && detail?.kind === 'note'

  // Browser / Android back: keep one sentinel history entry and turn "back" into in-app navigation.
  useEffect(() => {
    history.pushState({ calnotes: true }, '')
    const onPop = () => {
      if (nav.handleBack()) history.pushState({ calnotes: true }, '')
      else history.back()
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  const sectionPane = (
    <>
      {section === 'calendar' && <CalendarPane />}
      {section === 'notebooks' && <NotebooksPane />}
      {section === 'search' && <SearchPane />}
      {section === 'settings' && <SettingsPane onSignOut={onSignOut} />}
    </>
  )
  const navButtons = ITEMS.map(([s, label, icon]) => (
    <button key={s} className={section === s ? 'active' : ''} onClick={() => nav.section(s)}>
      <span className="icon-wrap"><Icon name={icon} /></span>{label}
    </button>
  ))

  return (
    <div className={`app ${wide ? 'wide' : 'narrow'} ${fullscreenNote ? 'note-fullscreen' : ''}`}>
      {wide && !fullscreenNote && <nav className="rail">{navButtons}</nav>}
      {!fullscreenNote && <main className="pane section">{sectionPane}</main>}
      {(wide || detail) && (
        <aside className="pane detail">
          {detail ? <DetailPane detail={detail} depth={stack.length} closeIsBack={!wide} wide={wide} fullscreen={fullscreenNote} /> : <div className="empty-hint">{t.selectEventHint}</div>}
        </aside>
      )}
      {!wide && <nav className="tabbar">{navButtons}</nav>}
    </div>
  )
}

function DetailPane({ detail, depth, closeIsBack, wide, fullscreen }: { detail: Detail; depth: number; closeIsBack: boolean; wide: boolean; fullscreen: boolean }) {
  const canGoBack = depth > 1 || closeIsBack
  switch (detail.kind) {
    case 'event':
      return (
        <EventDetail key={detail.calendarId + detail.eventId} calendarId={detail.calendarId} eventId={detail.eventId} canGoBack={canGoBack}
          onBack={nav.back} onEdit={() => nav.push({ kind: 'editEvent', calendarId: detail.calendarId, eventId: detail.eventId })}
          onOpenNote={(id) => nav.push({ kind: 'note', noteId: id })} onDeleted={nav.back} />
      )
    case 'note':
      return (
        <NoteEditor key={detail.noteId} noteId={detail.noteId} canGoBack={canGoBack} onBack={nav.back}
          fullscreen={fullscreen} onToggleFullscreen={wide ? () => nav.setNoteFullscreen(!fullscreen) : undefined}
          onOpenEvent={(c, e) => nav.push({ kind: 'event', calendarId: c, eventId: e })} onDeleted={nav.back} />
      )
    case 'editEvent':
      return (
        <EventEditor key={`${detail.calendarId}/${detail.eventId}/${detail.presetDate}/${detail.presetTime}`} {...detail}
          canGoBack={canGoBack} onBack={nav.back} onSaved={nav.back} />
      )
  }
}
