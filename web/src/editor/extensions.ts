import StarterKit from '@tiptap/starter-kit'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import { Markdown } from '@tiptap/markdown'
import Placeholder from '@tiptap/extension-placeholder'

/**
 * The rich-text feature set shared by the web app and the Android app (which hosts this editor in a
 * WebView). Everything here round-trips through Markdown (GFM), the note body format.
 */
export function editorExtensions(placeholder = '') {
  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      codeBlock: false,
      code: false,
      blockquote: false,
      horizontalRule: false,
      link: false,
    }),
    TaskList,
    TaskItem.configure({ nested: true }),
    Placeholder.configure({ placeholder }),
    Markdown.configure({ indentation: { style: 'space', size: 2 } }),
  ]
}
