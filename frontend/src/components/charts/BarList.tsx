import type { ReactNode } from 'react'

export type BarItem = {
  label: string
  value: number
  /** Texto à direita; por padrão, o valor. */
  display?: ReactNode
  /** Série 2 (laranja) destaca o item, sempre junto de um selo ou texto que diga o motivo. */
  tone?: 'series-1' | 'series-2'
  badge?: ReactNode
}

/**
 * Barras horizontais ordenadas com o valor escrito ao lado: lê como uma tabela, sem depender da cor. O tooltip
 * nativo repete rótulo e valor.
 */
export function BarList({ items, max, ariaLabel }: { items: BarItem[]; max?: number; ariaLabel: string }) {
  const top = Math.max(1, max ?? Math.max(...items.map((item) => item.value)))
  return (
    <ul className="space-y-2" aria-label={ariaLabel}>
      {items.map((item) => (
        <li key={item.label} className="grid grid-cols-[minmax(0,9rem)_1fr_auto] items-center gap-3 text-sm" title={`${item.label}: ${item.value}`}>
          <span className="flex min-w-0 items-center gap-1.5">
            <span className="truncate">{item.label}</span>
            {item.badge}
          </span>
          <span className="h-2.5 rounded-r bg-muted/60">
            <span
              className="block h-full rounded-r"
              style={{
                width: item.value === 0 ? '0%' : `${Math.max(2, (item.value / top) * 100)}%`,
                backgroundColor: `var(--${item.tone ?? 'series-1'})`,
              }}
            />
          </span>
          <span className="w-12 text-right tabular-nums text-muted-foreground">{item.display ?? item.value}</span>
        </li>
      ))}
    </ul>
  )
}
