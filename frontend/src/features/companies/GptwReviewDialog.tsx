import { Link } from 'react-router'
import { toast } from 'sonner'
import { useConfirmRecognition, useRecognitionReview, useRejectRecognition, type RecognitionView } from '@/api/queries'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { formatDate } from '@/lib/format'
import { recognitionLevelLabels } from '@/lib/labels'
import { Check, ExternalLink, X } from 'lucide-react'

/** Selos com confiança baixa ou homônimos: só aparecem depois da sua confirmação. */
export function GptwReviewDialog({ onClose }: { onClose: () => void }) {
  const review = useRecognitionReview()
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Revisar selos GPTW</DialogTitle>
          <DialogDescription>
            A IA achou estes selos com confiança abaixo de 80% ou com empresas de nome parecido. Confira a evidência antes
            de confirmar.
          </DialogDescription>
        </DialogHeader>
        {review.isLoading && <LoadingBlock />}
        {review.error && <ErrorBlock error={review.error} />}
        {review.data && review.data.length === 0 && <EmptyBlock title="Nenhum selo esperando revisão." />}
        <ul className="space-y-3">
          {review.data?.map((item) => (
            <ReviewItem key={item.id} item={item} />
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}

function ReviewItem({ item }: { item: RecognitionView }) {
  const confirm = useConfirmRecognition()
  const reject = useRejectRecognition()
  const busy = confirm.isPending || reject.isPending

  return (
    <li className="space-y-2 rounded-lg border p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <Link to={`/companies/${item.companyId}`} className="font-medium hover:underline">
            {item.companyName}
          </Link>
          <p className="text-sm">
            {recognitionLevelLabels[item.level]}
            {item.year ? ` ${item.year}` : ''}
            {item.rankingName ? ` · ${item.rankingName}` : ''}
            {item.rankingPosition ? ` · ${item.rankingPosition}º lugar` : ''}
          </p>
          <p className="text-xs text-muted-foreground">
            Confiança {Math.round(item.confidence * 100)}% · verificado em {formatDate(item.checkedAt)}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={busy}
            onClick={() =>
              reject.mutate(item.id, {
                onSuccess: () => toast.success('Selo descartado.'),
                onError: (error) => toast.error(error.message),
              })
            }
          >
            <X /> Não é esta empresa
          </Button>
          <Button
            size="sm"
            disabled={busy}
            onClick={() =>
              confirm.mutate(item.id, {
                onSuccess: () => toast.success(`Selo de ${item.companyName} confirmado.`),
                onError: (error) => toast.error(error.message),
              })
            }
          >
            <Check /> Confirmar
          </Button>
        </div>
      </div>
      {item.matchedName && <p className="text-sm">Na evidência: “{item.matchedName}”</p>}
      {item.homonyms.length > 0 && (
        <p className="text-sm text-amber-700 dark:text-warning">Nomes parecidos cadastrados: {item.homonyms.join(', ')}</p>
      )}
      {item.evidenceExcerpt && <blockquote className="border-l-2 pl-3 text-sm text-muted-foreground">{item.evidenceExcerpt}</blockquote>}
      {item.rationale && <p className="text-xs text-muted-foreground">{item.rationale}</p>}
      {item.evidenceUrl && (
        <a
          href={item.evidenceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
        >
          <ExternalLink className="size-3.5" aria-hidden /> Abrir evidência
        </a>
      )}
    </li>
  )
}
