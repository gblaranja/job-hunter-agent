import { useFunnelBreakdown, type FunnelBreakdown, type FunnelGroup } from '@/api/queries'
import { StatTile } from '@/components/charts/StatTile'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatPercent, rate } from '@/lib/format'
import { channelLabels, sourceLabel, tailoringLabels, timeToApplyLabels } from '@/lib/labels'

/** Abaixo disso a taxa oscila demais para comparar grupos. */
export const MIN_GROUP = 5

export type ConversionRow = { key: string; group: string; applied: number; interview: number; offer?: number }

/** Candidaturas, entrevistas (e ofertas, quando houver) por grupo, com a taxa Aplicada → Entrevista. */
export function ConversionTable({ rows, groupLabel }: { rows: ConversionRow[]; groupLabel: string }) {
  if (rows.length === 0) return <p className="text-sm text-muted-foreground">Sem candidaturas ainda.</p>
  const withOffers = rows.some((row) => row.offer !== undefined)
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{groupLabel}</TableHead>
          <TableHead className="text-right">Aplicadas</TableHead>
          <TableHead className="text-right">Entrevistas</TableHead>
          {withOffers && <TableHead className="text-right">Ofertas</TableHead>}
          <TableHead className="text-right">Conversão</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.key}>
            <TableCell>{row.group}</TableCell>
            <TableCell className="text-right tabular-nums">{row.applied}</TableCell>
            <TableCell className="text-right tabular-nums">{row.interview}</TableCell>
            {withOffers && <TableCell className="text-right tabular-nums">{row.offer ?? 0}</TableCell>}
            <TableCell className="text-right tabular-nums">
              {row.applied < MIN_GROUP ? (
                <span className="text-muted-foreground">poucos dados</span>
              ) : (
                formatPercent(rate(row.interview, row.applied))
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function toRows(groups: FunnelGroup[], label: (group: string) => string): ConversionRow[] {
  return groups.map((group) => ({ key: group.group, group: label(group.group), applied: group.applied, interview: group.interview, offer: group.offer }))
}

/**
 * O funil aberto pelo que mais pesa na conversão, calculado na hora: de onde veio a vaga, por onde você aplicou,
 * quanto tempo levou e se o currículo foi adaptado. Serve de linha de base para ver se as entrevistas aumentam.
 */
export function ConversionSection() {
  const funnel = useFunnelBreakdown()
  if (funnel.isLoading) return <LoadingBlock lines={3} />
  if (funnel.error) return <ErrorBlock error={funnel.error} />
  if (!funnel.data) return null
  return <Conversion data={funnel.data} />
}

function Conversion({ data }: { data: FunnelBreakdown }) {
  const ranking = data.rankingQuality
  if (data.applied === 0 && ranking.evaluated === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Sem candidaturas ainda. Quando você aplicar, aqui aparece a conversão por fonte, canal e tempo até aplicar.
      </p>
    )
  }
  return (
    <section aria-label="Conversão das candidaturas" className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="Aplicada → entrevista"
          value={formatPercent(rate(data.interview, data.applied))}
          hint={`${data.interview} de ${data.applied} candidaturas`}
        />
        <StatTile
          label="Entrevista → oferta"
          value={formatPercent(rate(data.offer, data.interview))}
          hint={`${data.offer} de ${data.interview} entrevistas · ${data.accepted} ${data.accepted === 1 ? 'aceita' : 'aceitas'}`}
        />
        <StatTile
          label="Até o primeiro retorno"
          value={data.medianDaysToFirstResponse == null ? '—' : `${String(data.medianDaysToFirstResponse).replace('.', ',')} dias`}
          hint="mediana desde a candidatura"
        />
        <StatTile
          label="Acerto do ranking"
          value={formatPercent(rate(ranking.topPositives, ranking.topCount))}
          hint={
            ranking.topCount === 0
              ? 'avalie ou descarte vagas para medir'
              : `das ${ranking.topCount} vagas avaliadas de maior score, ${ranking.topPositives} eram as que você quis`
          }
        />
      </div>
      {/* grid-cols-1 (minmax(0, 1fr)): no celular a coluna não cresce até a largura das tabelas. */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <BreakdownCard title="Por fonte da vaga" groupLabel="Fonte" rows={toRows(data.bySource, sourceLabel)} />
        <BreakdownCard
          title="Por canal da candidatura"
          description="Indicação costuma converter muito mais que a candidatura espontânea."
          groupLabel="Canal"
          rows={toRows(data.byChannel, (group) => channelLabels[group as keyof typeof channelLabels] ?? 'Não informado')}
        />
        <BreakdownCard
          title="Por tempo até aplicar"
          description="Do momento em que a vaga apareceu no agente até a candidatura."
          groupLabel="Tempo"
          rows={toRows(data.byTimeToApply, (group) => timeToApplyLabels[group] ?? group)}
        />
        <BreakdownCard
          title="Currículo adaptado × padrão"
          groupLabel="Currículo"
          rows={toRows(data.byTailoring, (group) => tailoringLabels[group] ?? group)}
        />
      </div>
    </section>
  )
}

function BreakdownCard({ title, description, groupLabel, rows }: { title: string; description?: string; groupLabel: string; rows: ConversionRow[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent>
        <ConversionTable rows={rows} groupLabel={groupLabel} />
      </CardContent>
    </Card>
  )
}
