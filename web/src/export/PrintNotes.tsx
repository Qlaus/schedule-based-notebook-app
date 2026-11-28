import { PageEditor } from '../editor/PageEditor'
import { parseInk } from '../editor/ink'
import type { ExportNote } from './exportMarkdown'

/** Read-only pages for printing / "Save as PDF": the same page layout, so handwriting stays in place. */
export function PrintNotes({ notes }: { notes: ExportNote[] }) {
  return (
    <div className="print-root">
      {notes.map((n, i) => (
        <section key={i} className="print-note">
          {n.meta && <div className="print-meta">{n.meta}</div>}
          <PageEditor noteKey={`print-${i}`} initial={{ title: n.title, markdown: n.markdown }} ink={parseInk(n.ink)}
            pageMode fixedZoom={1} editable={false} toolbar={false} titlePlaceholder="" bodyPlaceholder="" onChange={() => {}} />
        </section>
      ))}
    </div>
  )
}
