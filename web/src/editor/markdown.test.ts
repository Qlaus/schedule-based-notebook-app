// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import { editorExtensions } from './extensions'

const roundTrip = (md: string) => {
  const editor = new Editor({ extensions: editorExtensions(), content: md, contentType: 'markdown' })
  const out = editor.getMarkdown()
  editor.destroy()
  return out
}

describe('rich text ↔ Markdown', () => {
  it('keeps headings, marks, lists and task lists', () => {
    const md = [
      '# Fizik',
      '',
      '## Newton',
      '',
      'Kuvvet **kalın** ve *italik* ve ~~çizili~~ metin.',
      '',
      '- madde 1',
      '- madde 2',
      '',
      '1. bir',
      '2. iki',
      '',
      '- [ ] ödev',
      '- [x] tekrar',
    ].join('\n')
    const out = roundTrip(md)
    expect(out).toContain('# Fizik')
    expect(out).toContain('## Newton')
    expect(out).toContain('**kalın**')
    expect(out).toContain('*italik*')
    expect(out).toContain('~~çizili~~')
    expect(out).toMatch(/- \[ \] ödev/)
    expect(out).toMatch(/- \[x\] tekrar/)
    expect(out).toMatch(/1\. bir/)
    // Stable: a second round trip changes nothing.
    expect(roundTrip(out)).toBe(out)
  })

  it('plain text from older notes survives', () => {
    const out = roundTrip('Satır bir\n\nSatır iki')
    expect(out).toContain('Satır bir')
    expect(out).toContain('Satır iki')
  })
})
