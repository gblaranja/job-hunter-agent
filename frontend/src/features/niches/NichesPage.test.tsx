import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { NicheCompany, NicheView } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { NichesPage } from './NichesPage'

const weights = { segment: 0.35, stack: 0.3, domain: 0.2, signals: 0.15 }

const niches: NicheView[] = [
  {
    id: 1,
    name: 'Financeiro',
    description: 'Fintechs e bancos.',
    active: true,
    fitWeights: weights,
    companyKinds: ['CONTRATANTE'],
    domainTerms: ['crédito'],
    companies: 2,
    segments: [
      { id: 11, name: 'Crédito', description: null, baseFit: 90, keywords: ['credito'], authorizationTypes: [], companies: 2, highFit: 1, withOpenJobs: 1, avgFit: 70 },
      { id: 12, name: 'Seguros e consórcios', description: null, baseFit: 40, keywords: [], authorizationTypes: [], companies: 0, highFit: 0, withOpenJobs: 0, avgFit: null },
    ],
  },
  {
    id: 2,
    name: 'Consultorias',
    description: null,
    active: true,
    fitWeights: weights,
    companyKinds: ['CONSULTORIA_TI'],
    domainTerms: [],
    companies: 1,
    segments: [
      { id: 21, name: 'Alocação e outsourcing', description: null, baseFit: 70, keywords: [], authorizationTypes: [], companies: 1, highFit: 0, withOpenJobs: 1, avgFit: 60 },
    ],
  },
]

function company(overrides: Partial<NicheCompany> = {}): NicheCompany {
  return {
    id: 7,
    name: 'Credix',
    watchStatus: 'MONITORADA',
    kind: 'CONTRATANTE',
    clientSectors: [],
    authorizationType: 'Sociedade de Crédito Direto',
    segmentId: 11,
    segmentName: 'Crédito',
    source: 'BCB',
    verified: true,
    fitScore: 82,
    fitBand: 'ALTO',
    reasons: ['Crédito', '8 das 10 últimas vagas pedem Java/Spring'],
    components: { segment: 90, stack: 80, domain: 60, signals: 70 },
    gptwLevel: 'RANKING_TI',
    gptwYear: 2026,
    jobsActive: 3,
    jobsLast90Days: 5,
    applications: 0,
    trend: [0, 0, 1, 0, 2, 0, 0, 1, 0, 0, 1, 0],
    ...overrides,
  }
}

describe('NichesPage', () => {
  it('lista as empresas do nicho por fit, com selo, justificativa e o botão de alvo', async () => {
    const requests: URLSearchParams[] = []
    let watched: unknown
    server.use(
      http.get('/api/niches', () => HttpResponse.json(niches)),
      http.get('/api/niches/:id/companies', ({ request }) => {
        requests.push(new URL(request.url).searchParams)
        return HttpResponse.json({ items: [company()], page: 0, size: 50, total: 1 })
      }),
      http.put('/api/companies/:id/watch-status', async ({ request }) => {
        watched = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<NichesPage />)

    const row = (await screen.findByRole('link', { name: 'Credix' })).closest('tr')!
    expect(within(row).getByLabelText('Premiada no ranking GPTW TI em 2026')).toBeInTheDocument()
    expect(within(row).getByText('82')).toBeInTheDocument()
    expect(within(row).getByText('Fit alto')).toBeInTheDocument()
    expect(within(row).getByText('Crédito · 8 das 10 últimas vagas pedem Java/Spring')).toBeInTheDocument()
    expect(within(row).getByRole('img', { name: '5 vagas abertas nas últimas 12 semanas' })).toBeInTheDocument()
    // filtro de clientes financeiros só faz sentido para consultorias
    expect(screen.queryByRole('button', { name: 'Alocam em bancos e fintechs' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Apenas GPTW' }))
    expect(screen.getByRole('button', { name: 'Apenas GPTW' })).toHaveAttribute('aria-pressed', 'true')
    await vi.waitFor(() => expect(requests.at(-1)?.get('gptw')).toBe('true'))

    await userEvent.click(within(row).getByRole('button', { name: 'Marcar como alvo' }))
    await vi.waitFor(() => expect(watched).toEqual({ watchStatus: 'ALVO' }))
  })

  it('a aba Consultorias oferece o filtro de quem aloca em bancos e fintechs', async () => {
    const requests: string[] = []
    server.use(
      http.get('/api/niches', () => HttpResponse.json(niches)),
      http.get('/api/niches/:id/companies', ({ request, params }) => {
        requests.push(`${params.id}?${new URL(request.url).searchParams}`)
        return HttpResponse.json({
          items: [company({ id: 9, name: 'Omega', kind: 'CONSULTORIA_TI', segmentName: 'Alocação e outsourcing', gptwLevel: null })],
          page: 0,
          size: 50,
          total: 1,
        })
      }),
    )
    renderWithProviders(<NichesPage />)

    await userEvent.click(await screen.findByRole('tab', { name: /Consultorias/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Alocam em bancos e fintechs' }))

    await vi.waitFor(() => expect(requests.at(-1)).toMatch(/^2\?.*financialClients=true/))
    expect(await screen.findByRole('link', { name: 'Omega' })).toBeInTheDocument()
  })

  it('o editor de pesos normaliza para 100% e recalcula', async () => {
    let body: unknown
    server.use(
      http.get('/api/niches', () => HttpResponse.json(niches)),
      http.get('/api/niches/:id/companies', () => HttpResponse.json({ items: [], page: 0, size: 50, total: 0 })),
      http.patch('/api/niches/1', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(niches[0])
      }),
    )
    renderWithProviders(<NichesPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Pesos' }))
    const dialog = await screen.findByRole('dialog', { name: 'Pesos do nicho Financeiro' })
    const stack = within(dialog).getByLabelText(/^Stack/)
    await userEvent.clear(stack)
    await userEvent.type(stack, '80')
    expect(within(dialog).getByText('Soma: 150% (será normalizada para 100%)')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar e recalcular' }))

    await vi.waitFor(() => expect(body).toBeDefined())
    const { fitWeights } = body as { fitWeights: Record<string, number> }
    expect(fitWeights.stack).toBeCloseTo(80 / 150)
    expect(fitWeights.segment + fitWeights.stack + fitWeights.domain + fitWeights.signals).toBeCloseTo(1)
  })
})
