import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { InterviewPrep } from '@/api/interviews'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { InterviewSection } from './InterviewSection'

const prep: InterviewPrep = {
  id: 5,
  applicationId: 7,
  jobId: 20,
  stage: 'ENTREVISTA',
  status: 'PRONTO',
  source: 'REGRAS',
  content: {
    questions: [
      {
        question: 'Conte uma situação em que você usou Kafka para resolver um problema.',
        reason: 'Requisito obrigatório da vaga que o seu currículo atende.',
        storyId: 1,
        storyTitle: 'Motor de crédito',
        answerOutline: 'Use a história "Motor de crédito".',
      },
    ],
    technicalTopics: ['Kafka (obrigatória)'],
    companyFacts: ['Setor: Bancos.'],
    questionsToAsk: ['Como é o dia a dia do time?'],
    pointsToClarify: ['Faixa salarial: a vaga não informa.'],
  },
  createdAt: '2026-10-23T12:00:00Z',
  updatedAt: '2026-10-23T12:00:00Z',
}

describe('InterviewSection', () => {
  it('mostra a preparação com a história sugerida', async () => {
    server.use(http.get('/api/applications/:id/interview-prep', () => HttpResponse.json([prep])))
    renderWithProviders(<InterviewSection applicationId={7} jobId={20} status="ENTREVISTA" />)

    expect(await screen.findByText(/usou Kafka para resolver/)).toBeInTheDocument()
    expect(screen.getByText('História: Motor de crédito')).toBeInTheDocument()
    expect(screen.getByText('Faixa salarial: a vaga não informa.')).toBeInTheDocument()
    expect(screen.getByText(/Montada pelas regras/)).toBeInTheDocument()
  })

  it('registra a entrevista, mostra o follow-up e abre o agradecimento', async () => {
    let saved: unknown
    let draftKind: unknown
    server.use(
      http.get('/api/applications/:id/interview-prep', () => HttpResponse.json([prep])),
      http.get('/api/applications/:id/debriefs', () => HttpResponse.json([])),
      http.get('/api/applications/:id/contacts', () => HttpResponse.json({ contacts: [], interactions: [] })),
      http.post('/api/applications/:id/debriefs', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(
          {
            debrief: {
              id: 1,
              applicationId: 7,
              stage: 'ENTREVISTA',
              interviewedOn: '2026-10-23',
              questions: ['Como você usa Kafka?'],
              createdAt: '2026-10-23T15:00:00Z',
            },
            followUpAt: '2026-10-30T12:00:00Z',
          },
          { status: 201 },
        )
      }),
      http.post('/api/jobs/:jobId/drafts', async ({ request }) => {
        draftKind = ((await request.json()) as { kind: string }).kind
        return HttpResponse.json({ kind: 'AGRADECIMENTO', text: 'Obrigado pela conversa! [seu nome]', linkedinNote: null, language: 'pt', source: 'MODELO' })
      }),
    )
    renderWithProviders(<InterviewSection applicationId={7} jobId={20} status="ENTREVISTA" />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Registro' }))
    await userEvent.type(screen.getByLabelText(/O que perguntaram/), 'Como você usa Kafka?{enter}Fale de um conflito.')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar registro' }))

    expect(await screen.findByText(/Follow-up agendado para/)).toBeInTheDocument()
    expect(saved).toMatchObject({ stage: 'ENTREVISTA', questions: ['Como você usa Kafka?', 'Fale de um conflito.'] })
    expect(await screen.findByDisplayValue(/Obrigado pela conversa/)).toBeInTheDocument()
    expect(draftKind).toBe('AGRADECIMENTO')
  })
})
