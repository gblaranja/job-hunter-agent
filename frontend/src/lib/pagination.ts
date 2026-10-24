export const pageSizeOptions = [10, 20, 50, 100] as const

export function isPageSize(value: number) {
  return (pageSizeOptions as readonly number[]).includes(value)
}

/**
 * Até sete posições: a primeira e a última página sempre aparecem, as vizinhas da atual no meio e
 * reticências no lugar dos saltos. A largura fica constante enquanto o usuário navega.
 */
export function pageWindow(page: number, pageCount: number): (number | 'gap')[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, index) => index)
  let start = Math.max(1, page - 1)
  let end = Math.min(pageCount - 2, page + 1)
  if (page <= 3) [start, end] = [1, 4]
  if (page >= pageCount - 4) [start, end] = [pageCount - 5, pageCount - 2]
  const middle = Array.from({ length: end - start + 1 }, (_, index) => start + index)
  return [0, ...(start > 1 ? ['gap' as const] : []), ...middle, ...(end < pageCount - 2 ? ['gap' as const] : []), pageCount - 1]
}
