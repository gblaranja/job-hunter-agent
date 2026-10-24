import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { RecognitionView } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { CompaniesPage } from './CompaniesPage'
import { GptwImportDialog } from './GptwImportDialog'

const pending: RecognitionView = {
  id: 4,
  companyId: 3,
  companyName: 'Stone',
  program: 'GPTW',
  level: 'CERTIFICADA',
  rankingName: null,
  rankingPosition: null,
  year: 2026,
  validUntil: '2027-12-31',
  evidenceUrl: 'https://gptw.com.br/empresa/stone',
  evidenceExcerpt: 'Stone conquista a certificação',
  rationale: 'Há empresas com nome parecido: Stone Pagamentos.',
  matchedName: 'Stone',
  confidence: 0.95,
  verifiedBy: 'IA',
  reviewStatus: 'PENDENTE',
  source: 'BUSCA',
  checkedAt: '2026-10-23T10:00:00Z',
  homonyms: ['Stone Pagamentos'],
}

describe('selos GPTW na tela de Empresas', () => {
  it('a notificação abre a fila de revisão, que mostra os homônimos e confirma o selo', async () => {
    let confirmed = false
    server.use(
      http.get('/api/companies', () => HttpResponse.json([])),
      http.get('/api/recognitions/status', () =>
        HttpResponse.json({ enabled: true, searchConfigured: true, provider: 'tavily', searchesThisMonth: 2, monthlyLimit: 200, pendingReview: 1 }),
      ),
      http.get('/api/recognitions/review', () => HttpResponse.json(confirmed ? [] : [pending])),
      http.post('/api/recognitions/4/confirm', () => {
        confirmed = true
        return HttpResponse.json({ ...pending, reviewStatus: 'CONFIRMADO', verifiedBy: 'USUARIO' })
      }),
    )
    renderWithProviders(<CompaniesPage />, { route: '/companies?review=gptw' })

    const dialog = await screen.findByRole('dialog', { name: 'Revisar selos GPTW' })
    expect(await within(dialog).findByText('Nomes parecidos cadastrados: Stone Pagamentos')).toBeInTheDocument()
    expect(within(dialog).getByRole('link', { name: 'Abrir evidência' })).toHaveAttribute('href', pending.evidenceUrl)

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar' }))
    expect(await within(dialog).findByText('Nenhum selo esperando revisão.')).toBeInTheDocument()
  })

  it('a importação mostra a prévia e só leva as empresas marcadas; nomes parecidos começam desmarcados', async () => {
    let confirmation: unknown
    server.use(
      http.post('/api/recognitions/import', () =>
        HttpResponse.json({
          matched: 2,
          items: [
            { extractedName: 'Alfa Tecnologia', position: 1, companyId: 1, companyName: 'Alfa Tecnologia', match: 'EXATO', similarity: 1 },
            { extractedName: 'Gamma Sistemas', position: 2, companyId: 2, companyName: 'Gama Sistemas', match: 'SIMILAR', similarity: 0.6 },
            { extractedName: 'Desconhecida', position: 3, companyId: null, companyName: null, match: 'NENHUM', similarity: 0 },
          ],
        }),
      ),
      http.post('/api/recognitions/import/confirm', async ({ request }) => {
        confirmation = await request.json()
        return HttpResponse.json({ companies: 1 })
      }),
    )
    renderWithProviders(<GptwImportDialog onClose={() => {}} />)

    await userEvent.type(screen.getByLabelText('Ou o texto da lista'), '1. Alfa Tecnologia')
    await userEvent.click(screen.getByRole('button', { name: 'Ler a lista' }))

    const preview = await screen.findByRole('list', { name: 'Prévia da importação' })
    expect(within(preview).getByRole('checkbox', { name: 'Marcar Alfa Tecnologia' })).toBeChecked()
    expect(within(preview).getByRole('checkbox', { name: 'Marcar Gama Sistemas' })).not.toBeChecked()
    expect(within(preview).getByText('não cadastrada')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Registrar selo em 1 empresa' }))
    await vi.waitFor(() =>
      expect(confirmation).toMatchObject({ level: 'RANKING_TI', items: [{ companyId: 1, position: 1 }] }),
    )
  })
})
