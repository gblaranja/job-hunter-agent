import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ScreeningQuestionsCard } from './ScreeningQuestionsCard'

describe('ScreeningQuestionsCard', () => {
  it('envia as perguntas e mostra os rascunhos', async () => {
    let body: unknown
    server.use(
      http.post('/api/jobs/:id/screening-answers', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          answers: [
            { question: 'Pretensão?', answer: 'R$ 15.000 por mês (CLT)', basis: 'dados da triagem', needsInput: false },
            { question: 'Por que aqui?', answer: '', basis: 'Responda com base no seu currículo.', needsInput: true },
          ],
          coverLetter: 'Olá! Tenho interesse.',
          source: 'REGRAS',
        })
      }),
    )
    const user = userEvent.setup()
    renderWithProviders(<ScreeningQuestionsCard jobId={7} />)

    await user.type(screen.getByLabelText('Perguntas'), 'Pretensão?{enter}Por que aqui?')
    await user.click(screen.getByLabelText(/carta curta/))
    await user.click(screen.getByRole('button', { name: 'Gerar rascunhos' }))

    expect(await screen.findByText('R$ 15.000 por mês (CLT)')).toBeInTheDocument()
    expect(screen.getByText('Precisa de você')).toBeInTheDocument()
    expect(screen.getByText('Olá! Tenho interesse.')).toBeInTheDocument()
    expect(body).toEqual({ questions: ['Pretensão?', 'Por que aqui?'], coverLetter: true })
  })
})
