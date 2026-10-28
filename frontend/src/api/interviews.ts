import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, unwrap, type Schemas } from './client'

// Fase E: histórias STAR, preparação, registros pós-entrevista e propostas. Num arquivo próprio para não disputar
// o queries.ts com as outras frentes.

export type StarStory = Schemas['StarStoryView']
export type StarStoryCommand = Schemas['StarStoryCommand']
export type StarStoryPolish = Schemas['StarStoryPolish']
export type InterviewPrep = Schemas['InterviewPrepView']
export type InterviewPrepQuestion = Schemas['InterviewPrepQuestion']
export type InterviewDebrief = Schemas['InterviewDebriefView']
export type InterviewDebriefCommand = Schemas['InterviewDebriefCommand']
export type InterviewStage = InterviewDebriefCommand['stage']
export type Offer = Schemas['OfferView']
export type OfferCommand = Schemas['OfferCommand']
export type OfferDecision = Offer['decision']
export type PayRegime = Offer['regime']
export type PayPeriod = Offer['period']
export type NegotiationDraft = Schemas['NegotiationDraft']

export const interviewKeys = {
  stories: ['stories'] as const,
  preps: (applicationId: number) => ['interview-prep', applicationId] as const,
  debriefs: (applicationId: number) => ['debriefs', applicationId] as const,
  offers: ['offers'] as const,
  applicationOffers: (applicationId: number) => ['offers', applicationId] as const,
}

// Histórias STAR

export function useStories() {
  return useQuery({ queryKey: interviewKeys.stories, queryFn: () => unwrap(api.GET('/api/stories')) })
}

export function useSaveStory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id?: number; body: StarStoryCommand }) =>
      id === undefined
        ? unwrap(api.POST('/api/stories', { body }))
        : unwrap(api.PUT('/api/stories/{id}', { params: { path: { id } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: interviewKeys.stories }),
  })
}

export function useDeleteStory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/stories/{id}', { params: { path: { id } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: interviewKeys.stories }),
  })
}

/** Sugestão lapidada pela IA; nada é salvo até você aceitar. */
export function usePolishStory() {
  return useMutation({ mutationFn: (body: StarStoryCommand) => unwrap(api.POST('/api/stories/polish', { body })) })
}

// Preparação

export function useInterviewPreps(applicationId: number) {
  return useQuery({
    queryKey: interviewKeys.preps(applicationId),
    queryFn: () => unwrap(api.GET('/api/applications/{id}/interview-prep', { params: { path: { id: applicationId } } })),
    refetchInterval: (query) => (query.state.data?.some((prep) => prep.status === 'GERANDO') ? 3000 : false),
  })
}

export function useRequestInterviewPrep() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, stage }: { applicationId: number; stage?: InterviewStage }) =>
      unwrap(
        api.POST('/api/applications/{id}/interview-prep', {
          params: { path: { id: applicationId }, query: { stage } },
        }),
      ),
    onSuccess: (_data, { applicationId }) => client.invalidateQueries({ queryKey: interviewKeys.preps(applicationId) }),
  })
}

// Registros pós-entrevista

export function useDebriefs(applicationId: number) {
  return useQuery({
    queryKey: interviewKeys.debriefs(applicationId),
    queryFn: () => unwrap(api.GET('/api/applications/{id}/debriefs', { params: { path: { id: applicationId } } })),
  })
}

export function useCreateDebrief() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, body }: { applicationId: number; body: InterviewDebriefCommand }) =>
      unwrap(api.POST('/api/applications/{id}/debriefs', { params: { path: { id: applicationId } }, body })),
    // O registro agenda o follow-up: a candidatura (kanban, vaga) também muda.
    onSuccess: (_data, { applicationId }) =>
      Promise.all(
        [interviewKeys.debriefs(applicationId), ['board'], ['job'], ['today']].map((queryKey) =>
          client.invalidateQueries({ queryKey }),
        ),
      ),
  })
}

export function useDeleteDebrief() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: number; applicationId: number }) =>
      unwrap(api.DELETE('/api/debriefs/{id}', { params: { path: { id } } })),
    onSuccess: (_data, { applicationId }) => client.invalidateQueries({ queryKey: interviewKeys.debriefs(applicationId) }),
  })
}

// Propostas

export function useOffers() {
  return useQuery({ queryKey: interviewKeys.offers, queryFn: () => unwrap(api.GET('/api/offers')) })
}

export function useApplicationOffers(applicationId: number) {
  return useQuery({
    queryKey: interviewKeys.applicationOffers(applicationId),
    queryFn: () =>
      unwrap(api.GET('/api/applications/{applicationId}/offers', { params: { path: { applicationId } } })),
  })
}

export function useSaveOffer() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, applicationId, body }: { id?: number; applicationId: number; body: OfferCommand }) =>
      id === undefined
        ? unwrap(api.POST('/api/applications/{applicationId}/offers', { params: { path: { applicationId } }, body }))
        : unwrap(api.PUT('/api/offers/{id}', { params: { path: { id } }, body })),
    // 'funnel': a proposta aceita entra no funil do Insights.
    onSuccess: () =>
      Promise.all([interviewKeys.offers, ['funnel']].map((queryKey) => client.invalidateQueries({ queryKey }))),
  })
}

export function useDeleteOffer() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/offers/{id}', { params: { path: { id } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: interviewKeys.offers }),
  })
}

export function useNegotiationDraft() {
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Schemas['NegotiationRequest']) =>
      unwrap(api.POST('/api/offers/{id}/negotiation-draft', { params: { path: { id } }, body })),
  })
}
