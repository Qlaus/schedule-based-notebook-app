import { useSyncExternalStore } from 'react'
import { today } from '../repo/time'

/** App navigation state; saved in sessionStorage so a session-renewal redirect returns to the same view. */
export type Section = 'calendar' | 'notebooks' | 'search' | 'settings'
export type Mode = 'day' | 'week' | 'month' | 'agenda'
export type NotebookSel = { kind: 'all' } | { kind: 'unfiled' } | { kind: 'one'; id: string }

export type Detail =
  | { kind: 'event'; calendarId: string; eventId: string }
  | { kind: 'note'; noteId: string }
  | { kind: 'editEvent'; calendarId?: string; eventId?: string; presetDate?: string; presetTime?: string }

export interface NavState {
  section: Section
  mode: Mode
  anchor: string
  notebookSel: NotebookSel
  stack: Detail[]
}

const KEY = 'calnotes.nav'
const initial: NavState = { section: 'calendar', mode: window.innerWidth < 760 ? 'day' : 'week', anchor: today(), notebookSel: { kind: 'all' }, stack: [] }

let state: NavState = (() => {
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null')
    return saved ? { ...initial, ...saved } : initial
  } catch {
    return initial
  }
})()

const listeners = new Set<() => void>()
function set(patch: Partial<NavState>) {
  state = { ...state, ...patch }
  try {
    sessionStorage.setItem(KEY, JSON.stringify(state))
  } catch {
    // ignore
  }
  listeners.forEach((l) => l())
}

export const nav = {
  get: () => state,
  subscribe: (l: () => void) => {
    listeners.add(l)
    return () => {
      listeners.delete(l)
    }
  },
  section: (section: Section) => set({ section }),
  mode: (mode: Mode) => set({ mode }),
  anchor: (anchor: string) => set({ anchor }),
  openDay: (anchor: string) => set({ anchor, mode: 'day' }),
  notebook: (notebookSel: NotebookSel) => set({ notebookSel }),
  show: (d: Detail) => set({ stack: [d] }),
  push: (d: Detail) => set({ stack: [...state.stack, d] }),
  back: () => set({ stack: state.stack.slice(0, -1) }),
  /** Back gesture: detail first, then notebook list, then calendar. Returns false when nothing to do. */
  handleBack: (): boolean => {
    if (state.stack.length) set({ stack: state.stack.slice(0, -1) })
    else if (state.section === 'notebooks' && state.notebookSel.kind !== 'all') set({ notebookSel: { kind: 'all' } })
    else if (state.section !== 'calendar') set({ section: 'calendar' })
    else return false
    return true
  },
}

export const useNav = () => useSyncExternalStore(nav.subscribe, nav.get)
