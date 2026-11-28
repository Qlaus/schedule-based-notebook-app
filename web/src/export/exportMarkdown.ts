import { hasInk, inkBottom, parseInk, PAGE_WIDTH, strokeOpacity, strokePath, type InkDoc } from '../editor/ink'

/** One note to export. `meta` is a short line like "Fizik · 17 Kas" (event and date). */
export interface ExportNote { title: string; markdown: string; ink: string | null; meta: string }

/** Standalone SVG of a handwriting layer (used as an embedded image in Markdown exports). */
export function inkSvg(doc: InkDoc): string {
  const h = Math.ceil(inkBottom(doc) + 10)
  const paths = doc.strokes
    .map((s) => `<path d="${strokePath(s)}" fill="${s.color}" fill-opacity="${strokeOpacity(s)}"/>`)
    .join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${PAGE_WIDTH}" height="${h}" viewBox="0 0 ${PAGE_WIDTH} ${h}">${paths}</svg>`
}

const base64 = (s: string) => btoa(String.fromCharCode(...new TextEncoder().encode(s)))

/** Markdown export: each note as a section; handwriting embedded as an SVG image. */
export function exportMarkdown(notes: ExportNote[]): string {
  return notes
    .map((n) => {
      const parts = [`# ${n.title || 'Not'}`]
      if (n.meta) parts.push(`*${n.meta}*`)
      if (n.markdown.trim()) parts.push(n.markdown.trim())
      const ink = parseInk(n.ink)
      if (hasInk(ink)) parts.push(`![El yazısı](data:image/svg+xml;base64,${base64(inkSvg(ink!))})`)
      return parts.join('\n\n')
    })
    .join('\n\n---\n\n') + '\n'
}

export function safeFileName(name: string): string {
  return (name.trim() || 'not').replace(/[\\/:*?"<>|]+/g, '-').slice(0, 80)
}
