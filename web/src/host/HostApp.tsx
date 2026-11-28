import { useEffect, useRef, useState } from 'react'
import { PageEditor } from '../editor/PageEditor'
import { parseInk, type InkDoc } from '../editor/ink'
import { PrintNotes } from '../export/PrintNotes'
import { native, type LoadArgs, type PrintNote } from './bridge'

/** Editor page hosted by the Android app: fixed 640-unit page; handwriting drawn natively and rendered here. */
export function HostApp() {
  const [doc, setDoc] = useState<LoadArgs | null>(null)
  const [ink, setInk] = useState<InkDoc | null>(null)
  const [editable, setEditable] = useState(true)
  const [extra, setExtra] = useState(0)
  const [print, setPrint] = useState<PrintNote[] | null>(null)
  const pending = useRef<ReturnType<typeof setTimeout>>(undefined)

  useEffect(() => {
    window.calnotes = {
      load: (args) => {
        setPrint(null)
        setDoc(args)
        setInk(parseInk(args.ink))
        setEditable(args.editable)
      },
      setInk: (json) => setInk(parseInk(json)),
      setEditable,
      setExtraSpace: setExtra,
      renderPrint: (notes) => setPrint(notes),
    }
    native()?.onReady()
  }, [])

  if (print) return <PrintNotes notes={print} />
  if (!doc) return null
  return (
    <PageEditor
      noteKey={doc.noteKey}
      initial={{ title: doc.title, markdown: doc.markdown }}
      ink={ink}
      pageMode
      fixedZoom={1}
      editable={editable}
      extraSpace={extra}
      titlePlaceholder={doc.titlePlaceholder}
      bodyPlaceholder={doc.bodyPlaceholder}
      onHeight={(h) => native()?.onHeight(h)}
      onChange={(c) => {
        // Small debounce: Android autosaves anyway, this just avoids a bridge call per keystroke.
        clearTimeout(pending.current)
        pending.current = setTimeout(() => native()?.onChange(c.title, c.markdown), 250)
      }}
    />
  )
}
