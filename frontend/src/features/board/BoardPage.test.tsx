import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { board } from '@/test/fixtures'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { BoardPage } from './BoardPage'

describe('BoardPage', () => {
  it('mostra os cards por etapa com currículo, dias na etapa e alertas', async () => {
    server.use(http.get('/api/applications/board', () => HttpResponse.json(board())))
    renderWithProviders(<BoardPage />)

    const applied = await screen.findByRole('region', { name: 'Aplicada' })
    expect(within(applied).getByText('Desenvolvedor Java Sênior')).toBeInTheDocument()
    expect(within(applied).getByText('Backend Java')).toBeInTheDocument()
    expect(within(applied).getByText('4 dias')).toBeInTheDocument()
    expect(within(applied).getByText('vaga fora do ar')).toBeInTheDocument()
    expect(within(applied).getByText(/Follow-up · 1 de jan/)).toBeInTheDocument()

    const interview = screen.getByRole('region', { name: 'Entrevista' })
    expect(within(interview).getByText('0')).toBeInTheDocument()
  })

  it('abre o painel lateral da candidatura', async () => {
    server.use(
      http.get('/api/applications/board', () => HttpResponse.json(board())),
      http.get('/api/jobs/:id', () =>
        HttpResponse.json({
          job: {
            id: 11,
            origin: 'MANUAL',
            title: 'Engenheiro de Software',
            company: 'Fintech Exemplo',
            workModel: 'REMOTO',
            collectedAt: '2026-10-01T12:00:00Z',
          },
          application: { id: 1, status: 'INTERESSE', notes: 'Falar com a Ana' },
          timeline: [],
        }),
      ),
    )
    renderWithProviders(<BoardPage />)

    const interest = await screen.findByRole('region', { name: 'Interesse' })
    await userEvent.click(within(interest).getByRole('button', { name: 'Abrir detalhes' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(await screen.findByDisplayValue('Falar com a Ana')).toBeInTheDocument()
  })
})
