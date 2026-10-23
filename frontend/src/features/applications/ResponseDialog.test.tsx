import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ResponseDialog } from './ResponseDialog'

describe('ResponseDialog', () => {
  it('registra um convite recebido pelo WhatsApp movendo o card', async () => {
    let body: unknown
    server.use(
      http.post('/api/applications/:id/responses', async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<ResponseDialog applicationId={5} />)

    await userEvent.click(screen.getByRole('button', { name: /registrar retorno/i }))
    await userEvent.type(screen.getByLabelText('Nota'), 'Recrutadora chamou para quinta')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }))

    await vi.waitFor(() =>
      expect(body).toEqual({
        responseType: 'CONVITE_ENTREVISTA',
        channel: 'WHATSAPP',
        note: 'Recrutadora chamou para quinta',
        moveStatus: true,
      }),
    )
  })
})
