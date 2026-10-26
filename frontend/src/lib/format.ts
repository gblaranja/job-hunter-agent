import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'

export function formatDate(value: string | null | undefined): string {
  return value ? format(parseISO(value), "d 'de' MMM yyyy", { locale: ptBR }) : '—'
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? format(parseISO(value), "d 'de' MMM yyyy, HH:mm", { locale: ptBR }) : '—'
}

export function formatDays(days: number): string {
  if (days === 0) return 'hoje'
  return days === 1 ? '1 dia' : `${days} dias`
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

/** Fração de um total; nula quando o total é zero. */
export function rate(part: number, total: number): number | null {
  return total === 0 ? null : part / total
}

export function formatPercent(rate: number | null | undefined): string {
  return rate == null ? '—' : `${Math.round(rate * 100)}%`
}

/** Valor de um <input type="date"> (yyyy-MM-dd) a partir de um instante ISO, no fuso local. */
export function toDateInput(value: string | null | undefined): string {
  return value ? format(parseISO(value), 'yyyy-MM-dd') : ''
}

/** Instante ISO ao meio-dia local da data escolhida, para não virar o dia anterior em UTC. */
export function fromDateInput(value: string): string | null {
  return value ? new Date(`${value}T12:00:00`).toISOString() : null
}

/** Há quanto tempo, curto: "há 5 h", "há 3 dias". */
export function formatAge(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) return '—'
  const hours = Math.max(0, Math.floor((now.getTime() - parseISO(value).getTime()) / 3_600_000))
  if (hours < 1) return 'agora há pouco'
  if (hours < 24) return `há ${hours} h`
  const days = Math.floor(hours / 24)
  return days === 1 ? 'há 1 dia' : `há ${days} dias`
}

const salaryPeriods: Record<string, string> = { MENSAL: 'por mês', ANUAL: 'por ano', HORA: 'por hora' }

/** Faixa salarial como veio da vaga: "R$ 12.000 a R$ 15.000 por mês · PJ". */
export function formatSalary(salary: {
  min?: number | null
  max?: number | null
  currency: string
  period: string
  regime?: string | null
}): string {
  const money = (value: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: salary.currency, maximumFractionDigits: 0 }).format(value)
  const min = salary.min ?? salary.max
  const max = salary.max ?? salary.min
  const range = min == null || max == null ? '—' : min === max ? money(min) : `${money(min)} a ${money(max)}`
  return [`${range} ${salaryPeriods[salary.period] ?? ''}`.trim(), salary.regime].filter(Boolean).join(' · ')
}
