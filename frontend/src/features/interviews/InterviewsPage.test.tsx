import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { Offer, StarStory } from '@/api/interviews'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { InterviewsPage } from './InterviewsPage'

const story: StarStory = {
  id: 1,
  title: 'Motor de crédito',
  situation: 'O motor caía no pico.',
  task: 'Estabilizar.',
  action: 'Movi o cálculo para eventos no Kafka.',
  result: 'Zero quedas em 6 meses.',
  reflection: null,
  skills: ['Kafka'],
  createdAt: '2026-10-20T12:00:00Z',
  updatedAt: '2026-10-20T12:00:00Z',
}

function offer(partial: Partial<Offer>): Offer {
  return {
    id: 1,
    applicationId: 1,
    jobId: 10,
    title: 'Desenvolvedor Java',
    company: 'Banco Alfa',
    regime: 'CLT',
    baseSalary: 10000,
    currency: 'BRL',
    period: 'MENSAL',
    decision: 'PENDENTE',
    annualEquivalent: 161964,
    monthlyEquivalent: 13497,
    createdAt: '2026-10-20T12:00:00Z',
    ...partial,
  }
}

describe('InterviewsPage', () => {
  it('lista as histórias e salva uma nova', async () => {
    let created: unknown
    server.use(
      http.get('/api/stories', () => HttpResponse.json([story])),
      http.post('/api/stories', async ({ request }) => {
        created = await request.json()
        return HttpResponse.json({ ...story, id: 2 }, { status: 201 })
      }),
    )
    renderWithProviders(<InterviewsPage />, { route: '/interviews' })

    expect(await screen.findByText('Motor de crédito')).toBeInTheDocument()
    expect(screen.getByText('Kafka')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Nova história/ }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Título'), 'Conflito no time')
    await userEvent.type(within(dialog).getByLabelText(/Skills/), 'Liderança, Scrum')
    await userEvent.type(within(dialog).getByLabelText('Resultado'), 'Entregamos no prazo.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await vi.waitFor(() =>
      expect(created).toMatchObject({ title: 'Conflito no time', skills: ['Liderança', 'Scrum'], result: 'Entregamos no prazo.' }),
    )
  })

  it('compara as propostas pelo equivalente anual e marca a maior', async () => {
    server.use(
      http.get('/api/offers', () =>
        HttpResponse.json([
          offer({}),
          offer({ id: 2, regime: 'PJ', baseSalary: 15000, company: 'Fintech Beta', annualEquivalent: 148200, monthlyEquivalent: 12350 }),
          offer({ id: 3, regime: 'CONTRACTOR', currency: 'EUR', baseSalary: 5000, company: 'Euro Co', annualEquivalent: null, monthlyEquivalent: null }),
        ]),
      ),
    )
    renderWithProviders(<InterviewsPage />, { route: '/interviews?tab=offers' })

    const clt = await screen.findByRole('row', { name: /Banco Alfa/ })
    expect(within(clt).getByText(/R\$\s161\.964/)).toBeInTheDocument()
    expect(within(clt).getByText('maior')).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Fintech Beta/ })).getByText(/PJ · R\$\s15\.000 por mês/)).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Euro Co/ })).getByText('sem câmbio')).toBeInTheDocument()
  })
})
