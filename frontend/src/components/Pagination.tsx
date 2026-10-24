import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pageSizeOptions, pageWindow } from '@/lib/pagination'
import { cn } from '@/lib/utils'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'

type PaginationProps = {
  /** Página atual, começando em 0. */
  page: number
  size: number
  total: number
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
  /** Nome do item no plural, para o resumo "1–20 de 134 vagas". */
  itemLabel?: string
  className?: string
}

/** Navegação entre páginas com escolha de quantos itens mostrar por página. */
export function Pagination({ page, size, total, onPageChange, onSizeChange, itemLabel = 'itens', className }: PaginationProps) {
  const pageCount = Math.max(1, Math.ceil(total / size))
  const first = total === 0 ? 0 : page * size + 1
  const last = Math.min(total, (page + 1) * size)
  const isFirst = page <= 0
  const isLast = page >= pageCount - 1

  return (
    <div className={cn('flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between', className)}>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
        <span aria-live="polite">
          {first}–{last} de {total} {itemLabel}
        </span>
        <span className="flex items-center gap-2">
          Por página
          <Select value={String(size)} onValueChange={(value) => onSizeChange(Number(value))}>
            <SelectTrigger size="sm" aria-label="Itens por página">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {pageSizeOptions.map((option) => (
                <SelectItem key={option} value={String(option)}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </span>
      </div>
      <nav aria-label="Paginação" className="flex items-center gap-1">
        <Button variant="outline" size="icon-sm" aria-label="Primeira página" disabled={isFirst} onClick={() => onPageChange(0)}>
          <ChevronsLeft />
        </Button>
        <Button variant="outline" size="icon-sm" aria-label="Página anterior" disabled={isFirst} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft />
        </Button>
        <span className="px-2 text-sm text-muted-foreground sm:hidden">
          Página {page + 1} de {pageCount}
        </span>
        <span className="hidden items-center gap-1 sm:flex">
          {pageWindow(page, pageCount).map((item, index) =>
            item === 'gap' ? (
              <span key={`gap-${index}`} className="px-1 text-sm text-muted-foreground" aria-hidden>
                …
              </span>
            ) : (
              <Button
                key={item}
                variant={item === page ? 'default' : 'ghost'}
                size="icon-sm"
                aria-label={`Página ${item + 1}`}
                aria-current={item === page ? 'page' : undefined}
                onClick={() => onPageChange(item)}
              >
                {item + 1}
              </Button>
            ),
          )}
        </span>
        <Button variant="outline" size="icon-sm" aria-label="Próxima página" disabled={isLast} onClick={() => onPageChange(page + 1)}>
          <ChevronRight />
        </Button>
        <Button variant="outline" size="icon-sm" aria-label="Última página" disabled={isLast} onClick={() => onPageChange(pageCount - 1)}>
          <ChevronsRight />
        </Button>
      </nav>
    </div>
  )
}
