import { useSyncExternalStore } from 'react'
import { db } from '../data/db'
import { dayShort } from '../ui/format'
import { exportMarkdown, safeFileName, type ExportNote } from './exportMarkdown'

/** Collects notes for export (in the order given). */
export async function exportNotesFor(noteIds: string[]): Promise<ExportNote[]> {
  const out: ExportNote[] = []
  for (const id of noteIds) {
    const n = await db.notes.get(id)
    if (!n || n.deletedAt != null) continue
    const link = (await db.eventLinks.where('noteId').equals(id).toArray()).find((l) => l.deletedAt == null)
    const meta = link ? `${link.eventTitleSnapshot} · ${dayShort(link.eventStartSnapshot)}` : dayShort(n.createdAt)
    out.push({ title: n.title, markdown: n.body, ink: n.ink ?? null, meta })
  }
  return out
}

/** Shares the Markdown file (iPhone share sheet) or downloads it. */
export async function shareMarkdown(name: string, notes: ExportNote[]) {
  const file = new File([exportMarkdown(notes)], `${safeFileName(name)}.md`, { type: 'text/markdown' })
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
  if (nav.canShare?.({ files: [file] })) {
    try {
      await nav.share({ files: [file], title: name })
      return
    } catch (e) {
      if ((e as Error).name === 'AbortError') return
    }
  }
  const url = URL.createObjectURL(file)
  const a = document.createElement('a')
  a.href = url
  a.download = file.name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

// --- printing (PDF via the browser's print dialog) ---

let job: ExportNote[] | null = null
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())
export const usePrintJob = () =>
  useSyncExternalStore((l) => (listeners.add(l), () => listeners.delete(l)), () => job)

/** Renders the notes into the print view, then opens the print dialog ("Save as PDF"). */
export function printNotes(notes: ExportNote[]) {
  job = notes
  emit()
  const done = () => {
    job = null
    emit()
    window.removeEventListener('afterprint', done)
  }
  window.addEventListener('afterprint', done)
  // Let React render and fonts settle before printing.
  setTimeout(() => void document.fonts.ready.then(() => window.print()), 400)
}
