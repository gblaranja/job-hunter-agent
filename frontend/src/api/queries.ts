import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api, ApiError, send, unwrap, type Schemas } from './client'

export type { Schemas }

export type ApplicationStatus = Schemas['BoardCard']['status']
export type ApplicationChannel = NonNullable<Schemas['StartApplication']['channel']>
export type WorkModel = Schemas['JobView']['workModel']
export type LinkStatus = Schemas['AppliedJobSnapshot']['linkStatus']
export type Board = Schemas['Board']
export type BoardCard = Schemas['BoardCard']
export type AppliedJob = Schemas['AppliedJob']
export type AppliedListItem = Schemas['AppliedListItem']
export type JobDetail = Schemas['JobDetail']
export type ResumeItem = Schemas['ResumeItem']
export type DownloadLink = Schemas['DownloadLink']
export type NextActionKind = NonNullable<Schemas['BoardCard']['nextActionKind']>

export const queryKeys = {
  me: ['me'] as const,
  board: ['board'] as const,
  applications: (status?: ApplicationStatus, q?: string) => ['applications', { status, q }] as const,
  applied: (applicationId: number) => ['applied', applicationId] as const,
  job: (jobId: number) => ['job', jobId] as const,
  resumes: ['resumes'] as const,
  tailored: (jobId: number) => ['tailored-resumes', jobId] as const,
}

/** Tudo o que depende das candidaturas é recarregado depois de qualquer mudança no funil. */
function invalidateTracking(client: QueryClient) {
  return Promise.all(
    // 'jobs': a vaga que vira candidatura sai da caixa de vagas
    ['board', 'applications', 'applied', 'job', 'jobs', 'resumes', 'funnel'].map((key) =>
      client.invalidateQueries({ queryKey: [key] }),
    ),
  )
}

// Sessão

export function useCurrentUser() {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: async () => {
      try {
        return await unwrap(api.GET('/api/auth/me'))
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    staleTime: 5 * 60 * 1000,
  })
}

/** Formas de login habilitadas na instalação (o botão do Google só aparece se estiver configurado). */
export function useLoginProviders() {
  return useQuery({
    queryKey: ['auth-providers'],
    queryFn: () => unwrap(api.GET('/api/auth/providers')),
    staleTime: Infinity,
  })
}

export function useLogin() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (credentials: { username: string; password: string }) => {
      const response = await send('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(credentials),
      })
      return (await response.json()) as Schemas['CurrentUser']
    },
    onSuccess: (user) => client.setQueryData(queryKeys.me, user),
  })
}

export function useLogout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => send('/api/auth/logout', { method: 'POST' }),
    onSettled: () => {
      client.clear()
      client.setQueryData(queryKeys.me, null)
    },
  })
}

// Candidaturas

export function useBoard() {
  return useQuery({ queryKey: queryKeys.board, queryFn: () => unwrap(api.GET('/api/applications/board')) })
}

export function useApplications(status?: ApplicationStatus, q?: string) {
  return useQuery({
    queryKey: queryKeys.applications(status, q),
    queryFn: () =>
      unwrap(api.GET('/api/applications', { params: { query: { status, q: q || undefined } } })),
    placeholderData: (previous) => previous,
  })
}

export function useAppliedJob(applicationId: number | undefined, enabled = true) {
  return useQuery({
    queryKey: queryKeys.applied(applicationId ?? -1),
    queryFn: () =>
      unwrap(api.GET('/api/applications/{id}/snapshot', { params: { path: { id: applicationId! } } })),
    enabled: enabled && applicationId !== undefined,
  })
}

export function useJob(jobId: number) {
  return useQuery({
    queryKey: queryKeys.job(jobId),
    queryFn: () => unwrap(api.GET('/api/jobs/{id}', { params: { path: { id: jobId } } })),
  })
}

export function useCreateJob() {
  return useMutation({
    mutationFn: (body: Schemas['ManualJobCommand']) => unwrap(api.POST('/api/jobs', { body })),
  })
}

export type JobImportPreview = Schemas['JobImportPreview']

/** Lê a página da vaga pelo link e devolve os campos do cadastro, sem criar nada. */
export function useJobImportPreview() {
  return useMutation({
    mutationFn: (url: string) => unwrap(api.POST('/api/jobs/import/preview', { body: { url } })),
  })
}

export function useStartApplication() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ jobId, body }: { jobId: number; body: Schemas['StartApplication'] }) =>
      unwrap(api.POST('/api/jobs/{jobId}/application', { params: { path: { jobId } }, body })),
    onSuccess: () => invalidateTracking(client),
  })
}

type ChangeStatusInput = { applicationId: number } & Schemas['ChangeApplicationStatus']

