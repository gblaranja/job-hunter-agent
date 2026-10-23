import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { board } from '@/test/fixtures'
import { api, ApiError, unwrap } from './client'
import { moveCard } from './queries'

describe('cliente da API', () => {
  it('devolve o token CSRF do cookie no header em requisições que alteram dados', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    let received: string | null = null
    server.use(
      http.patch('/api/applications/:id/status', ({ request }) => {
        received = request.headers.get('X-XSRF-TOKEN')
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await unwrap(api.PATCH('/api/applications/{id}/status', { params: { path: { id: 1 } }, body: { status: 'APLICADA' } }))

    expect(received).toBe('token-123')
  })

  it('transforma o ProblemDetail em ApiError com o id do registro existente', async () => {
    server.use(
      http.post('/api/jobs', () =>
        HttpResponse.json({ title: 'Conflito', detail: 'Essa vaga já está cadastrada.', existingId: 7 }, { status: 409 }),
      ),
    )

    const error = await unwrap(api.POST('/api/jobs', { body: { title: 'Dev', company: 'X' } })).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, message: 'Essa vaga já está cadastrada.', existingId: 7 })
  })
})

describe('moveCard', () => {
  it('move o card para o topo da coluna de destino e zera os dias na etapa', () => {
    const moved = moveCard(board(), 1, 'ENTREVISTA')

    expect(moved.columns.find((column) => column.status === 'INTERESSE')?.cards).toHaveLength(0)
    expect(moved.columns.find((column) => column.status === 'ENTREVISTA')?.cards[0]).toMatchObject({
      applicationId: 1,
      status: 'ENTREVISTA',
      daysInStatus: 0,
    })
  })
})
