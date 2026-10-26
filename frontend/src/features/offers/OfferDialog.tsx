import { useState } from 'react'
import { toast } from 'sonner'
import { useSaveOffer, type Offer, type OfferCommand, type OfferDecision, type PayPeriod, type PayRegime } from '@/api/interviews'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { offerDecisionLabels, payPeriodLabels, payRegimeLabels } from '@/lib/interviewLabels'

type Form = {
  regime: PayRegime
  baseSalary: string
  currency: string
  period: PayPeriod
  annualBonus: string
  monthlyBenefits: string
  benefitsNotes: string
  equity: string
  startDate: string
  respondBy: string
  decision: OfferDecision
  notes: string
}

const text = (value: number | string | null | undefined) => (value == null ? '' : String(value))
const number = (value: string) => (value.trim() === '' ? null : Number(value.replace(',', '.')))
const optional = (value: string) => (value.trim() === '' ? null : value.trim())

/** Os números da proposta como a empresa passou; o equivalente anual é calculado no backend. */
export function OfferDialog({ applicationId, offer, onClose }: { applicationId: number; offer?: Offer; onClose: () => void }) {
  const save = useSaveOffer()
  const [form, setForm] = useState<Form>({
    regime: offer?.regime ?? 'CLT',
    baseSalary: text(offer?.baseSalary),
    currency: offer?.currency ?? 'BRL',
    period: offer?.period ?? 'MENSAL',
    annualBonus: text(offer?.annualBonus),
    monthlyBenefits: text(offer?.monthlyBenefits),
    benefitsNotes: offer?.benefitsNotes ?? '',
    equity: offer?.equity ?? '',
    startDate: offer?.startDate ?? '',
    respondBy: offer?.respondBy ?? '',
    decision: offer?.decision ?? 'PENDENTE',
    notes: offer?.notes ?? '',
  })
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((current) => ({ ...current, [key]: value }))

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const body: OfferCommand = {
      regime: form.regime,
      baseSalary: Number(form.baseSalary.replace(',', '.')),
      currency: form.currency.trim().toUpperCase() || 'BRL',
      period: form.period,
      annualBonus: number(form.annualBonus),
      monthlyBenefits: number(form.monthlyBenefits),
      benefitsNotes: optional(form.benefitsNotes),
      equity: optional(form.equity),
      workModel: offer?.workModel ?? null,
      startDate: optional(form.startDate),
      respondBy: optional(form.respondBy),
      decision: form.decision,
      notes: optional(form.notes),
    }
    save.mutate(
      { id: offer?.id, applicationId, body },
      {
        onSuccess: () => {
          toast.success(offer ? 'Proposta atualizada.' : 'Proposta registrada.')
          onClose()
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{offer ? 'Editar proposta' : 'Registrar proposta'}</DialogTitle>
          <DialogDescription>Os valores como a empresa passou. Benefícios em reais por mês.</DialogDescription>
        </DialogHeader>
        <form id="offer-form" onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="offer-regime">Regime</Label>
            <Select value={form.regime} onValueChange={(value) => set('regime', value as PayRegime)}>
              <SelectTrigger id="offer-regime" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(payRegimeLabels) as PayRegime[]).map((regime) => (
                  <SelectItem key={regime} value={regime}>
                    {payRegimeLabels[regime]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-decision">Situação</Label>
            <Select value={form.decision} onValueChange={(value) => set('decision', value as OfferDecision)}>
              <SelectTrigger id="offer-decision" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(offerDecisionLabels) as OfferDecision[]).map((decision) => (
                  <SelectItem key={decision} value={decision}>
                    {offerDecisionLabels[decision]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-base">Valor-base</Label>
            <Input
              id="offer-base"
              required
              inputMode="decimal"
              value={form.baseSalary}
              onChange={(event) => set('baseSalary', event.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-2">
              <Label htmlFor="offer-currency">Moeda</Label>
              <Input
                id="offer-currency"
                maxLength={3}
                value={form.currency}
                onChange={(event) => set('currency', event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="offer-period">Período</Label>
              <Select value={form.period} onValueChange={(value) => set('period', value as PayPeriod)}>
                <SelectTrigger id="offer-period" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(payPeriodLabels) as PayPeriod[]).map((period) => (
                    <SelectItem key={period} value={period}>
                      {payPeriodLabels[period]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-bonus">Bônus ou PLR no ano</Label>
            <Input
              id="offer-bonus"
              inputMode="decimal"
              value={form.annualBonus}
              onChange={(event) => set('annualBonus', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-benefits">Benefícios por mês (R$)</Label>
            <Input
              id="offer-benefits"
              inputMode="decimal"
              placeholder="vazio usa a premissa"
              value={form.monthlyBenefits}
              onChange={(event) => set('monthlyBenefits', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-start">Início</Label>
            <Input id="offer-start" type="date" value={form.startDate} onChange={(event) => set('startDate', event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-respond">Responder até</Label>
            <Input
              id="offer-respond"
              type="date"
              value={form.respondBy}
              onChange={(event) => set('respondBy', event.target.value)}
            />
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="offer-benefit-notes">Benefícios (detalhes)</Label>
            <Input
              id="offer-benefit-notes"
              value={form.benefitsNotes}
              onChange={(event) => set('benefitsNotes', event.target.value)}
            />
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="offer-equity">Equity</Label>
            <Input id="offer-equity" value={form.equity} onChange={(event) => set('equity', event.target.value)} />
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="offer-notes">Notas</Label>
            <Textarea id="offer-notes" rows={2} value={form.notes} onChange={(event) => set('notes', event.target.value)} />
          </div>
        </form>
        <DialogFooter>
          <Button type="submit" form="offer-form" disabled={save.isPending}>
            Salvar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