/** Move o card na hora (atualização otimista) e desfaz se o backend recusar. */
export function useChangeStatus() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, ...body }: ChangeStatusInput) =>
      unwrap(api.PATCH('/api/applications/{id}/status', { params: { path: { id: applicationId } }, body })),
    onMutate: async ({ applicationId, status }) => {
      await client.cancelQueries({ queryKey: queryKeys.board })
      const previous = client.getQueryData<Board>(queryKeys.board)
      if (previous) client.setQueryData(queryKeys.board, moveCard(previous, applicationId, status))
      return { previous }
    },
    onError: (_error, _input, context) => {
      if (context?.previous) client.setQueryData(queryKeys.board, context.previous)
    },
    onSettled: () => invalidateTracking(client),
  })
}

export function moveCard(board: Board, applicationId: number, status: ApplicationStatus): Board {
  const card = board.columns.flatMap((column) => column.cards).find((c) => c.applicationId === applicationId)
  if (!card) return board
  return {
    columns: board.columns.map((column) => {
      const cards = column.cards.filter((c) => c.applicationId !== applicationId)
      return column.status === status
        ? { ...column, cards: [{ ...card, status, daysInStatus: 0 }, ...cards] }
        : { ...column, cards }
    }),
  }
}

export function useChangeResume() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, resumeVersionId }: { applicationId: number; resumeVersionId: number }) =>
      unwrap(
        api.PATCH('/api/applications/{id}/resume', {
          params: { path: { id: applicationId } },
          body: { resumeVersionId },
        }),
      ),
    onSuccess: () => invalidateTracking(client),
  })
}

export function useUpdateApplicationDetails() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, ...body }: { applicationId: number } & Schemas['UpdateApplicationDetails']) =>
      unwrap(api.PATCH('/api/applications/{id}', { params: { path: { id: applicationId } }, body })),
    onSuccess: () => invalidateTracking(client),
  })
}

/** Você mandou um follow-up: conta o envio e o backend agenda o próximo (até o máximo). */
export function useRegisterFollowUp() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (applicationId: number) =>
      unwrap(api.POST('/api/applications/{id}/follow-ups', { params: { path: { id: applicationId } } })),
    onSuccess: () => invalidateTracking(client),
  })
}

export function useSaveSnapshot() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, ...body }: { applicationId: number } & Schemas['SaveJobSnapshot']) =>
      unwrap(api.PUT('/api/applications/{id}/snapshot', { params: { path: { id: applicationId } }, body })),
    onSuccess: () => invalidateTracking(client),
  })
}

export function useAttachPagePdf() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, file }: { applicationId: number; file: File }) => {
      const form = new FormData()
      form.append('file', file)
      return send(`/api/applications/${applicationId}/snapshot/pdf`, { method: 'POST', body: form })
    },
    onSuccess: () => invalidateTracking(client),
  })
}

export function fetchPagePdfLink(applicationId: number): Promise<DownloadLink> {
  return unwrap(api.GET('/api/applications/{id}/snapshot/pdf', { params: { path: { id: applicationId } } }))
}

// Currículos

export function useResumes() {
  return useQuery({ queryKey: queryKeys.resumes, queryFn: () => unwrap(api.GET('/api/resumes')) })
}

export function useUploadResume() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ file, label, targetRole }: { file: File; label?: string; targetRole?: string }) => {
      const form = new FormData()
      form.append('file', file)
      if (label) form.append('label', label)
      if (targetRole) form.append('targetRole', targetRole)
      const response = await send('/api/resumes', { method: 'POST', body: form })
      return (await response.json()) as ResumeItem
    },
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.resumes }),
  })
}

export function useUpdateResume() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Schemas['UpdateResumeCommand']) =>
      unwrap(api.PATCH('/api/resumes/{id}', { params: { path: { id } }, body })),
    onSuccess: () => invalidateTracking(client),
  })
}

export function fetchResumeLink(id: number): Promise<DownloadLink> {
  return unwrap(api.GET('/api/resumes/{id}/file', { params: { path: { id } } }))
}

// Caixa de vagas e feedback

export type JobListItem = Schemas['JobListItem']
export type FeedbackRating = NonNullable<Schemas['JobListItem']['rating']>
export type FeedbackReason = Schemas['JobListItem']['reasons'][number]

export type JobScope = Schemas['JobListItem']['scope']
export type JobFamily = NonNullable<Schemas['JobListItem']['jobFamily']>
export type InternationalEligibility = NonNullable<Schemas['JobListItem']['internationalEligibility']>

type InboxFilter = {
  q?: string
  scope: JobScope
  /** Só as vagas desta família de cargo. */
  family?: JobFamily
  /** Esconde as vagas do exterior restritas a outro país. */
  eligibleFromBrazil?: boolean
}

