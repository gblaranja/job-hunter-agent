import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { appliedListItem } from '@/test/fixtures'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ApplicationsPage } from './ApplicationsPage'

describe('ApplicationsPage', () => {
  it('busca no texto salvo das vagas e mostra o trecho encontrado', async () => {
    const queries: (string | null)[] = []
    server.use(
      http.get('/api/applications', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q')
        queries.push(q)
        return HttpResponse.json(
          q === 'kafka'
            ? [appliedListItem({ excerpt: '…experiência com Apache Kafka e mensageria…' })]
            : [appliedListItem(), appliedListItem({ applicationId: 11, jobId: 21, company: 'Fintech Exemplo' })],
        )
      }),
    )
    renderWithProviders(<ApplicationsPage />)

    expect(await screen.findByText('Fintech Exemplo')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Buscar candidaturas'), 'kafka')

    expect(await screen.findByText(/experiência com Apache Kafka/)).toBeInTheDocument()
    expect(screen.queryByText('Fintech Exemplo')).not.toBeInTheDocument()
    expect(queries).toContain('kafka')
  })

  it('explica o que fazer quando ainda não há candidaturas', async () => {
    server.use(http.get('/api/applications', () => HttpResponse.json([])))
    renderWithProviders(<ApplicationsPage />)

    expect(await screen.findByText('Nenhuma candidatura registrada ainda.')).toBeInTheDocument()
  })
})
