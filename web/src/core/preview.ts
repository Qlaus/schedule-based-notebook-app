/** Plain-text preview of a Markdown note body for lists and cards. */
export function plainPreview(markdown: string): string {
  return markdown
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+\[( |x|X)\]\s+/gm, (_m, c: string) => (c.trim() ? '☑ ' : '☐ '))
    .replace(/^\s*([-*+]|\d+[.)])\s+/gm, '')
    .replace(/(\*\*|__)(.+?)\1/g, '$2')
    .replace(/(\*|_)(.+?)\1/g, '$2')
    .replace(/~~(.+?)~~/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\\([\\`*_{}[\]()#+\-.!~])/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()
}
