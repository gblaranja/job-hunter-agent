import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { FunnelBreakdown } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ConversionSection } from './ConversionSection'

const funnel: FunnelBreakdown = {
  applied: 8,
  interview: 3,
  offer: 1,
  bySource: [
    { group: 'GREENHOUSE', applied: 5, interview: 2, offer: 1 },
    { group: 'LINKEDIN_ALERTA', applied: 3, interview: 1, offer: 0 },
  ],
  byChannel: [
    { group: 'INDICACAO', applied: 2, interview: 2, offer: 1 },
    { group: 'EASY_APPLY', applied: 6, interview: 1, offer: 0 },
  ],
  byTimeToApply: [{ group: 'ATE_1_DIA', applied: 6, interview: 3, offer: 1 }],
  byTailoring: [{ group: 'ADAPTADO', applied: 7, interview: 3, offer: 1 }],
  medianDaysToFirstResponse: 2.5,
  rankingQuality: { evaluated: 30, positives: 12, topCount: 20, topPositives: 11, avgScorePositive: 78, avgScoreNegative: 55 },
}

describe('ConversionSection', () => {
  it('abre a conversão por fonte e canal e marca os grupos pequenos', async () => {
    server.use(http.get('/api/insights/funnel', () => HttpResponse.json(funnel)))
    renderWithProviders(<ConversionSection />)

    expect(await screen.findByText('38%')).toBeInTheDocument() // 3 de 8 candidaturas
    expect(screen.getByText('2,5 dias')).toBeInTheDocument()
    expect(screen.getByText('55%')).toBeInTheDocument() // 11 das 20 de maior score

    expect(within(screen.getByRole('row', { name: /Até 1 dia/ })).getByText('50%')).toBeInTheDocument()
    const source = screen.getByRole('row', { name: /Greenhouse/ })
    expect(within(source).getByText('40%')).toBeInTheDocument()
    // Menos de 5 candidaturas: a taxa não aparece.
    expect(within(screen.getByRole('row', { name: /LinkedIn/ })).getByText('poucos dados')).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Indicação/ })).getByText('poucos dados')).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Easy Apply/ })).getByText('17%')).toBeInTheDocument()
  })

  it('sem candidaturas, explica o que vai aparecer', async () => {
    server.use(
      http.get('/api/insights/funnel', () =>
        HttpResponse.json({
          ...funnel,
          applied: 0,
          interview: 0,
          offer: 0,
          bySource: [],
          byChannel: [],
          byTimeToApply: [],
          byTailoring: [],
          medianDaysToFirstResponse: null,
          rankingQuality: { evaluated: 0, positives: 0, topCount: 0, topPositives: 0, avgScorePositive: null, avgScoreNegative: null },
        }),
      ),
    )
    renderWithProviders(<ConversionSection />)

    expect(await screen.findByText(/Sem candidaturas ainda/)).toBeInTheDocument()
  })
})
