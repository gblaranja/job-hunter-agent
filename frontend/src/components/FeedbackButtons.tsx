import { useState } from 'react'
import { toast } from 'sonner'
import { useSaveFeedback, type FeedbackRating, type FeedbackReason } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { feedbackReasonLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { Meh, ThumbsDown, ThumbsUp } from 'lucide-react'

type Props = {
  jobId: number
  rating?: FeedbackRating | null
  reasons?: FeedbackReason[]
  onSaved?: (rating: FeedbackRating) => void
}

const allReasons = Object.keys(feedbackReasonLabels) as FeedbackReason[]

/** Gostei, neutro e não gostei; "não gostei" pede os motivos, que viram filtros sugeridos mais adiante. */
export function FeedbackButtons({ jobId, rating, reasons = [], onSaved }: Props) {
  const save = useSaveFeedback()
  const [open, setOpen] = useState(false)
  const [chosen, setChosen] = useState<FeedbackReason[]>(reasons)

  function send(next: FeedbackRating, nextReasons: FeedbackReason[] = []) {
    save.mutate(
      { jobId, rating: next, reasons: nextReasons },
      {
        onSuccess: () => {
          setOpen(false)
          onSaved?.(next)
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function toggle(reason: FeedbackReason) {
    setChosen((current) => (current.includes(reason) ? current.filter((r) => r !== reason) : [...current, reason]))
  }

  return (
    <div className="flex flex-wrap items-center gap-1" role="group" aria-label="Sua avaliação da vaga">
      <Button
        variant={rating === 'GOSTEI' ? 'default' : 'outline'}
        size="sm"
        aria-pressed={rating === 'GOSTEI'}
        disabled={save.isPending}
        onClick={() => send('GOSTEI')}
      >
        <ThumbsUp /> Gostei
      </Button>
      <Button
        variant={rating === 'NEUTRO' ? 'secondary' : 'outline'}
        size="sm"
        aria-pressed={rating === 'NEUTRO'}
        disabled={save.isPending}
        onClick={() => send('NEUTRO')}
      >
        <Meh /> Neutro
      </Button>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant={rating === 'NAO_GOSTEI' ? 'destructive' : 'outline'}
            size="sm"
            aria-pressed={rating === 'NAO_GOSTEI'}
            disabled={save.isPending}
          >
            <ThumbsDown /> Não gostei
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-72">
          <p className="mb-3 text-sm font-medium">O que pesou contra?</p>
          <div className="flex flex-wrap gap-2">
            {allReasons.map((reason) => (
              <button
                key={reason}
                type="button"
                aria-pressed={chosen.includes(reason)}
                onClick={() => toggle(reason)}
                className={cn(
                  'rounded-full border px-3 py-1 text-xs transition-colors',
                  chosen.includes(reason) ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-muted',
                )}
              >
                {feedbackReasonLabels[reason]}
              </button>
            ))}
          </div>
          <Button size="sm" className="mt-4 w-full" onClick={() => send('NAO_GOSTEI', chosen)}>
            Salvar
          </Button>
        </PopoverContent>
      </Popover>
    </div>
  )
}
