import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { inboxJob } from '@/test/fixtures'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { InboxPage } from './InboxPage'

describe('InboxPage', () => {
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

    await userEvent.click(await screen.findByRole('button', { name: /tenho interesse/i }))

    await vi.waitFor(() => expect(body).toEqual({ jobId: '30', status: 'INTERESSE' }))
  })
})
