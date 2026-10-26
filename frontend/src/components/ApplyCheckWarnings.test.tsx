import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ApplyCheckWarnings } from './ApplyCheckWarnings'

describe('ApplyCheckWarnings', () => {
  it('lista os avisos antes de aplicar', async () => {
    server.use(
      http.get('/api/jobs/7/apply-check', () =>
        HttpResponse.json({
          jobId: 7,
          linkStatus: 'REMOVIDO',
          warnings: [
            { code: 'LINK_FORA_DO_AR', message: 'O link da vaga parece fora do ar ou encerrado.' },
            { code: 'ELIMINATORIO', message: 'Eliminatório que você não atende: Pede 8 anos' },
          ],
        }),
      ),
    )
    renderWithProviders(<ApplyCheckWarnings jobId={7} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Antes de aplicar, confira')
    expect(screen.getByText('Eliminatório que você não atende: Pede 8 anos')).toBeInTheDocument()
  })
})