export function useInboxJobs({
  q,
  scope,
  page,
  size,
  family,
  eligibleFromBrazil,
  allLegitimacy = false,
  hasConnection = false,
  salaryFloor = false,
}: InboxFilter & {
  page: number
  size: number
  /** mostra também as vagas de legitimidade baixa e suspeitas */
  allLegitimacy?: boolean
  /** só vagas de empresas onde você tem contato ou conexão do LinkedIn */
  hasConnection?: boolean
  /** esconde as vagas que pagam menos que a sua pretensão (equivalente anual) */
  salaryFloor?: boolean
}) {
  return useQuery({
    queryKey: ['jobs', 'inbox', { q, scope, page, size, family, eligibleFromBrazil, allLegitimacy, hasConnection, salaryFloor }],
    queryFn: () =>
      unwrap(
        api.GET('/api/jobs', {
          params: {
            query: {
              inbox: true,
              q: q || undefined,
              scope,
              page,
              size,
              family,
              eligibleFromBrazil,
              allLegitimacy: allLegitimacy || undefined,
              hasConnection: hasConnection || undefined,
              salaryFloor: salaryFloor || undefined,
            },
          },
        }),
      ),
    placeholderData: (previous) => previous,
  })
}

/** Só o total de cada seção da caixa, para o contador da aba que não está aberta. */
export function useInboxCount({ q, scope, family, eligibleFromBrazil }: InboxFilter) {
  return useQuery({
    queryKey: ['jobs', 'inbox', 'count', { q, scope, family, eligibleFromBrazil }],
    queryFn: () =>
      unwrap(
        api.GET('/api/jobs', {
          params: { query: { inbox: true, q: q || undefined, scope, family, eligibleFromBrazil, size: 1 } },
        }),
      ),
    select: (data) => data.total,
    placeholderData: (previous) => previous,
  })
}

/** Separa a vaga da publicação canônica: ela volta para a caixa como uma vaga própria. */
export function useSeparateDuplicate() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (jobId: number) => unwrap(api.DELETE('/api/jobs/{id}/duplicate', { params: { path: { id: jobId } } })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['jobs'] }), client.invalidateQueries({ queryKey: ['job'] })]),
  })
}

export function useChangeJobScope() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ jobId, scope }: { jobId: number; scope: JobScope }) =>
      unwrap(api.PUT('/api/jobs/{id}/scope', { params: { path: { id: jobId } }, body: { scope } })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['jobs'] }), client.invalidateQueries({ queryKey: ['job'] })]),
  })
}

export function useSaveFeedback() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ jobId, ...body }: { jobId: number } & Schemas['Feedback']) =>
      unwrap(api.PUT('/api/jobs/{id}/feedback', { params: { path: { id: jobId } }, body })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['jobs'] }), client.invalidateQueries({ queryKey: ['job'] })]),
  })
}

export type DismissReason = Schemas['Dismissal']['reason']

/** "Não tenho interesse" ou "não tenho o perfil técnico": a vaga sai da caixa. */
export function useDismissJob() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ jobId, ...body }: { jobId: number } & Schemas['Dismissal']) =>
      unwrap(api.PUT('/api/jobs/{id}/dismissal', { params: { path: { id: jobId } }, body })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['jobs'] }), client.invalidateQueries({ queryKey: ['job'] })]),
  })
}

/** Desfaz o descarte: a vaga volta para a caixa. */
export function useRestoreJob() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (jobId: number) => unwrap(api.DELETE('/api/jobs/{id}/dismissal', { params: { path: { id: jobId } } })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['jobs'] }), client.invalidateQueries({ queryKey: ['job'] })]),
  })
}

// Empresas

export type CompanyItem = Schemas['CompanyItem']
export type WatchStatus = CompanyItem['watchStatus']

export function useCompanies({ gptw = false }: { gptw?: boolean } = {}) {
  return useQuery({
    queryKey: ['companies', { gptw }],
    queryFn: () => unwrap(api.GET('/api/companies', { params: { query: { gptw: gptw || undefined } } })),
  })
}

/** Só o acompanhamento ("Marcar como alvo"), sem reenviar os outros dados da empresa. */
export function useChangeWatchStatus() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, watchStatus }: { id: number; watchStatus: WatchStatus }) =>
      unwrap(api.PUT('/api/companies/{id}/watch-status', { params: { path: { id } }, body: { watchStatus } })),
    onSuccess: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: ['companies'] }),
        client.invalidateQueries({ queryKey: ['niches'] }),
      ]),
  })
}

export function useUpdateCompany() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Schemas['UpdateCompanyCommand']) =>
      unwrap(api.PATCH('/api/companies/{id}', { params: { path: { id } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['companies'] }),
  })
}

export type CompanyAtsCheck = Schemas['CompanyAtsCheck']

/** Detecta o ATS pelo link da página de carreira e confere o slug, sem gravar. */
export function useCheckCompanyAts() {
  return useMutation({
    mutationFn: (body: Schemas['CompanyAtsCheckRequest']) => unwrap(api.POST('/api/companies/ats-check', { body })),
  })
}

