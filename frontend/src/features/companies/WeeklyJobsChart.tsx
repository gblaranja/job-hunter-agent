import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps } from 'recharts'
import type { WeeklyMetrics } from '@/api/queries'
import type { NameType, ValueType } from 'recharts/types/component/DefaultTooltipContent'
import { format, parseISO } from 'date-fns'
import { formatDate } from '@/lib/format'

const series = [
  { key: 'jobsOpened', label: 'Abertas', color: 'var(--series-1)' },
  { key: 'jobsClosed', label: 'Fechadas', color: 'var(--series-2)' },
] as const

/** Vagas abertas e fechadas por semana; o tooltip traz também as ativas e os reposts. */
export function WeeklyJobsChart({ weeks }: { weeks: WeeklyMetrics[] }) {
  return (
    <div className="h-64 w-full" role="img" aria-label="Vagas abertas e fechadas por semana">
      <ResponsiveContainer>
        <BarChart data={weeks} barGap={2} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid vertical={false} stroke="var(--border)" />
          <XAxis
            dataKey="weekStart"
            tickFormatter={(value: string) => format(parseISO(value), 'dd/MM')}
            tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: 'var(--border)' }}
            minTickGap={16}
          />
          <YAxis allowDecimals={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }} tickLine={false} axisLine={false} />
          <Tooltip content={WeekTooltip} cursor={{ fill: 'var(--muted)', opacity: 0.5 }} />
          <Legend
            verticalAlign="top"
            align="right"
            height={28}
            iconType="square"
            formatter={(value) => <span className="text-sm text-foreground">{value}</span>}
          />
          {series.map((item) => (
            <Bar key={item.key} dataKey={item.key} name={item.label} fill={item.color} radius={[4, 4, 0, 0]} maxBarSize={18} />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function WeekTooltip({ active, payload }: TooltipContentProps<ValueType, NameType>) {
  if (!active || !payload?.length) return null
  const week = payload[0].payload as WeeklyMetrics
  return (
    <div className="rounded-md border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md">
      <div className="mb-1 font-medium">Semana de {formatDate(week.weekStart)}</div>
      {series.map((item) => (
        <div key={item.key} className="flex items-center gap-2">
          <span className="size-2.5 rounded-sm" style={{ backgroundColor: item.color }} aria-hidden />
          {item.label}: <span className="tabular-nums">{week[item.key]}</span>
        </div>
      ))}
      <div className="mt-1 text-muted-foreground">
        Ativas: {week.jobsActive} · Reposts: {week.reposts}
        {week.headcount != null && ` · Funcionários: ${week.headcount.toLocaleString('pt-BR')}`}
      </div>
    </div>
  )
}
