import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { resume } from '@/test/fixtures'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ResumesPage } from './ResumesPage'

describe('ResumesPage', () => {
  it('mostra os avisos do que o ATS lê em cada versão', async () => {
    server.use(
      http.get('/api/resumes', () =>
        HttpResponse.json([
          resume({
            lint: [
              { code: 'DUAS_COLUNAS', message: 'Muitas linhas curtas e soltas: indício de duas colunas.' },
              { code: 'SEM_TELEFONE', message: 'Não achamos um telefone no texto.' },
            ],
          }),
        ]),
      ),
    )
    renderWithProviders(<ResumesPage />)

    await userEvent.click(await screen.findByText(/O que o ATS lê: 2 avisos/))
    expect(screen.getByText('Muitas linhas curtas e soltas: indício de duas colunas.')).toBeVisible()
  })
})