export type CompanyOverview = Schemas['CompanyOverview']
export type WeeklyMetrics = Schemas['WeeklyMetrics']

export function useCompany(id: number) {
  return useQuery({
    queryKey: ['companies', id],
    queryFn: () => unwrap(api.GET('/api/companies/{id}', { params: { path: { id } } })),
  })
}

export function useCompanyOverview(id: number) {
  return useQuery({
    queryKey: ['companies', id, 'overview'],
    queryFn: () => unwrap(api.GET('/api/companies/{id}/overview', { params: { path: { id } } })),
  })
}

export function useCompanyMetrics(id: number) {
  return useQuery({
    queryKey: ['companies', id, 'metrics'],
    queryFn: () => unwrap(api.GET('/api/companies/{id}/metrics', { params: { path: { id } } })),
  })
}

export function useSaveHeadcount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, headcount }: { id: number; headcount: number }) =>
      unwrap(api.PUT('/api/companies/{id}/headcount', { params: { path: { id } }, body: { headcount } })),
    onSuccess: (_data, { id }) => client.invalidateQueries({ queryKey: ['companies', id, 'metrics'] }),
  })
}

export function useRefreshMetrics() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/companies/refresh-metrics')),
    onSuccess: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: ['companies'] }),
        client.invalidateQueries({ queryKey: ['notifications'] }),
      ]),
  })
}

export function useMergeCompany() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetId }: { id: number; targetId: number }) =>
      unwrap(api.POST('/api/companies/{id}/merge', { params: { path: { id } }, body: { targetId } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['companies'] }),
  })
}

// Selos GPTW

export type RecognitionView = Schemas['RecognitionView']
export type RecognitionLevel = RecognitionView['level']
export type ImportPreview = Schemas['ImportPreview']
export type ImportItem = Schemas['ImportItem']
export type ImportConfirmation = Schemas['ImportConfirmation']

/** Tudo o que mostra o selo é recarregado depois de uma revisão ou importação. */
function invalidateRecognitions(client: QueryClient) {
  return Promise.all(
    ['recognitions', 'companies', 'niches', 'jobs'].map((key) => client.invalidateQueries({ queryKey: [key] })),
  )
}

export function useRecognitionStatus() {
  return useQuery({
    queryKey: ['recognitions', 'status'],
    queryFn: () => unwrap(api.GET('/api/recognitions/status')),
  })
}

export function useRecognitionReview(enabled = true) {
  return useQuery({
    queryKey: ['recognitions', 'review'],
    queryFn: () => unwrap(api.GET('/api/recognitions/review')),
    enabled,
  })
}

export function useConfirmRecognition() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.POST('/api/recognitions/{id}/confirm', { params: { path: { id } } })),
    onSuccess: () => invalidateRecognitions(client),
  })
}

export function useRejectRecognition() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.POST('/api/recognitions/{id}/reject', { params: { path: { id } } })),
    onSuccess: () => invalidateRecognitions(client),
  })
}

export function useCompanyRecognitions(companyId: number) {
  return useQuery({
    queryKey: ['recognitions', 'company', companyId],
    queryFn: () => unwrap(api.GET('/api/companies/{companyId}/recognitions', { params: { path: { companyId } } })),
  })
}

export function useCheckRecognition() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (companyId: number) =>
      unwrap(api.POST('/api/companies/{companyId}/recognitions/check', { params: { path: { companyId } } })),
    onSuccess: () => invalidateRecognitions(client),
  })
}

export function useImportPreview() {
  return useMutation({
    mutationFn: (body: Schemas['ImportSource']) => unwrap(api.POST('/api/recognitions/import', { body })),
  })
}

export function useConfirmImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: ImportConfirmation) => unwrap(api.POST('/api/recognitions/import/confirm', { body })),
    onSuccess: () => invalidateRecognitions(client),
  })
}

// Nichos

export type NicheView = Schemas['NicheView']
export type SegmentView = Schemas['SegmentView']
export type NicheCompany = Schemas['NicheCompany']
export type FitBand = NonNullable<NicheCompany['fitBand']>
export type NicheSource = NicheCompany['source']
export type CompanyNiche = Schemas['CompanyNiche']
export type FitWeights = Schemas['FitWeights']

export type NicheFilters = {
  segment?: number
  gptw?: boolean
  hasOpenJobs?: boolean
  neverApplied?: boolean
  financialClients?: boolean
  q?: string
  sort?: 'fit' | 'jobs' | 'name'
  page: number
  size: number
}

export function useNiches() {
  return useQuery({ queryKey: ['niches'], queryFn: () => unwrap(api.GET('/api/niches')) })
}

