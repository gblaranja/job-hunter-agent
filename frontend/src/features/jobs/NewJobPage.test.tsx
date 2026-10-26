import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { NewJobPage } from './NewJobPage'

describe('NewJobPage', () => {
  it('preenche o formulário pelo link da vaga', async () => {
    let requested: unknown
    server.use(
      http.get('/api/resumes', () => HttpResponse.json([])),
      http.post('/api/jobs/import/preview', async ({ request }) => {
        requested = await request.json()
        return HttpResponse.json({
          title: 'Pessoa Desenvolvedora Backend',
          company: 'Banco Alfa',
          location: 'Remoto (Brasil)',
          workModel: 'REMOTO',
          scope: null,
          salaryRange: 'R$ 15.000–19.000/mês',
          url: 'https://boards.greenhouse.io/alfa/jobs/1',
          description: 'Time de pagamentos.',
          method: 'JSON_LD',
          warnings: ['Essa vaga já está cadastrada.'],
          existingJobId: 9,
        })
      }),
    )
    const user = userEvent.setup()
    renderWithProviders(<NewJobPage />)

    await user.type(screen.getByLabelText('Link'), 'https://boards.greenhouse.io/alfa/jobs/1')
    await user.click(screen.getByRole('button', { name: 'Preencher pelo link' }))

    expect(await screen.findByDisplayValue('Pessoa Desenvolvedora Backend')).toBeInTheDocument()
    expect(screen.getByLabelText('Empresa')).toHaveValue('Banco Alfa')
    expect(screen.getByLabelText('Faixa salarial')).toHaveValue('R$ 15.000–19.000/mês')
    expect(screen.getByText('Essa vaga já está cadastrada.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Abrir a vaga cadastrada' })).toHaveAttribute('href', '/jobs/9')
    expect(requested).toEqual({ url: 'https://boards.greenhouse.io/alfa/jobs/1' })
  })
})
