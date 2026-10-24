import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Toaster } from 'sonner'
import { inboxJob } from '@/test/fixtures'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { InboxPage } from './InboxPage'

describe('InboxPage', () => {
  beforeEach(() => window.localStorage.clear())

  // 45 vagas na caixa; a resposta segue a página e o tamanho pedidos.
  function paginatedInbox(requests: URLSearchParams[]) {
    return http.get('/api/jobs', ({ request }) => {
      const params = new URL(request.url).searchParams
      requests.push(params)
      const page = Number(params.get('page'))
      const size = Number(params.get('size'))
      const count = Math.max(0, Math.min(size, 45 - page * size))
      const items = Array.from({ length: count }, (_, index) =>
        inboxJob({ id: page * size + index + 1, title: `Vaga ${page * size + index + 1}` }),
      )
      return HttpResponse.json({ items, page, size, total: 45 })
    })
  }

  it('navega entre as páginas da caixa', async () => {
    const requests: URLSearchParams[] = []
    server.use(paginatedInbox(requests))
    renderWithProviders(<InboxPage />)

    expect(await screen.findByText('1–20 de 45 vagas')).toBeInTheDocument()
    expect(requests.at(-1)?.get('inbox')).toBe('true')
    expect(screen.getByRole('button', { name: 'Página anterior' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Próxima página' }))
    expect(await screen.findByText('Vaga 21')).toBeInTheDocument()
    expect(screen.getByText('21–40 de 45 vagas')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Página 2' })).toHaveAttribute('aria-current', 'page')

    await userEvent.click(screen.getByRole('button', { name: 'Última página' }))
    expect(await screen.findByText('Vaga 45')).toBeInTheDocument()
    expect(requests.at(-1)?.get('page')).toBe('2')
    expect(screen.getByRole('button', { name: 'Próxima página' })).toBeDisabled()
  })

  it('deixa escolher quantas vagas mostrar e lembra a escolha', async () => {
    const requests: URLSearchParams[] = []
    server.use(paginatedInbox(requests))
    renderWithProviders(<InboxPage />, { route: '/?page=2' })

    expect(await screen.findByText('21–40 de 45 vagas')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('combobox', { name: 'Itens por página' }))
    await userEvent.click(screen.getByRole('option', { name: '50' }))

    expect(await screen.findByText('1–45 de 45 vagas')).toBeInTheDocument()
    expect(requests.at(-1)?.get('size')).toBe('50')
    expect(requests.at(-1)?.get('page')).toBe('0')
    expect(window.localStorage.getItem('job-hunter-inbox-page-size')).toBe('50')
  })

  it('volta para a última página que ainda existe', async () => {
    const requests: URLSearchParams[] = []
    server.use(paginatedInbox(requests))
    renderWithProviders(<InboxPage />, { route: '/?page=9&size=10' })

    expect(await screen.findByText('41–45 de 45 vagas')).toBeInTheDocument()
    expect(requests.at(-1)?.get('page')).toBe('4')
  })

  it('registra "não gostei" com os motivos escolhidos', async () => {
    let feedback: unknown
    server.use(
      http.get('/api/jobs', () => HttpResponse.json({ items: [inboxJob()], page: 0, size: 50, total: 1 })),
      http.put('/api/jobs/:id/feedback', async ({ request }) => {
        feedback = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<InboxPage />)

    expect(await screen.findByText('Desenvolvedor Java Sênior')).toBeInTheDocument()
    expect(screen.getByText('LinkedIn')).toBeInTheDocument()
    expect(screen.getByLabelText('Aderência 82')).toBeInTheDocument()
    expect(screen.getByText(/motor de crédito/)).toBeInTheDocument()
    expect(screen.getAllByLabelText('lacuna')).toHaveLength(1)

    await userEvent.click(screen.getByRole('button', { name: /não gostei/i }))
    await userEvent.click(await screen.findByRole('button', { name: 'Salário' }))
    await userEvent.click(screen.getByRole('button', { name: 'Stack' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await vi.waitFor(() => expect(feedback).toEqual({ rating: 'NAO_GOSTEI', reasons: ['SALARIO', 'STACK'] }))
  })

  it('marca interesse criando a candidatura', async () => {
    let body: unknown
    server.use(
      http.get('/api/jobs', () => HttpResponse.json({ items: [inboxJob()], page: 0, size: 50, total: 1 })),
      http.post('/api/jobs/:id/application', async ({ request, params }) => {
        body = { jobId: params.id, ...((await request.json()) as object) }
        return HttpResponse.json({ id: 1 }, { status: 201 })
      }),
    )
    renderWithProviders(<InboxPage />)

    await userEvent.click(await screen.findByRole('button', { name: /tenho interesse\?/i }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Tenho interesse' }))

    await vi.waitFor(() => expect(body).toEqual({ jobId: '30', status: 'INTERESSE' }))
  })

  it.each([
    ['Não tenho interesse', 'SEM_INTERESSE'],
    ['Não tenho o perfil técnico', 'SEM_PERFIL_TECNICO'],
  ])('"%s" tira a vaga da caixa e deixa desfazer', async (option, reason) => {
    let dismissed: unknown
    let restored = false
    server.use(
      http.get('/api/jobs', () =>
        dismissed && !restored
          ? HttpResponse.json({ items: [], page: 0, size: 50, total: 0 })
          : HttpResponse.json({ items: [inboxJob()], page: 0, size: 50, total: 1 }),
      ),
      http.put('/api/jobs/:id/dismissal', async ({ request }) => {
        dismissed = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
      http.delete('/api/jobs/:id/dismissal', () => {
        restored = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(
      <>
        <InboxPage />
        <Toaster />
      </>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /tenho interesse\?/i }))
    await userEvent.click(await screen.findByRole('menuitem', { name: option }))

    await vi.waitFor(() => expect(dismissed).toEqual({ reason }))
    await vi.waitFor(() => expect(screen.queryByText('Desenvolvedor Java Sênior')).not.toBeInTheDocument())

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))
    expect(await screen.findByText('Desenvolvedor Java Sênior')).toBeInTheDocument()
  })

  it('registra a vaga que não aceita mais candidaturas como encerrada', async () => {
    let body: unknown
    server.use(
      http.get('/api/jobs', () => HttpResponse.json({ items: [inboxJob()], page: 0, size: 50, total: 1 })),
      http.post('/api/jobs/:id/application', async ({ request, params }) => {
        body = { jobId: params.id, ...((await request.json()) as object) }
        return HttpResponse.json({ id: 1 }, { status: 201 })
      }),
    )
    renderWithProviders(<InboxPage />)

    await userEvent.click(await screen.findByRole('button', { name: /vaga encerrada/i }))

    await vi.waitFor(() =>
      expect(body).toEqual({ jobId: '30', status: 'ENCERRADA', notes: expect.stringContaining('não aceitava') }),
    )
  })

  describe('seções nacionais e internacionais', () => {
    // 3 vagas nacionais e 2 internacionais; a resposta segue o "scope" pedido.
    function inboxByScope(requests: URLSearchParams[]) {
      return http.get('/api/jobs', ({ request }) => {
        const params = new URL(request.url).searchParams
        requests.push(params)
        const scope = params.get('scope')
        const total = scope === 'INTERNACIONAL' ? 2 : 3
        const items = Array.from({ length: Math.min(total, Number(params.get('size'))) }, (_, index) =>
          inboxJob({
            id: (scope === 'INTERNACIONAL' ? 100 : 1) + index,
            title: `${scope === 'INTERNACIONAL' ? 'Backend Engineer' : 'Dev Java'} ${index + 1}`,
            scope: scope === 'INTERNACIONAL' ? 'INTERNACIONAL' : 'NACIONAL',
          }),
        )
        return HttpResponse.json({ items, page: Number(params.get('page') ?? 0), size: 20, total })
      })
    }

    it('abre nas nacionais e mostra o total de cada seção', async () => {
      const requests: URLSearchParams[] = []
      server.use(inboxByScope(requests))
      renderWithProviders(<InboxPage />)

      expect(await screen.findByText('Dev Java 1')).toBeInTheDocument()
      expect(screen.queryByText('Backend Engineer 1')).not.toBeInTheDocument()
      expect(screen.getByRole('tab', { name: /^nacionais/i })).toHaveAttribute('aria-selected', 'true')
      expect(await screen.findByRole('tab', { name: 'Internacionais 2' })).toBeInTheDocument()
      expect(screen.getByRole('tab', { name: 'Nacionais 3' })).toBeInTheDocument()
      expect(screen.getByText('5 vagas')).toBeInTheDocument()
    })

    it('troca para as internacionais voltando para a primeira página', async () => {
      const requests: URLSearchParams[] = []
      server.use(inboxByScope(requests))
      renderWithProviders(<InboxPage />, { route: '/?page=2' })

      await userEvent.click(await screen.findByRole('tab', { name: /internacionais/i }))

      expect(await screen.findByText('Backend Engineer 1')).toBeInTheDocument()
      const listing = requests.filter((params) => params.get('size') !== '1').at(-1)
      expect(listing?.get('scope')).toBe('INTERNACIONAL')
      expect(listing?.get('page')).toBe('0')
    })
  })
})
