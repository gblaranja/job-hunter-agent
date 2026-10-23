import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

// Classes completas para o Tailwind encontrá-las no código.
const spans = {
  4: 'md:col-span-4',
  5: 'md:col-span-5',
  6: 'md:col-span-6',
  7: 'md:col-span-7',
  8: 'md:col-span-8',
  12: 'md:col-span-12',
} as const

/**
 * Agrupa de 2 a 6 itens num painel só, numa grade de 12 colunas separada por filetes.
 * É a resposta para "como agrupar itens sem card dentro de card": os blocos dividem uma superfície.
 */
export function Bento({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn('grid gap-px overflow-hidden rounded-2xl border bg-border md:grid-cols-12', className)}
      {...props}
    />
  )
}

/** Um bloco do Bento. Larguras diferentes marcam o que pesa mais; repetir a mesma largura em tudo apaga isso. */
export function BentoTile({
  span = 6,
  className,
  ...props
}: ComponentProps<'article'> & { span?: keyof typeof spans }) {
  return <article className={cn('flex flex-col bg-card p-6 md:p-8', spans[span], className)} {...props} />
}
