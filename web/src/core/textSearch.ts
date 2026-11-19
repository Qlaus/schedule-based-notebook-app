/** Multi-word, Turkish-aware search (port of TextSearch.kt). */
export function normalize(text: string, locale = 'tr-TR'): string {
  return text
    .toLocaleLowerCase(locale)
    .replace(/ı/g, 'i').replace(/ş/g, 's').replace(/ğ/g, 'g')
    .replace(/ü/g, 'u').replace(/ö/g, 'o').replace(/ç/g, 'c')
    .replace(/i̇/g, 'i')
}

export function tokens(query: string, locale = 'tr-TR'): string[] {
  return normalize(query, locale).split(/[\s,]+/).filter((t) => t.length > 0)
}

export function textMatches(haystack: string, query: string, locale = 'tr-TR'): boolean {
  const terms = tokens(query, locale)
  if (terms.length === 0) return false
  const text = normalize(haystack, locale)
  return terms.every((t) => text.includes(t))
}
