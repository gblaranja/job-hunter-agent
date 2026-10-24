import { toast } from 'sonner'
import { useAssignNiche, useClearNiche, useCompanyNiches, useNiches, type CompanyNiche, type NicheView } from '@/api/queries'
import { LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { fitBandLabels, nicheSourceLabels } from '@/lib/labels'

const NONE = 'NENHUM'
const OUTSIDE = 'FORA'

/** Nicho, subsegmento e fit da empresa em cada nicho ativo; a sua escolha prevalece sobre a automática. */
export function CompanyNicheCard({ companyId }: { companyId: number }) {
  const niches = useNiches()
  const assigned = useCompanyNiches(companyId)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Nichos</CardTitle>
        <CardDescription>
          Subsegmento vindo do Banco Central, da Receita, das palavras-chave das vagas ou da IA. Escolha outro para corrigir.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {(niches.isLoading || assigned.isLoading) && <LoadingBlock />}
        {niches.data
          ?.filter((niche) => niche.active)
          .map((niche) => (
            <NicheRow
              key={niche.id}
              companyId={companyId}
              niche={niche}
              current={assigned.data?.find((item) => item.nicheId === niche.id)}
            />
          ))}
      </CardContent>
    </Card>
  )
}

function NicheRow({ companyId, niche, current }: { companyId: number; niche: NicheView; current?: CompanyNiche }) {
  const assign = useAssignNiche()
  const clear = useClearNiche()
  const value = !current ? NONE : current.segmentId == null ? OUTSIDE : String(current.segmentId)

  function change(next: string) {
    if (next === NONE) return
    assign.mutate(
      { companyId, nicheId: niche.id, segmentId: next === OUTSIDE ? null : Number(next) },
      { onSuccess: () => toast.success('Classificação salva.'), onError: (error) => toast.error(error.message) },
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-56 flex-1 space-y-2">
          <Label htmlFor={`niche-${niche.id}`}>{niche.name}</Label>
          <Select value={value} onValueChange={change}>
            <SelectTrigger id={`niche-${niche.id}`} className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {!current && <SelectItem value={NONE}>Sem classificação</SelectItem>}
              {niche.segments.map((segment) => (
                <SelectItem key={segment.id} value={String(segment.id)}>
                  {segment.name}
                </SelectItem>
              ))}
              <SelectItem value={OUTSIDE}>Fora deste nicho</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {current?.source === 'MANUAL' && (
          <Button
            variant="ghost"
            size="sm"
            disabled={clear.isPending}
            onClick={() =>
              clear.mutate(
                { companyId, nicheId: niche.id },
                { onSuccess: () => toast.success('Voltou a classificação automática.'), onError: (error) => toast.error(error.message) },
              )
            }
          >
            Usar a automática
          </Button>
        )}
      </div>
      {current && (
        <p className="text-xs text-muted-foreground">
          {nicheSourceLabels[current.source]}
          {current.source === 'IA' && !current.verified ? ` · confiança ${Math.round(current.confidence * 100)}%, confirme escolhendo o subsegmento` : ''}
        </p>
      )}
      {current?.fitScore != null && current.fitBand && (
        <p className="text-sm">
          <span className="font-semibold tabular-nums">{current.fitScore}</span>{' '}
          <span className="text-muted-foreground">
            {fitBandLabels[current.fitBand]} · {current.reasons.join(' · ')}
          </span>
        </p>
      )}
    </div>
  )
}
