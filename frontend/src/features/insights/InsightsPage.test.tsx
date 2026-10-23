import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { InsightReport } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { InsightsPage } from './InsightsPage'

const report: InsightReport = {
  id: 1,
  periodStart: '2026-09-26',
  periodEnd: '2026-10-23',
  topSkills: [
    { skill: 'Java', jobs: 12, covered: true },
    { skill: 'Kafka', jobs: 8, covered: false },
  ],
  gaps: [{ skill: 'Kafka', jobs: 8, avgScore: 62 }],
  funnel: {
    applied: 6,
    screening: 2,
    interview: 2,
    offer: 1,
    avgScoreApplied: 62,
    bySeniority: [{ group: 'SENIOR', applied: 6, interview: 2 }],
    scoreByRating: { NAO_GOSTEI: 77 },
  },
  resumePerformance: [{ resumeVersionId: 1, label: 'Backend 2026', applied: 5, interview: 2 }],
  suggestedFilters: [
    { reason: 'SENIORIDADE', count: 6, title: 'Exclua vagas "júnior"', detail: '6 vagas descartadas.', keyword: 'júnior' },
  ],
  aiAccuracy: 0.8,
  llmCostUsd: 0.1,
  recommendations: {
    gaps: [{ skill: 'Kafka', evidence: '8 vagas recentes pedem Kafka.', suggestion: 'Cite a fila de pagamentos.' }],
    bullets: [{ original: 'Construí APIs.', rewritten: 'Construí APIs que processam [X] pagamentos.', reason: 'Mostra escala.' }],
    linkedinKeywords: ['Spring Boot'],
    diagnosis: 'CURRICULO',
    diagnosisExplanation: 'Vagas aderentes, pouca conversão.',
    source: 'IA',
  },
  modelVersion: 'claude-opus-5-5|resume-advice@v1',
  createdAt: '2026-10-23T12:00:00Z',
}

describe('InsightsPage', () => {
  it('sem relatório, gera um na hora e mostra o diagnóstico, as lacunas e os bullets', async () => {
    let generated = false
    server.use(
      http.get('/api/insights/latest', () => new HttpResponse(null, { status: 204 })),
      http.get('/api/insights/usage', () => HttpResponse.json([])),
      http.post('/api/insights/generate', () => {
        generated = true
        return HttpResponse.json(report)
      }),
    )
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText('Nenhum relatório ainda.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /gerar sugestões/i }))

    expect(await screen.findByText('O currículo é o gargalo')).toBeInTheDocument()
    expect(generated).toBe(true)
    expect(screen.getByText('lacuna')).toBeInTheDocument()
    expect(screen.getByText('Construí APIs que processam [X] pagamentos.')).toBeInTheDocument()
    expect(screen.getByText('Exclua vagas "júnior"')).toBeInTheDocument()
    expect(screen.getByText('80%')).toBeInTheDocument()
  })
})
