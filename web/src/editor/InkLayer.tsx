import { PAGE_WIDTH, strokeOpacity, strokePath, type InkDoc } from './ink'

/** Read-only rendering of the handwriting layer on top of the page. */
export function InkLayer({ ink, height }: { ink: InkDoc; height: number }) {
  return (
    <svg className="ink-layer" width={PAGE_WIDTH} height={height} viewBox={`0 0 ${PAGE_WIDTH} ${height}`} aria-hidden="true">
      {ink.strokes.map((s) => (
        <path key={s.id} d={strokePath(s)} fill={s.color} fillOpacity={strokeOpacity(s)} />
      ))}
    </svg>
  )
}
