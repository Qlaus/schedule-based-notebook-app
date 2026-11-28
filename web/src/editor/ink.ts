/**
 * Ink (handwriting) document, format v2 (docs/note-format.md). Coordinates are page units on a page
 * PAGE_WIDTH wide; y grows downwards from the top of the page. Shared with the Android app.
 */
export const PAGE_WIDTH = 640
export const INK_FORMAT_VERSION = 2

export type InkTool = 'pen' | 'highlighter'

export interface InkStroke {
  id: string
  tool: InkTool
  /** #RRGGBB */
  color: string
  /** Nominal width in page units (scaled by pressure for pens). */
  size: number
  /** Flat list of x, y, pressure (0..1) triples. */
  points: number[]
}

export interface InkDoc {
  formatVersion: number
  pageWidth: number
  strokes: InkStroke[]
}

export const emptyInk = (): InkDoc => ({ formatVersion: INK_FORMAT_VERSION, pageWidth: PAGE_WIDTH, strokes: [] })

export function parseInk(text: string | null | undefined): InkDoc | null {
  if (!text) return null
  try {
    const o = JSON.parse(text) as Partial<InkDoc>
    if (typeof o.formatVersion !== 'number' || o.formatVersion > INK_FORMAT_VERSION || !Array.isArray(o.strokes)) return null
    return {
      formatVersion: o.formatVersion,
      pageWidth: typeof o.pageWidth === 'number' ? o.pageWidth : PAGE_WIDTH,
      strokes: o.strokes.filter((s) => s && Array.isArray(s.points) && s.points.length >= 3),
    }
  } catch {
    return null
  }
}

export const hasInk = (doc: InkDoc | null | undefined) => !!doc && doc.strokes.length > 0

/** Same key order as the Kotlin writer (kotlinx.serialization, compact). */
export function encodeInk(doc: InkDoc): string {
  return JSON.stringify({
    formatVersion: doc.formatVersion,
    pageWidth: doc.pageWidth,
    strokes: doc.strokes.map((s) => ({ id: s.id, tool: s.tool, color: s.color, size: s.size, points: s.points })),
  })
}

/** Bottom of the lowest stroke (for sizing the page). */
export function inkBottom(doc: InkDoc | null | undefined): number {
  let max = 0
  for (const s of doc?.strokes ?? []) for (let i = 1; i < s.points.length; i += 3) max = Math.max(max, s.points[i] + s.size)
  return max
}

/**
 * Filled outline of a pressure-sensitive stroke: offset the centre line by half the width on both
 * sides and close the shape with round caps. Good enough to show tablet handwriting faithfully.
 */
export function strokePath(s: InkStroke): string {
  const p = s.points
  const n = p.length / 3
  if (n === 1) {
    const r = Math.max(0.5, s.size * widthFactor(s, p[2]) / 2)
    return `M${p[0] - r},${p[1]}a${r},${r} 0 1,0 ${2 * r},0a${r},${r} 0 1,0 ${-2 * r},0`
  }
  const left: string[] = []
  const right: string[] = []
  for (let i = 0; i < n; i++) {
    const x = p[i * 3], y = p[i * 3 + 1]
    const j0 = Math.max(0, i - 1), j1 = Math.min(n - 1, i + 1)
    let dx = p[j1 * 3] - p[j0 * 3], dy = p[j1 * 3 + 1] - p[j0 * 3 + 1]
    const len = Math.hypot(dx, dy) || 1
    dx /= len
    dy /= len
    const r = Math.max(0.4, (s.size * widthFactor(s, p[i * 3 + 2])) / 2)
    left.push(`${(x - dy * r).toFixed(1)},${(y + dx * r).toFixed(1)}`)
    right.push(`${(x + dy * r).toFixed(1)},${(y - dx * r).toFixed(1)}`)
  }
  const rEnd = Math.max(0.4, (s.size * widthFactor(s, p[(n - 1) * 3 + 2])) / 2)
  const rStart = Math.max(0.4, (s.size * widthFactor(s, p[2])) / 2)
  return `M${left[0]}L${left.join('L')}A${rEnd},${rEnd} 0 0,0 ${right[n - 1]}L${right.reverse().join('L')}A${rStart},${rStart} 0 0,0 ${left[0]}Z`
}

const widthFactor = (s: InkStroke, pressure: number) => (s.tool === 'highlighter' ? 1 : 0.55 + 0.9 * Math.min(1, Math.max(0, pressure)))
export const strokeOpacity = (s: InkStroke) => (s.tool === 'highlighter' ? 0.32 : 1)
