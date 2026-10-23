import { useState } from 'react'
import { Link } from 'react-router'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { useBoard, type BoardCard as Card, type Schemas } from '@/api/queries'
import { PageHeader } from '@/components/PageHeader'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { useStatusMove } from '@/features/applications/useStatusMove'
import { formatDate, formatDays } from '@/lib/format'
import { statusAccent, statusLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { CalendarClock, CircleX, FileText, PanelRightOpen, PlusCircle } from 'lucide-react'
import { ApplicationSheet } from './ApplicationSheet'

export function BoardPage() {
  const board = useBoard()
  const { requestMove, dialog } = useStatusMove()
  const [dragging, setDragging] = useState<Card | null>(null)
  const [selected, setSelected] = useState<Card | null>(null)
  const sensors = useSensors(
    // Um clique curto abre o card; só arrastar a partir de alguns pixels inicia a mudança de coluna.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  )

  function handleDragStart(event: DragStartEvent) {
    setDragging((event.active.data.current?.card as Card | undefined) ?? null)
  }

  function handleDragEnd(event: DragEndEvent) {
    setDragging(null)
    const card = event.active.data.current?.card as Card | undefined
    const target = event.over?.id as Card['status'] | undefined
    if (card && target) requestMove(card, target)
  }

  return (
    <>
      <PageHeader
        title="Kanban"
        description="Arraste os cards entre as etapas. Ao soltar em Aplicada, você escolhe o currículo enviado."
        actions={
          <Button asChild>
            <Link to="/jobs/new">
              <PlusCircle /> Nova vaga
            </Link>
          </Button>
        }
      />
      {board.isLoading && <LoadingBlock />}
      {board.error && <ErrorBlock error={board.error} />}
      {board.data && (
        <DndContext
          sensors={sensors}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          onDragCancel={() => setDragging(null)}
        >
          {/* relative: os textos sr-only (absolutos) dos cards ficam presos ao quadro e não alargam a página. */}
          <div className="relative -mx-4 flex gap-3 overflow-x-auto px-4 pb-4 md:-mx-8 md:px-8">
            {board.data.columns.map((column) => (
              <BoardColumn key={column.status} column={column} onOpen={setSelected} />
            ))}
          </div>
          <DragOverlay>{dragging && <BoardCardView card={dragging} overlay />}</DragOverlay>
        </DndContext>
      )}
      {dialog}
      {selected && <ApplicationSheet card={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function BoardColumn({ column, onOpen }: { column: Schemas['BoardColumn']; onOpen: (card: Card) => void }) {
  const { setNodeRef, isOver } = useDroppable({ id: column.status })
  return (
    <section
      ref={setNodeRef}
      aria-label={statusLabels[column.status]}
      className={cn(
        'flex w-72 shrink-0 flex-col rounded-xl bg-muted/70 p-2 transition-colors',
        isOver && 'bg-accent ring-2 ring-ring/40',
      )}
    >
      <header className="flex items-center gap-2 px-2 pb-2 pt-1">
        <span className={cn('size-2.5 rounded-full', statusAccent[column.status])} aria-hidden />
        <h2 className="text-sm font-semibold">{statusLabels[column.status]}</h2>
        <span className="ml-auto text-xs text-muted-foreground">{column.cards.length}</span>
      </header>
      <div className="flex min-h-24 flex-col gap-2">
        {column.cards.map((card) => (
          <DraggableCard key={card.applicationId} card={card} onOpen={onOpen} />
        ))}
      </div>
    </section>
  )
}

function DraggableCard({ card, onOpen }: { card: Card; onOpen: (card: Card) => void }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: card.applicationId,
    data: { card },
  })
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      aria-label={`${card.title} em ${card.company}`}
      onClick={() => onOpen(card)}
      className={cn('cursor-grab touch-none rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-ring', isDragging && 'opacity-40')}
    >
      <BoardCardView card={card} onOpen={() => onOpen(card)} />
    </div>
  )
}

function BoardCardView({ card, overlay = false, onOpen }: { card: Card; overlay?: boolean; onOpen?: () => void }) {
  const overdue = card.nextActionAt != null && new Date(card.nextActionAt) < new Date()
  return (
    <article
      className={cn(
        'rounded-lg border bg-card p-3 text-card-foreground shadow-xs',
        overlay && 'rotate-1 cursor-grabbing shadow-lg',
      )}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-medium leading-snug">{card.title}</h3>
          <p className="truncate text-xs text-muted-foreground">{card.company}</p>
        </div>
        {onOpen && (
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label="Abrir detalhes"
            onPointerDown={(event) => event.stopPropagation()}
            onKeyDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation()
              onOpen()
            }}
          >
            <PanelRightOpen />
          </Button>
        )}
      </div>
      <dl className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <div title="Tempo nesta etapa">
          <dt className="sr-only">Na etapa há</dt>
          <dd>{formatDays(card.daysInStatus)}</dd>
        </div>
        {card.resumeLabel && (
          <div className="flex items-center gap-1" title="Currículo enviado">
            <dt>
              <FileText className="size-3" aria-label="Currículo" />
            </dt>
            <dd className="max-w-32 truncate">{card.resumeLabel}</dd>
          </div>
        )}
        {card.nextActionAt && (
          <div className={cn('flex items-center gap-1', overdue && 'font-medium text-destructive')} title="Próxima ação">
            <dt>
              <CalendarClock className="size-3" aria-label="Próxima ação" />
            </dt>
            <dd>{formatDate(card.nextActionAt)}</dd>
          </div>
        )}
        {card.linkStatus === 'REMOVIDO' && (
          <div className="flex items-center gap-1 text-destructive">
            <dt>
              <CircleX className="size-3" aria-label="Link" />
            </dt>
            <dd>vaga fora do ar</dd>
          </div>
        )}
      </dl>
    </article>
  )
}
