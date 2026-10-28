import { useState } from 'react'
import { toast } from 'sonner'
import { useApplicationOffers, useDeleteOffer, useNegotiationDraft, type NegotiationDraft, type Offer } from '@/api/interviews'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatBrl } from '@/lib/format'
import { offerDecisionLabels } from '@/lib/interviewLabels'
import { describeOffer } from '@/lib/offers'
import { Copy, Pencil, Plus, Trash2 } from 'lucide-react'
import { OfferDialog } from './OfferDialog'

/** As propostas de uma candidatura, com o equivalente anual e o rascunho da negociação. */
export function ApplicationOffers({ applicationId }: { applicationId: number }) {
  const offers = useApplicationOffers(applicationId)
  const remove = useDeleteOffer()
  const [editing, setEditing] = useState<Offer | 'new' | null>(null)
  const [negotiating, setNegotiating] = useState<Offer | null>(null)

  return (
    <div className="space-y-3">
      {offers.isLoading && <LoadingBlock lines={2} />}
      {offers.error && <ErrorBlock error={offers.error} />}
      {offers.data?.length === 0 && <p className="text-sm text-muted-foreground">Nenhuma proposta registrada.</p>}
      {offers.data?.map((offer) => (
        <div key={offer.id} className="space-y-2 rounded-md border p-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{describeOffer(offer)}</span>
            <Badge variant="secondary">{offerDecisionLabels[offer.decision]}</Badge>
            <div className="ml-auto flex gap-1">
              <Button size="icon" variant="ghost" aria-label="Editar proposta" onClick={() => setEditing(offer)}>
                <Pencil />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                aria-label="Excluir proposta"
                onClick={() => remove.mutate(offer.id, { onError: (error) => toast.error(error.message) })}
              >
                <Trash2 />
              </Button>
            </div>
          </div>
          <p className="text-sm">
            Equivalente anual:{' '}
            <span className="font-semibold tabular-nums">
              {offer.annualEquivalent == null ? 'sem câmbio' : formatBrl(offer.annualEquivalent)}
            </span>
            {offer.monthlyEquivalent != null && (
              <span className="text-muted-foreground"> ({formatBrl(offer.monthlyEquivalent)} por mês)</span>
            )}
          </p>
          <Button size="sm" variant="outline" onClick={() => setNegotiating(negotiating?.id === offer.id ? null : offer)}>
            Rascunho de negociação
          </Button>
          {negotiating?.id === offer.id && <Negotiation offer={offer} />}
        </div>
      ))}
      <Button size="sm" onClick={() => setEditing('new')}>
        <Plus /> Registrar proposta
      </Button>
      {editing && (
        <OfferDialog applicationId={applicationId} offer={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />
      )}
    </div>
  )
}

function Negotiation({ offer }: { offer: Offer }) {
  const draft = useNegotiationDraft()
  const [target, setTarget] = useState('')
  const [args, setArgs] = useState('')
  const [result, setResult] = useState<NegotiationDraft | null>(null)

  function generate() {
    draft.mutate(
      {
        id: offer.id,
        targetSalary: target.trim() ? Number(target.replace(',', '.')) : null,
        arguments: args.trim() || null,
      },
      { onSuccess: setResult, onError: (error) => toast.error(error.message) },
    )
  }

  return (
    <div className="space-y-2 rounded-md bg-muted/40 p-3">
      <div className="space-y-1">
        <Label htmlFor={`target-${offer.id}`}>Valor-base que você quer pedir ({offer.currency}, mesmo período)</Label>
        <Input id={`target-${offer.id}`} inputMode="decimal" value={target} onChange={(event) => setTarget(event.target.value)} />
      </div>
      <div className="space-y-1">
        <Label htmlFor={`args-${offer.id}`}>Seus argumentos (opcional)</Label>
        <Textarea id={`args-${offer.id}`} rows={2} value={args} onChange={(event) => setArgs(event.target.value)} />
      </div>
      <Button size="sm" onClick={generate} disabled={draft.isPending}>
        {draft.isPending ? 'Escrevendo…' : 'Gerar rascunho'}
      </Button>
      {result && (
        <div className="space-y-2">
          <Textarea aria-label="Rascunho da negociação" rows={8} value={result.text} readOnly />
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => navigator.clipboard.writeText(result.text).then(() => toast.success('Rascunho copiado.'))}
            >
              <Copy /> Copiar
            </Button>
            <span className="text-xs text-muted-foreground">
              {result.source === 'IA' ? 'Escrito pela IA só com os seus números.' : 'Texto modelo.'} Nada é enviado.
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