export function useNicheCompanies(nicheId: number | undefined, filters: NicheFilters) {
  return useQuery({
    queryKey: ['niches', nicheId, 'companies', filters],
    queryFn: () =>
      unwrap(
        api.GET('/api/niches/{id}/companies', {
          params: {
            path: { id: nicheId! },
            query: {
              segment: filters.segment,
              gptw: filters.gptw || undefined,
              hasOpenJobs: filters.hasOpenJobs || undefined,
              neverApplied: filters.neverApplied || undefined,
              financialClients: filters.financialClients || undefined,
              q: filters.q || undefined,
              sort: filters.sort,
              page: filters.page,
              size: filters.size,
            },
          },
        }),
      ),
    enabled: nicheId !== undefined,
    placeholderData: (previous) => previous,
  })
}

export function useUpdateNiche() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Schemas['UpdateNiche']) =>
      unwrap(api.PATCH('/api/niches/{id}', { params: { path: { id } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['niches'] }),
  })
}

export function useUpdateSegment() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ nicheId, segmentId, ...body }: { nicheId: number; segmentId: number } & Schemas['UpdateSegment']) =>
      unwrap(
        api.PATCH('/api/niches/{id}/segments/{segmentId}', { params: { path: { id: nicheId, segmentId } }, body }),
      ),
    onSuccess: () => client.invalidateQueries({ queryKey: ['niches'] }),
  })
}

export function useRecomputeNiches() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/niches/recompute')),
    onSuccess: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: ['niches'] }),
        client.invalidateQueries({ queryKey: ['companies'] }),
      ]),
  })
}

export function useCompanyNiches(companyId: number) {
  return useQuery({
    queryKey: ['niches', 'company', companyId],
    queryFn: () => unwrap(api.GET('/api/companies/{companyId}/niches', { params: { path: { companyId } } })),
  })
}

export function useAssignNiche() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ companyId, ...body }: { companyId: number } & Schemas['NicheAssignment']) =>
      unwrap(api.PUT('/api/companies/{companyId}/niche', { params: { path: { companyId } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['niches'] }),
  })
}

export function useClearNiche() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ companyId, nicheId }: { companyId: number; nicheId: number }) =>
      unwrap(api.DELETE('/api/companies/{companyId}/niches/{nicheId}', { params: { path: { companyId, nicheId } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['niches'] }),
  })
}

// Bases públicas de empresas

export type SeedSource = Schemas['SeedSourceView']

export function useSeedSources() {
  return useQuery({ queryKey: ['seeds'], queryFn: () => unwrap(api.GET('/api/admin/seeds')) })
}

export function useRunSeed() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (source: string) => unwrap(api.POST('/api/admin/seeds/{source}/run', { params: { path: { source } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['seeds'] }),
  })
}

// Configurações: Gmail, coleta e perfis de busca

export type MailboxStatus = Schemas['MailboxStatus']
export type CollectionRun = Schemas['CollectionRun']
export type SearchProfile = Schemas['SearchProfileView']

export function useGmailStatus() {
  return useQuery({ queryKey: ['gmail'], queryFn: () => unwrap(api.GET('/api/admin/gmail/status')) })
}

export function useDisconnectGmail() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/admin/gmail')),
    onSuccess: () => client.invalidateQueries({ queryKey: ['gmail'] }),
  })
}

export function useCollectionRuns() {
  return useQuery({ queryKey: ['collection-runs'], queryFn: () => unwrap(api.GET('/api/admin/collection-runs')) })
}

export function useCollectNow() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/admin/collect')),
    onSettled: () =>
      Promise.all(
        ['collection-runs', 'source-health', 'jobs', 'companies', 'gmail'].map((key) =>
          client.invalidateQueries({ queryKey: [key] }),
        ),
      ),
  })
}

export type SourceHealth = Schemas['HealthReport']
export type SourceStatus = Schemas['SourceStatus']

/** Última vaga nova e erros de cada fonte, e os alertas de vaga que não viraram vaga. */
export function useSourceHealth() {
  return useQuery({ queryKey: ['source-health'], queryFn: () => unwrap(api.GET('/api/admin/sources/health')) })
}

export function useSearchProfiles() {
  return useQuery({ queryKey: ['search-profiles'], queryFn: () => unwrap(api.GET('/api/search-profiles')) })
}

export function useSaveSearchProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id?: number } & Schemas['SearchProfileCommand']) =>
      id === undefined
        ? unwrap(api.POST('/api/search-profiles', { body }))
        : unwrap(api.PUT('/api/search-profiles/{id}', { params: { path: { id } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['search-profiles'] }),
  })
}

export function useDeleteSearchProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/search-profiles/{id}', { params: { path: { id } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['search-profiles'] }),
  })
}

// Dados da triagem (eliminatórios, pretensão e meta semanal)

export type CandidateProfile = Schemas['CandidateProfileView']
export type CandidateProfileCommand = Schemas['CandidateProfileCommand']

export function useCandidateProfile() {
  return useQuery({ queryKey: ['candidate-profile'], queryFn: () => unwrap(api.GET('/api/candidate-profile')) })
}

