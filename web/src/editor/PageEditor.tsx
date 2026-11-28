import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { EditorContent, useEditor } from '@tiptap/react'
import { editorExtensions } from './extensions'
import { InkLayer } from './InkLayer'
import { Toolbar } from './Toolbar'
import { hasInk, inkBottom, PAGE_WIDTH, type InkDoc } from './ink'
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-ext-400.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-ext-600.css'
import '@fontsource/inter/latin-700.css'
import '@fontsource/inter/latin-ext-700.css'
import './page.css'

export interface PageContent { title: string; markdown: string }

/**
 * The note page: title + WYSIWYG rich text + handwriting layer. With `pageMode` the page has a fixed
 * width (PAGE_WIDTH units, bundled Inter font) and is zoomed to fit, so text wraps exactly like on the
 * tablet and handwriting stays on the right lines. Without it (text-only notes on narrow screens)
 * the text simply reflows.
 */
export function PageEditor({ noteKey, initial, ink, pageMode, editable = true, toolbar = true, titlePlaceholder, bodyPlaceholder, onChange, onHeight, fixedZoom, extraSpace = 0 }: {
  noteKey: string
  initial: PageContent
  ink: InkDoc | null
  pageMode: boolean
  editable?: boolean
  toolbar?: boolean
  titlePlaceholder: string
  bodyPlaceholder: string
  onChange: (c: PageContent) => void
  onHeight?: (pageUnits: number) => void
  /** Use this zoom instead of fitting the container (Android host: the WebView viewport already fits 640). */
  fixedZoom?: number
  /** Extra empty page space below the content (room to write while in pen mode). */
  extraSpace?: number
}) {
  const [title, setTitle] = useState(initial.title)
  const titleRef = useRef(initial.title)
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  const outer = useRef<HTMLDivElement>(null)
  const page = useRef<HTMLDivElement>(null)
  const [zoom, setZoom] = useState(1)
  const [pageHeight, setPageHeight] = useState(0)

  const editor = useEditor({
    extensions: editorExtensions(bodyPlaceholder),
    content: initial.markdown,
    contentType: 'markdown',
    editable,
    onUpdate: ({ editor: e }) => onChangeRef.current({ title: titleRef.current, markdown: e.getMarkdown() }),
  }, [noteKey])

  useEffect(() => {
    setTitle(initial.title)
    titleRef.current = initial.title
  }, [noteKey]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => editor?.setEditable(editable), [editor, editable])

  // Fit the fixed-width page into the available width.
  useLayoutEffect(() => {
    if (!pageMode || !outer.current) return
    if (fixedZoom) {
      setZoom(fixedZoom)
      return
    }
    const el = outer.current
    const ro = new ResizeObserver(() => setZoom(Math.min(1.6, el.clientWidth / PAGE_WIDTH)))
    ro.observe(el)
    return () => ro.disconnect()
  }, [pageMode, fixedZoom])

  // Track the page height (content or lowest stroke, whichever is lower on the page).
  useLayoutEffect(() => {
    if (!page.current) return
    const el = page.current
    const ro = new ResizeObserver(() => {
      const h = Math.max(el.scrollHeight, inkBottom(ink) + 40)
      setPageHeight(h)
      onHeight?.(h)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [ink, onHeight])

  const editTitle = (v: string) => {
    setTitle(v)
    titleRef.current = v
    onChangeRef.current({ title: v, markdown: editor?.getMarkdown() ?? initial.markdown })
  }

  return (
    <div className="page-outer" ref={outer}>
      {toolbar && editable && editor && <Toolbar editor={editor} />}
      <div className={`page ${pageMode ? 'fixed' : 'fluid'}`} ref={page}
        style={pageMode ? { zoom, minHeight: inkBottom(ink) + 40 + extraSpace } : undefined}>
        <textarea className="page-title" rows={1} value={title} placeholder={titlePlaceholder} readOnly={!editable}
          onChange={(e) => editTitle(e.target.value.replace(/\n/g, ' '))} />
        <EditorContent editor={editor} className="page-body" />
        {pageMode && hasInk(ink) && <InkLayer ink={ink!} height={Math.max(pageHeight, inkBottom(ink) + 40)} />}
      </div>
    </div>
  )
}
