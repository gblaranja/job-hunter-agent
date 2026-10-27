import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { FollowUpStatus } from './FollowUpStatus'

const application = {
  id: 7,
  status: 'APLICADA',
  nextActionAt: '2026-10-30T12:00:00Z',
  nextActionKind: 'FOLLOW_UP',
  nextActionAuto: true,
  followUpsSent: 0,
}

describe('FollowUpStatus', () => {
  it('mostra o follow-up agendado sozinho e registra o envio', async () => {
    let registered: string | undefined
    server.use(
      http.post('/api/applications/:id/follow-ups', ({ params }) => {
        registered = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<FollowUpStatus application={application} />)

    expect(screen.getByText(/Follow-up em 30 de out 2026/)).toBeInTheDocument()
    expect(screen.getByText(/agendado sozinho/)).toBeInTheDocument()
    expect(screen.getByText('Nenhum follow-up enviado.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Registrar follow-up enviado' }))

    await vi.waitFor(() => expect(registered).toBe('7'))
  })

  it('não oferece follow-up numa candidatura encerrada', () => {
    renderWithProviders(
      <FollowUpStatus application={{ ...application, status: 'RECUSADA', nextActionAt: null, followUpsSent: 2 }} />,
    )

    expect(screen.getByText('Nenhuma próxima ação agendada.')).toBeInTheDocument()
    expect(screen.getByText('2 follow-ups enviados.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar follow-up enviado' })).not.toBeInTheDocument()
  })
})