/** Salva e recarrega as vagas: os eliminatórios e o score são recalculados no backend. */
export function useSaveCandidateProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: CandidateProfileCommand) => unwrap(api.PUT('/api/candidate-profile', { body })),
    onSuccess: (profile) => {
      client.setQueryData(['candidate-profile'], profile)
      return Promise.all(['jobs', 'job'].map((key) => client.invalidateQueries({ queryKey: [key] })))
    },
  })
}

// Análise

export type JobAnalysis = Schemas['JobAnalysis']

export function useReanalyze() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (jobId: number) => unwrap(api.POST('/api/jobs/{id}/analysis', { params: { path: { id: jobId } } })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['job'] }), client.invalidateQueries({ queryKey: ['jobs'] })]),
  })
}

// Respostas do formulário de candidatura (rascunhos; nada é enviado)

export type ScreeningAnswers = Schemas['ScreeningAnswersView']

export function useScreeningAnswers(jobId: number) {
  return useMutation({
    mutationFn: (body: { questions: string[]; coverLetter: boolean }) =>
      unwrap(api.POST('/api/jobs/{id}/screening-answers', { params: { path: { id: jobId } }, body })),
  })
}

// Currículos adaptados por vaga

export type TailoredResume = Schemas['TailoredResumeView']
export type ResumeChange = Schemas['ResumeChange']

/** Se a instalação tem a Claude API configurada para adaptar currículos. */
export function useTailoringAvailability() {
  return useQuery({
    queryKey: ['tailored-resumes', 'availability'],
    queryFn: () => unwrap(api.GET('/api/tailored-resumes/availability')),
    staleTime: 5 * 60 * 1000,
  })
}

/** Versões adaptadas da vaga; enquanto uma estiver sendo gerada, consulta de novo a cada 3 segundos. */
export function useTailoredResumes(jobId: number) {
  return useQuery({
    queryKey: queryKeys.tailored(jobId),
    queryFn: () => unwrap(api.GET('/api/jobs/{jobId}/tailored-resumes', { params: { path: { jobId } } })),
    refetchInterval: (query) => (query.state.data?.some((version) => version.status === 'GERANDO') ? 3000 : false),
  })
}

export function useRequestTailoredResume() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ jobId, baseResumeId }: { jobId: number; baseResumeId?: number }) =>
      unwrap(
        api.POST('/api/jobs/{jobId}/tailored-resumes', {
          params: { path: { jobId } },
          body: { baseResumeId: baseResumeId ?? null },
        }),
      ),
    onSuccess: (_created, { jobId }) => client.invalidateQueries({ queryKey: queryKeys.tailored(jobId) }),
  })
}

export function useEditTailoredResume() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) =>
      unwrap(api.PUT('/api/tailored-resumes/{id}/content', { params: { path: { id } }, body: { content } })),
    onSuccess: (updated) => client.invalidateQueries({ queryKey: queryKeys.tailored(updated.jobId) }),
  })
}

/** Gera o DOCX e salva como versão do currículo, para escolher ao registrar a aplicação. */
export function useSaveTailoredResume() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: number; jobId: number }) =>
      unwrap(api.POST('/api/tailored-resumes/{id}/resume-version', { params: { path: { id } } })),
    onSuccess: (_saved, { jobId }) =>
      Promise.all([
        client.invalidateQueries({ queryKey: queryKeys.tailored(jobId) }),
        client.invalidateQueries({ queryKey: queryKeys.resumes }),
      ]),
  })
}

/** Baixa o DOCX gerado do texto adaptado. */
export async function downloadTailoredDocx(id: number): Promise<void> {
  const response = await send(`/api/tailored-resumes/${id}/docx`, { method: 'GET' })
  const fileName = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') ?? '')?.[1] ?? 'curriculo.docx'
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

// Retornos

export type EmailItem = Schemas['EmailItem']
export type EmailClassification = NonNullable<EmailItem['aiClassification']>
export type ResponseType = Schemas['RecordResponse']['responseType']
export type ResponseChannel = Schemas['RecordResponse']['channel']

export function useEmails(pending: boolean) {
  return useQuery({
    queryKey: ['emails', { pending }],
    queryFn: () => unwrap(api.GET('/api/emails', { params: { query: { pending } } })),
  })
}

export function useEmailAccuracy() {
  return useQuery({ queryKey: ['emails', 'accuracy'], queryFn: () => unwrap(api.GET('/api/emails/accuracy')) })
}

export function useReviewEmail() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Schemas['Review']) =>
      unwrap(api.PATCH('/api/emails/{id}/classification', { params: { path: { id } }, body })),
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ['emails'] }), invalidateTracking(client)]),
  })
}

