/** Side-by-side layout of overlapping events in a time grid (port of core/layout/EventColumns.kt). */
export interface TimedItem<T> { item: T; start: number; end: number }
export interface PlacedItem<T> { item: T; start: number; end: number; column: number; columnCount: number }

export function layoutColumns<T>(items: TimedItem<T>[]): PlacedItem<T>[] {
  const sorted = [...items].sort((a, b) => a.start - b.start || b.end - a.end)
  const result: PlacedItem<T>[] = []
  let cluster: { it: TimedItem<T>; column: number }[] = []
  let columnEnds: number[] = []
  let clusterEnd = -Infinity
  const flush = () => {
    const count = columnEnds.length
    for (const c of cluster) result.push({ ...c.it, column: c.column, columnCount: count })
    cluster = []
    columnEnds = []
  }
  for (const it of sorted) {
    if (cluster.length > 0 && it.start >= clusterEnd) flush()
    let column = columnEnds.findIndex((end) => end <= it.start)
    if (column < 0) {
      column = columnEnds.length
      columnEnds.push(it.end)
    } else {
      columnEnds[column] = it.end
    }
    cluster.push({ it, column })
    clusterEnd = cluster.length === 1 ? it.end : Math.max(clusterEnd, it.end)
  }
  if (cluster.length > 0) flush()
  return result
}
