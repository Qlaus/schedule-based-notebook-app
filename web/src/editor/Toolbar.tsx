import type { Editor } from '@tiptap/core'
import { useEditorState } from '@tiptap/react'

const B = ({ on, label, title, onClick, disabled }: { on?: boolean; label: string; title: string; onClick: () => void; disabled?: boolean }) => (
  <button type="button" className={`tb ${on ? 'on' : ''}`} title={title} aria-label={title} aria-pressed={!!on}
    disabled={disabled} onMouseDown={(e) => e.preventDefault()} onClick={onClick}>{label}</button>
)

/** Formatting bar for the WYSIWYG editor (same on web and Android). */
export function Toolbar({ editor }: { editor: Editor }) {
  const s = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      bold: e.isActive('bold'), italic: e.isActive('italic'), strike: e.isActive('strike'),
      h1: e.isActive('heading', { level: 1 }), h2: e.isActive('heading', { level: 2 }), h3: e.isActive('heading', { level: 3 }),
      bullet: e.isActive('bulletList'), ordered: e.isActive('orderedList'), task: e.isActive('taskList'),
      canUndo: e.can().undo(), canRedo: e.can().redo(),
    }),
  })
  const c = () => editor.chain().focus()
  return (
    <div className="editor-toolbar" role="toolbar">
      <B label="B" title="Kalın" on={s.bold} onClick={() => c().toggleBold().run()} />
      <B label="İ" title="İtalik" on={s.italic} onClick={() => c().toggleItalic().run()} />
      <B label="S̶" title="Üstü çizili" on={s.strike} onClick={() => c().toggleStrike().run()} />
      <span className="tb-sep" />
      <B label="B1" title="Başlık 1" on={s.h1} onClick={() => c().toggleHeading({ level: 1 }).run()} />
      <B label="B2" title="Başlık 2" on={s.h2} onClick={() => c().toggleHeading({ level: 2 }).run()} />
      <B label="B3" title="Başlık 3" on={s.h3} onClick={() => c().toggleHeading({ level: 3 }).run()} />
      <span className="tb-sep" />
      <B label="•" title="Madde listesi" on={s.bullet} onClick={() => c().toggleBulletList().run()} />
      <B label="1." title="Numaralı liste" on={s.ordered} onClick={() => c().toggleOrderedList().run()} />
      <B label="☑" title="Yapılacaklar" on={s.task} onClick={() => c().toggleTaskList().run()} />
      <span className="tb-sep" />
      <B label="↶" title="Geri al" disabled={!s.canUndo} onClick={() => c().undo().run()} />
      <B label="↷" title="Yinele" disabled={!s.canRedo} onClick={() => c().redo().run()} />
    </div>
  )
}