export function useRecordResponse() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, ...body }: { applicationId: number } & Schemas['RecordResponse']) =>
      unwrap(api.POST('/api/applications/{id}/responses', { params: { path: { id: applicationId } }, body })),
    onSuccess: () => invalidateTracking(client),
  })
}

export function useVerifyLinks() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/admin/verify-links')),
    onSuccess: () => invalidateTracking(client),
  })
}

// Insights

export type InsightReport = Schemas['InsightReport']
export type LlmUsage = Schemas['LlmUsage']

/** Último relatório; null enquanto nenhum foi gerado (a API responde 204). */
export function useLatestInsights() {
  return useQuery({
    queryKey: ['insights'],
    queryFn: async () => (await unwrap(api.GET('/api/insights/latest'))) ?? null,
  })
}

export function useGenerateInsights() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/insights/generate')),
    onSuccess: (report) => {
      client.setQueryData(['insights'], report)
      return client.invalidateQueries({ queryKey: ['llm-usage'] })
    },
  })
}

export type FunnelBreakdown = Schemas['FunnelBreakdown']
export type FunnelGroup = Schemas['FunnelGroup']

/** Funil por fonte, canal, tempo até aplicar e adaptação, calculado na hora pelo backend (sem a Claude API). */
export function useFunnelBreakdown() {
  return useQuery({ queryKey: ['funnel'], queryFn: () => unwrap(api.GET('/api/insights/funnel')) })
}

export function useLlmUsage() {
  return useQuery({ queryKey: ['llm-usage'], queryFn: () => unwrap(api.GET('/api/insights/usage')) })
}

export type LlmBudgetStatus = Schemas['LlmBudgetStatus']

/** Gasto do mês por tarefa contra o orçamento; a tarefa esgotada fica no determinístico. */
export function useLlmBudget() {
  return useQuery({ queryKey: ['llm-budget'], queryFn: () => unwrap(api.GET('/api/insights/budget')) })
}

// Dicionário de skills

export type SkillSuggestion = Schemas['SkillSuggestion']
export type ReviewedSkill = Schemas['ReviewedSkill']
export type EscoSkill = Schemas['EscoSkill']

export function useSkillSuggestions() {
  return useQuery({ queryKey: ['skills', 'suggestions'], queryFn: () => unwrap(api.GET('/api/skills/suggestions')) })
}

export function useReviewedSkills() {
  return useQuery({ queryKey: ['skills', 'reviewed'], queryFn: () => unwrap(api.GET('/api/skills/reviewed')) })
}

export function useAcceptSkill() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: Schemas['SkillAcceptance']) => unwrap(api.POST('/api/skills/reviewed', { body })),
    // a skill aceita repontua as vagas que a pedem
    onSuccess: () => Promise.all(['skills', 'jobs', 'job'].map((key) => client.invalidateQueries({ queryKey: [key] }))),
  })
}

export function useIgnoreSkill() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => unwrap(api.POST('/api/skills/ignored', { body: { name } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['skills'] }),
  })
}

export function useRemoveSkill() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/skills/reviewed/{id}', { params: { path: { id } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['skills'] }),
  })
}

/** Busca a skill na ESCO (URI e rótulos em português e inglês). */
export function useEscoSearch() {
  return useMutation({
    mutationFn: (text: string) => unwrap(api.GET('/api/skills/esco', { params: { query: { text } } })),
  })
}

// Notificações

export type NotificationItem = Schemas['NotificationView']

export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => unwrap(api.GET('/api/notifications')),
    refetchInterval: 60_000,
  })
}

export function useMarkNotificationRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.PATCH('/api/notifications/{id}/read', { params: { path: { id } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

export function useMarkAllNotificationsRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/notifications/read-all')),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

// Contatos e conexões

export type ContactView = Schemas['ContactView']
export type ContactRole = ContactView['role']
export type ContactInteractionView = Schemas['ContactInteractionView']
export type InteractionChannel = ContactInteractionView['channel']
export type InteractionDirection = ContactInteractionView['direction']
export type InteractionKind = ContactInteractionView['kind']
export type ApplicationContacts = Schemas['ApplicationContacts']

/** Tudo o que mostra contatos ou conversas é recarregado depois de qualquer mudança neles. */
function invalidateContacts(client: QueryClient) {
  return Promise.all(['contacts', 'jobs'].map((key) => client.invalidateQueries({ queryKey: [key] })))
}

export function useContacts({
  q,
  role,
  companyId,
  page,
  size,
}: {
  q?: string
  role?: ContactRole
  companyId?: number
  page: number
  size: number
}) {
  return useQuery({
    queryKey: ['contacts', 'list', { q, role, companyId, page, size }],
    queryFn: () =>
      unwrap(api.GET('/api/contacts', { params: { query: { q: q || undefined, role, companyId, page, size } } })),
    placeholderData: (previous) => previous,
  })
}

/** Quem você conhece na empresa da vaga. */
export function useJobContacts(jobId: number, enabled = true) {
  return useQuery({
    queryKey: ['contacts', 'job', jobId],
    queryFn: () => unwrap(api.GET('/api/jobs/{jobId}/contacts', { params: { path: { jobId } } })),
    enabled,
  })
}

/** Contatos da candidatura e as conversas registradas nela. */
export function useApplicationContacts(applicationId: number | undefined) {
  return useQuery({
    queryKey: ['contacts', 'application', applicationId ?? -1],
    queryFn: () =>
      unwrap(api.GET('/api/applications/{id}/contacts', { params: { path: { id: applicationId! } } })),
    enabled: applicationId !== undefined,
  })
}

export function useSaveContact() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id?: number } & Schemas['SaveContact']) =>
      id === undefined
        ? unwrap(api.POST('/api/contacts', { body }))
        : unwrap(api.PUT('/api/contacts/{id}', { params: { path: { id } }, body })),
    onSuccess: () => invalidateContacts(client),
  })
}

