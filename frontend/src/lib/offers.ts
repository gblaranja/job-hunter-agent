import type { Offer } from '@/api/interviews'
import { formatMoney } from './format'
import { payPeriodLabels, payRegimeLabels } from './interviewLabels'

/** "PJ · R$ 15.000 por mês". */
export function describeOffer(offer: Offer): string {
  return `${payRegimeLabels[offer.regime]} · ${formatMoney(offer.baseSalary, offer.currency)} ${payPeriodLabels[offer.period]}`
}

/** A proposta de maior equivalente anual entre as que ainda estão em aberto ou foram aceitas. */
export function bestOfferId(offers: Offer[]): number | undefined {
  return offers
    .filter((offer) => offer.decision !== 'RECUSADA' && offer.annualEquivalent != null)
    .reduce<Offer | undefined>((best, offer) => (!best || offer.annualEquivalent! > best.annualEquivalent! ? offer : best), undefined)
    ?.id
}
