import type { OfferDecision, PayPeriod, PayRegime } from '@/api/interviews'

export const offerDecisionLabels: Record<OfferDecision, string> = {
  PENDENTE: 'Pendente',
  NEGOCIANDO: 'Negociando',
  ACEITA: 'Aceita',
  RECUSADA: 'Recusada',
}

export const payRegimeLabels: Record<PayRegime, string> = { CLT: 'CLT', PJ: 'PJ', CONTRACTOR: 'Contractor' }

export const payPeriodLabels: Record<PayPeriod, string> = { MENSAL: 'por mês', ANUAL: 'por ano', HORA: 'por hora' }

/** Etapas em que a preparação, o registro e as propostas fazem sentido. */
export const interviewStatuses = new Set(['ENTREVISTA', 'TESTE_TECNICO', 'OFERTA'])