export type ConnectionsImport = Schemas['ConnectionsImport']

/** Importa o Connections.csv do export de dados do LinkedIn. */
export function useImportConnections() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData()
      form.append('file', file)
      const response = await send('/api/contacts/import/linkedin', { method: 'POST', body: form })
      return (await response.json()) as ConnectionsImport
    },
    onSuccess: () => invalidateContacts(client),
  })
}

/** Apaga o contato e as conversas registradas com ele. */
export function useDeleteContact() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/contacts/{id}', { params: { path: { id } } })),
    onSuccess: () => invalidateContacts(client),
  })
}

export function useRecordInteraction() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ contactId, ...body }: { contactId: number } & Schemas['RecordInteraction']) =>
      unwrap(api.POST('/api/contacts/{id}/interactions', { params: { path: { id: contactId } }, body })),
    onSuccess: () => invalidateContacts(client),
  })
}

// Telegram (alertas no celular)

export type TelegramStatus = Schemas['TelegramStatus']

/** Se o bot do Telegram está configurado no backend e quais tipos de notificação vão ao celular. */
export function useTelegramStatus() {
  return useQuery({ queryKey: ['telegram'], queryFn: () => unwrap(api.GET('/api/notifications/telegram')) })
}

export function useSendTelegramTest() {
  return useMutation({ mutationFn: () => unwrap(api.POST('/api/notifications/telegram/test')) })
}

export type ApiTokenStatus = Schemas['ApiTokenStatus']

/** Token pessoal da extensão do navegador: só o hash fica no servidor. */
export function useApiToken() {
  return useQuery({ queryKey: ['api-token'], queryFn: () => unwrap(api.GET('/api/auth/api-token')) })
}

export function useGenerateApiToken() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.POST('/api/auth/api-token')),
    onSuccess: () => client.invalidateQueries({ queryKey: ['api-token'] }),
  })
}

export function useRevokeApiToken() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/auth/api-token')),
    onSuccess: () => client.invalidateQueries({ queryKey: ['api-token'] }),
  })
}

// Fila do dia e checagem antes de aplicar

export type TodayAgenda = Schemas['TodayAgenda']
export type ApplyCheck = Schemas['ApplyCheck']

export function useToday() {
  return useQuery({ queryKey: ['today'], queryFn: () => unwrap(api.GET('/api/today')), refetchInterval: 5 * 60_000 })
}

/** Avisos antes de aplicar (link no ar, eliminatórios, legitimidade, score e candidaturas na empresa). */
export function useApplyCheck(jobId: number | undefined) {
  return useQuery({
    queryKey: ['apply-check', jobId],
    queryFn: () => unwrap(api.GET('/api/jobs/{id}/apply-check', { params: { path: { id: jobId as number } } })),
    enabled: jobId !== undefined,
    staleTime: 60_000,
  })
}

// Rascunhos de mensagem

export type MessageDraft = Schemas['MessageDraft']
export type DraftKind = MessageDraft['kind']

/** Gera o rascunho (nada é guardado nem enviado). */
export function useDraftMessage() {
  return useMutation({
    mutationFn: ({ jobId, kind, contactId }: { jobId: number; kind: DraftKind; contactId?: number | null }) =>
      unwrap(
        api.POST('/api/jobs/{jobId}/drafts', { params: { path: { jobId } }, body: { kind, contactId: contactId ?? null } }),
      ),
  })
}

/** Cria a vaga e a candidatura em Aplicada a partir do e-mail "recebemos sua candidatura". */
export function useRegisterFromEmail() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ emailId, ...body }: { emailId: number } & Schemas['ApplicationFromEmail']) =>
      unwrap(api.POST('/api/applications/from-email/{emailId}', { params: { path: { emailId } }, body })),
    onSuccess: () => Promise.all([client.invalidateQueries({ queryKey: ['emails'] }), invalidateTracking(client)]),
  })
}
