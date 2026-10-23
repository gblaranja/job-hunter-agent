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

export const queryKeys = {
  me: ['me'] as const,
  board: ['board'] as const,
  applications: (status?: ApplicationStatus, q?: string) => ['applications', { status, q }] as const,
  applied: (applicationId: number) => ['applied', applicationId] as const,
  job: (jobId: number) => ['job', jobId] as const,
  resumes: ['resumes'] as const,
}

/** Tudo o que depende das candidaturas é recarregado depois de qualquer mudança no funil. */
function invalidateTracking(client: QueryClient) {
  return Promise.all(
    ['board', 'applications', 'applied', 'job', 'resumes'].map((key) =>
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

export function useInboxJobs(q?: string) {
  return useQuery({
    queryKey: ['jobs', 'inbox', { q }],
    queryFn: () => unwrap(api.GET('/api/jobs', { params: { query: { inbox: true, q: q || undefined, size: 50 } } })),
    placeholderData: (previous) => previous,
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

// Empresas

export type CompanyItem = Schemas['CompanyItem']
export type WatchStatus = CompanyItem['watchStatus']

export function useCompanies() {
  return useQuery({ queryKey: ['companies'], queryFn: () => unwrap(api.GET('/api/companies')) })
}

export function useUpdateCompany() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Schemas['UpdateCompanyCommand']) =>
      unwrap(api.PATCH('/api/companies/{id}', { params: { path: { id } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['companies'] }),
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
        ['collection-runs', 'jobs', 'companies', 'gmail'].map((key) => client.invalidateQueries({ queryKey: [key] })),
      ),
  })
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
