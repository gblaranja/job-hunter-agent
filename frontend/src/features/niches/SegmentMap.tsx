import { ResponsiveContainer, Tooltip, Treemap, type TooltipContentProps } from 'recharts'
import type { NameType, ValueType } from 'recharts/types/component/DefaultTooltipContent'
import type { SegmentView } from '@/api/queries'

type Tile = { id: number; name: string; size: number; highFit: number; withOpenJobs: number; avgFit: number | null }

/**
 * Mapa do nicho: a área de cada subsegmento é o número de empresas. Uma cor só (a série 1); o subsegmento escolhido
 * no filtro fica destacado e os outros esmaecem. Clicar num bloco filtra a tabela.
 */
export function SegmentMap({
  segments,
  selected,
  onSelect,
}: {
  segments: SegmentView[]
  selected?: number
  onSelect: (segmentId: number | undefined) => void
}) {
  const tiles: Tile[] = segments
    .filter((segment) => segment.companies > 0)
    .map((segment) => ({
      id: segment.id,
      name: segment.name,
      size: segment.companies,
      highFit: segment.highFit,
      withOpenJobs: segment.withOpenJobs,
      avgFit: segment.avgFit ?? null,
    }))
  if (tiles.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhuma empresa classificada neste nicho ainda.</p>
  }
  return (
    <div
      className="h-56 w-full"
      role="img"
      aria-label={`Empresas por subsegmento: ${tiles.map((tile) => `${tile.name}, ${tile.size}`).join('; ')}`}
    >
      <ResponsiveContainer>
        <Treemap
          data={tiles}
          dataKey="size"
          nameKey="name"
          isAnimationActive={false}
          content={(props) => <TileShape {...(props as unknown as TileProps)} selected={selected} onSelect={onSelect} />}
        >
          <Tooltip content={TileTooltip} />
        </Treemap>
      </ResponsiveContainer>
    </div>
  )
}

type TileProps = Tile & { x: number; y: number; width: number; height: number; depth: number }

function TileShape({
  x,
  y,
  width,
  height,
  depth,
  id,
  name,
  size,
  selected,
  onSelect,
}: TileProps & { selected?: number; onSelect: (segmentId: number | undefined) => void }) {
  if (depth !== 1 || width <= 0 || height <= 0) return null
  const dimmed = selected !== undefined && selected !== id
  const showLabel = width > 72 && height > 34
  return (
    <g
      className="cursor-pointer"
      onClick={() => onSelect(selected === id ? undefined : id)}
      aria-label={`${name}: ${size} empresas`}
    >
      {/* o filete de 2px da cor do card separa os blocos */}
      <rect
        x={x}
        y={y}
        width={width}
        height={height}
        rx={4}
        fill="var(--series-1)"
        fillOpacity={dimmed ? 0.12 : selected === id ? 0.45 : 0.25}
        stroke="var(--card)"
        strokeWidth={2}
      />
      {showLabel && (
        <>
          <text x={x + 8} y={y + 18} fill="var(--foreground)" fontSize={12} fontWeight={500}>
            {truncate(name, Math.floor(width / 7))}
          </text>
          <text x={x + 8} y={y + 33} fill="var(--muted-foreground)" fontSize={11}>
            {size} {size === 1 ? 'empresa' : 'empresas'}
          </text>
        </>
      )}
    </g>
  )
}

function TileTooltip({ active, payload }: TooltipContentProps<ValueType, NameType>) {
  const tile = active ? (payload?.[0]?.payload as Tile | undefined) : undefined
  if (!tile) return null
  return (
    <div className="rounded-md border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md">
      <p className="font-medium">{tile.name}</p>
      <p className="tabular-nums text-muted-foreground">
        {tile.size} empresas · {tile.highFit} com fit alto · {tile.withOpenJobs} com vaga aberta
      </p>
      {tile.avgFit != null && <p className="tabular-nums text-muted-foreground">Fit médio {tile.avgFit}</p>}
    </div>
  )
}

function truncate(text: string, max: number) {
  return text.length <= max ? text : `${text.slice(0, Math.max(1, max - 1))}…`
}
