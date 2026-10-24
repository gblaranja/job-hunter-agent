import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useUpdateNiche, useUpdateSegment, type NicheView } from '@/api/queries'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const components = [
  { key: 'segment', label: 'Subsegmento', hint: 'o fit base de cada subsegmento, abaixo' },
  { key: 'stack', label: 'Stack', hint: 'Java, Spring, microsserviços, Kafka e AWS nas últimas vagas' },
  { key: 'domain', label: 'Domínio', hint: 'termos do nicho nas vagas (crédito, antifraude, Pix...)' },
  { key: 'signals', label: 'Sinais', hint: 'selo GPTW, ritmo de vagas e modelo de trabalho' },
] as const

type WeightKey = (typeof components)[number]['key']

/** Pesos do fit do nicho (em %, normalizados ao salvar) e o fit base de cada subsegmento. */
export function WeightsDialog({ niche, onClose }: { niche: NicheView; onClose: () => void }) {
  const updateNiche = useUpdateNiche()
  const updateSegment = useUpdateSegment()
  const [weights, setWeights] = useState<Record<WeightKey, string>>(() => ({
    segment: percent(niche.fitWeights.segment),
    stack: percent(niche.fitWeights.stack),
    domain: percent(niche.fitWeights.domain),
    signals: percent(niche.fitWeights.signals),
  }))
  const [baseFits, setBaseFits] = useState<Record<number, string>>(() =>
    Object.fromEntries(niche.segments.map((segment) => [segment.id, String(segment.baseFit)])),
  )
  const total = Object.values(weights).reduce((sum, value) => sum + (Number(value) || 0), 0)
  const saving = updateNiche.isPending || updateSegment.isPending

  async function save(event: FormEvent) {
    event.preventDefault()
    if (total <= 0) return
    try {
      for (const segment of niche.segments) {
        const baseFit = Number(baseFits[segment.id])
        if (Number.isInteger(baseFit) && baseFit !== segment.baseFit && baseFit >= 0 && baseFit <= 100) {
          await updateSegment.mutateAsync({ nicheId: niche.id, segmentId: segment.id, baseFit })
        }
      }
      await updateNiche.mutateAsync({
        id: niche.id,
        fitWeights: {
          segment: (Number(weights.segment) || 0) / total,
          stack: (Number(weights.stack) || 0) / total,
          domain: (Number(weights.domain) || 0) / total,
          signals: (Number(weights.signals) || 0) / total,
        },
      })
      toast.success(`Pesos de ${niche.name} salvos; o fit foi recalculado.`)
      onClose()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Pesos do nicho {niche.name}</DialogTitle>
          <DialogDescription>
            O fit é a média ponderada dos quatro componentes. Os pesos são normalizados para somar 100%.
          </DialogDescription>
        </DialogHeader>
        <form id="weights" className="space-y-5" onSubmit={save}>
          <fieldset className="space-y-3">
            <legend className="text-sm font-medium">Componentes</legend>
            {components.map((component) => (
              <div key={component.key} className="grid grid-cols-[1fr_5rem] items-center gap-3">
                <Label htmlFor={`weight-${component.key}`} className="flex-col items-start gap-0.5">
                  {component.label}
                  <span className="text-xs font-normal text-muted-foreground">{component.hint}</span>
                </Label>
                <Input
                  id={`weight-${component.key}`}
                  type="number"
                  min={0}
                  max={100}
                  inputMode="numeric"
                  value={weights[component.key]}
                  onChange={(event) => setWeights((previous) => ({ ...previous, [component.key]: event.target.value }))}
                />
              </div>
            ))}
            <p className={total === 100 ? 'text-xs text-muted-foreground' : 'text-xs text-amber-700 dark:text-warning'}>
              Soma: {total}%{total !== 100 && total > 0 ? ' (será normalizada para 100%)' : ''}
            </p>
          </fieldset>
          <fieldset className="space-y-3">
            <legend className="text-sm font-medium">Fit base dos subsegmentos (0 a 100)</legend>
            {niche.segments.map((segment) => (
              <div key={segment.id} className="grid grid-cols-[1fr_5rem] items-center gap-3">
                <Label htmlFor={`segment-${segment.id}`} className="font-normal">
                  {segment.name}
                </Label>
                <Input
                  id={`segment-${segment.id}`}
                  type="number"
                  min={0}
                  max={100}
                  inputMode="numeric"
                  value={baseFits[segment.id]}
                  onChange={(event) => setBaseFits((previous) => ({ ...previous, [segment.id]: event.target.value }))}
                />
              </div>
            ))}
          </fieldset>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="weights" disabled={saving || total <= 0}>
            Salvar e recalcular
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function percent(value: number) {
  return String(Math.round(value * 100))
}
