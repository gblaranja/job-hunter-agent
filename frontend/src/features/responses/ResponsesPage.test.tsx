import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { EmailItem } from '@/api/queries'
import { board } from '@/test/fixtures'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ResponsesPage } from './ResponsesPage'

const email: EmailItem = {
  id: 7,
  sender: 'talentos@bancoexemplo.com.br',
  subject: 'Atualização do processo',
  snippet: 'Infelizmente seguimos com outra pessoa.',
  receivedAt: '2026-10-23T10:00:00Z',
  aiClassification: 'RECUSA',
  aiConfidence: 0.6,
  finalClassification: null,
  classifiedBy: null,
  companyGuess: null,
  jobGuess: null,
  rationale: 'Palavras-chave de RECUSA',
  needsReview: true,
  applicationId: 2,
  applicationTitle: 'Desenvolvedor Java Sênior',
  applicationCompany: 'Banco Exemplo',
}

describe('ResponsesPage', () => {
  it('mostra a sugestão da IA com a confiança e confirma com a candidatura sugerida', async () => {
    let reviewed: unknown
    server.use(
      http.get('/api/emails', () => HttpResponse.json([email])),
      http.get('/api/emails/accuracy', () => HttpResponse.json({ reviewed: 4, agreed: 3, pending: 1, rate: 0.75 })),
      http.get('/api/applications/board', () => HttpResponse.json(board())),
      http.patch('/api/emails/:id/classification', async ({ request }) => {
        reviewed = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<ResponsesPage />)

    expect(await screen.findByText('Atualização do processo')).toBeInTheDocument()
    expect(screen.getByText('60%')).toBeInTheDocument()
    expect(await screen.findByText('75%')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /confirmar/i }))

    await vi.waitFor(() => expect(reviewed).toEqual({ classification: 'RECUSA', applicationId: 2 }))
  })
})
