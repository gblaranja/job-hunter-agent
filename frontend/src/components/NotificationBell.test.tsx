import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { NotificationItem } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { NotificationBell } from './NotificationBell'

const unread: NotificationItem = {
  id: 3,
  type: 'EMPRESA_VOLTOU',
  title: 'Banco Alfa voltou a contratar',
  body: 'Primeira vaga depois de mais de 60 dias.',
  link: '/companies/1',
  readAt: null,
  createdAt: '2026-10-23T12:00:00Z',
}

describe('NotificationBell', () => {
  it('mostra quantas não foram lidas e marca como lida ao abrir', async () => {
    let read: string | undefined
    server.use(
      http.get('/api/notifications', () =>
        HttpResponse.json([unread, { ...unread, id: 2, title: 'Antiga', readAt: '2026-10-20T00:00:00Z' }]),
      ),
      http.patch('/api/notifications/:id/read', ({ params }) => {
        read = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<NotificationBell />)

    await userEvent.click(await screen.findByRole('button', { name: 'Notificações (1 não lidas)' }))
    await userEvent.click(screen.getByText('Banco Alfa voltou a contratar'))

    await vi.waitFor(() => expect(read).toBe('3'))
  })
})
