import createClient, { type Middleware } from 'openapi-fetch'
import type { components, paths } from './schema'

export type Schemas = components['schemas']

/** Erro da API no formato ProblemDetail (RFC 9457). */
export class ApiError extends Error {
  readonly status: number
  readonly existingId?: number

  constructor(status: number, detail: string, existingId?: number) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.existingId = existingId
  }
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/** O backend usa CSRF em modo SPA: o token vem no cookie XSRF-TOKEN e volta no header X-XSRF-TOKEN. */
export function withCsrf(headers: Headers, method: string): Headers {
  if (!SAFE_METHODS.has(method.toUpperCase())) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }
  return headers
}

const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    withCsrf(request.headers, request.method)
    return request
  },
}

export const api = createClient<paths>({
  baseUrl: window.location.origin,
  credentials: 'same-origin',
  // Resolve o fetch a cada chamada (e não na criação), para interceptadores como o MSW funcionarem.
  fetch: (request) => globalThis.fetch(request),
})
api.use(csrfMiddleware)

type ProblemBody = { detail?: string; title?: string; existingId?: number }

export async function toApiError(response: Response, body?: unknown): Promise<ApiError> {
  let problem: ProblemBody | undefined = body as ProblemBody | undefined
  if (problem === undefined) {
    try {
      problem = (await response.json()) as ProblemBody
    } catch {
      problem = undefined
    }
  }
  const detail = problem?.detail ?? problem?.title ?? defaultMessage(response.status)
  return new ApiError(response.status, detail, problem?.existingId)
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Sua sessão expirou. Entre de novo.'
  if (status === 403) return 'Ação não permitida. Recarregue a página e tente de novo.'
  if (status >= 500) return 'O servidor não conseguiu concluir a operação.'
  return 'Não foi possível concluir a operação.'
}

/** Converte o retorno do openapi-fetch em dado ou exceção, para o TanStack Query tratar. */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call
  if (!response.ok) throw await toApiError(response, error)
  return data as T
}

/** Requisições fora do contrato OpenAPI (login e multipart), com o mesmo tratamento de CSRF e erro. */
export async function send(path: string, init: RequestInit & { method: string }): Promise<Response> {
  const response = await fetch(new URL(path, window.location.origin), {
    credentials: 'same-origin',
    ...init,
    headers: withCsrf(new Headers(init.headers), init.method),
  })
  if (!response.ok) throw await toApiError(response)
  return response
}
