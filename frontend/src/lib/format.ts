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
