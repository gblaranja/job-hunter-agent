import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { TodayPage } from './TodayPage'

describe('TodayPage', () => {
  it('mostra a fila do dia e a meta semanal', async () => {
    server.use(
      http.get('/api/today', () =>
        HttpResponse.json({
          day: '2026-10-26',
          jobs: [
            { id: 1, title: 'Desenvolvedor Java', company: 'Acme', matchScore: 85, firstSeenAt: new Date().toISOString(), legitimacy: 'MEDIA', url: null },
          ],
          followUps: [
            { applicationId: 2, jobId: 3, title: 'Backend', company: 'Zeta', status: 'APLICADA', nextActionAt: '2026-10-25T12:00:00Z', nextActionKind: 'FOLLOW_UP', followUpsSent: 1 },
          ],
          emails: [{ id: 4, sender: 'rh@eta.com', subject: 'Próximos passos', receivedAt: '2026-10-26T09:00:00Z', classification: 'OUTRO' }],
          interviews: [],
          tailoredResumes: [],
          weeklyGoal: { weekStart: '2026-10-26', applied: 5, aligned: 3, goal: 12 },
        }),
      ),
    )
    renderWithProviders(<TodayPage />)

    expect(await screen.findByRole('link', { name: 'Desenvolvedor Java' })).toHaveAttribute('href', '/jobs/1')
    expect(screen.getByText('Atenção')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zeta' })).toHaveAttribute('href', '/jobs/3')
    expect(screen.getByText('Próximos passos')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma entrevista ou teste em andamento.')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Progresso da meta semanal' })).toHaveAttribute('aria-valuenow', '3')
  })
})
