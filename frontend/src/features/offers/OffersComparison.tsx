import { Link } from 'react-router'
import { useOffers } from '@/api/interviews'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatBrl, formatDate } from '@/lib/format'
import { offerDecisionLabels } from '@/lib/interviewLabels'
import { bestOfferId, describeOffer } from '@/lib/offers'

/**
 * Todas as propostas na mesma régua: o equivalente anual em reais (CLT com 13,33 salários, FGTS e benefícios; PJ
 * líquido de impostos e custos; moeda estrangeira pela PTAX).
 */
export function OffersComparison() {
  const offers = useOffers()
  if (offers.isLoading) return <LoadingBlock lines={3} />
  if (offers.error) return <ErrorBlock error={offers.error} />
  const rows = offers.data ?? []
  if (rows.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Nenhuma proposta ainda. Registre a proposta na candidatura (aba Propostas) para comparar aqui.
      </p>
    )
  }
  const best = bestOfferId(rows)
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        Equivalente anual: CLT soma 13,33 salários, FGTS de 8%, benefícios e bônus; PJ desconta impostos e custos fixos.
        Moeda estrangeira pela PTAX do dia.
      </p>
      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Vaga</TableHead>
              <TableHead>Proposta</TableHead>
              <TableHead className="text-right">Equivalente anual</TableHead>
              <TableHead className="text-right">Por mês</TableHead>
              <TableHead>Responder até</TableHead>
              <TableHead>Situação</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((offer) => (
              <TableRow key={offer.id}>
                <TableCell>
                  <Link to={`/jobs/${offer.jobId}`} className="font-medium hover:underline">
                    {offer.title}
                  </Link>
                  <p className="text-xs text-muted-foreground">{offer.company}</p>
                </TableCell>
                <TableCell>{describeOffer(offer)}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {offer.annualEquivalent == null ? (
                    <span className="text-muted-foreground">sem câmbio</span>
                  ) : (
                    <span className={offer.id === best ? 'font-semibold' : undefined}>
                      {formatBrl(offer.annualEquivalent)}
                      {offer.id === best && rows.length > 1 && (
                        <Badge variant="secondary" className="ml-2">
                          maior
                        </Badge>
                      )}
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatBrl(offer.monthlyEquivalent)}</TableCell>
                <TableCell>{offer.respondBy ? formatDate(offer.respondBy) : '—'}</TableCell>
                <TableCell>{offerDecisionLabels[offer.decision]}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
