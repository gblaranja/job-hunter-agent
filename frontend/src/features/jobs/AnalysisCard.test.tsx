import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { JobAnalysis } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { AnalysisCard } from './AnalysisCard'

const analysis: JobAnalysis = {
  status: 'COMPLETO',
  seniority: 'SENIOR',
  requiredSkills: ['Java', 'Spring Boot', 'AWS', 'Kafka'],
  niceToHave: ['Kubernetes'],
  summary: 'Evoluir o motor de crédito.',
  deterministicScore: 68,
  llmAdjustment: 5,
  matchScore: 73,
  matchedSkills: ['Java', 'Spring Boot'],
  gaps: ['AWS'],
  justification: 'Experiência com motores de decisão compensa a falta de AWS.',
  modelVersion: 'claude-haiku-5-5|extract-requirements@v1',
  analyzedAt: '2026-10-23T12:00:00Z',
  partialSkills: [{ skill: 'Kafka', via: 'RabbitMQ' }],
  knockouts: [
    { type: 'IDIOMA', text: 'Inglês fluente', value: 'INGLES:FLUENTE', status: 'NAO_ATENDE', reason: 'Seu inglês: avançado; a vaga pede fluente.' },
    { type: 'ANOS_EXPERIENCIA', text: '5 anos', value: '5', status: 'ATENDE', reason: 'Você tem 6 anos; a vaga pede 5.' },
  ],
  salary: { min: 12000, max: 15000, currency: 'BRL', period: 'MENSAL', regime: 'PJ', text: 'R$ 12 mil a R$ 15 mil' },
}

describe('AnalysisCard', () => {
  it('explica o score: regras, ajuste da IA, lacunas e justificativa', () => {
    renderWithProviders(<AnalysisCard jobId={1} analysis={analysis} />)

    expect(screen.getByLabelText('Aderência 73')).toBeInTheDocument()
    expect(screen.getByText('68')).toBeInTheDocument()
    expect(screen.getByText('+5')).toBeInTheDocument()
    expect(screen.getByText(/motores de decisão/)).toBeInTheDocument()
    expect(screen.getByText('Senioridade: Sênior')).toBeInTheDocument()
    expect(screen.getByText('AWS').closest('li')).toHaveTextContent('AWS')
    expect(screen.getAllByLabelText('lacuna')).toHaveLength(1)
    expect(screen.getAllByLabelText('você tem')).toHaveLength(2)
    expect(screen.getByText('1 eliminatório não atendido')).toBeInTheDocument()
    expect(screen.getByText('Seu inglês: avançado; a vaga pede fluente.')).toBeInTheDocument()
    expect(screen.getByText(/R\$\s?12\.000 a R\$\s?15\.000 por mês · PJ/)).toBeInTheDocument()
    expect(screen.getByLabelText('em parte').closest('li')).toHaveTextContent('Kafka≈ RabbitMQ')
  })

  it('pede o currículo quando não há score e permite reanalisar', async () => {
    let reanalyzed = false
    server.use(
      http.post('/api/jobs/:id/analysis', () => {
        reanalyzed = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(
      <AnalysisCard jobId={1} analysis={{ ...analysis, status: 'DETERMINISTICO', matchScore: null, llmAdjustment: null }} />,
    )

    expect(screen.getByText(/Envie um currículo/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /reanalisar/i }))
    await vi.waitFor(() => expect(reanalyzed).toBe(true))
  })
})
