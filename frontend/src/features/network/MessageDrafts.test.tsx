import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { MessageDrafts } from './MessageDrafts'

describe('MessageDrafts', () => {
  it('gera o follow-up, copia e marca como enviada contando o follow-up', async () => {
    let followUps = 0
    let draftBody: unknown
    server.use(
      http.get('/api/applications/:id/contacts', () => HttpResponse.json({ contacts: [], interactions: [] })),
      http.post('/api/jobs/:jobId/drafts', async ({ request }) => {
        draftBody = await request.json()
        return HttpResponse.json({
          kind: 'FOLLOW_UP',
          text: 'Olá! Me candidatei à vaga de Java. [seu nome]',
          linkedinNote: null,
          language: 'pt',
          source: 'MODELO',
        })
      }),
      http.post('/api/applications/:id/follow-ups', () => {
        followUps++
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<MessageDrafts jobId={20} applicationId={7} />)

    await userEvent.click(screen.getByRole('button', { name: /Follow-up/ }))

    expect(await screen.findByDisplayValue(/Me candidatei à vaga de Java/)).toBeInTheDocument()
    expect(draftBody).toEqual({ kind: 'FOLLOW_UP', contactId: null })
    expect(screen.getByText(/complete os colchetes/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Marcar como enviada' }))
    await vi.waitFor(() => expect(followUps).toBe(1))
  })

  it('abre direto no tipo pedido e mostra o convite com a contagem de caracteres', async () => {
    server.use(
      http.get('/api/jobs/:jobId/contacts', () => HttpResponse.json([])),
      http.post('/api/jobs/:jobId/drafts', () =>
        HttpResponse.json({
          kind: 'PEDIDO_INDICACAO',
          text: 'Olá! Você toparia me indicar? [seu nome]',
          linkedinNote: 'Olá! Gostaria de me conectar.',
          language: 'pt',
          source: 'IA',
        }),
      ),
    )
    renderWithProviders(<MessageDrafts jobId={20} initialKind="PEDIDO_INDICACAO" />)

    expect(await screen.findByDisplayValue('Olá! Gostaria de me conectar.')).toBeInTheDocument()
    expect(screen.getByText('29/300 caracteres')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Marcar como enviada' })).not.toBeInTheDocument()
  })
})
