import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { CompanyItem } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { CompanyAtsForm } from './CompanyAtsForm'

const company: CompanyItem = {
  id: 9,
  name: 'Zeta',
  website: null,
  atsType: null,
  atsSlug: null,
  careersUrl: null,
  kind: 'CONTRATANTE',
  watchStatus: 'ALVO',
  notes: null,
  firstSeenAt: '2026-10-01T10:00:00Z',
  clientSectors: [],
  contractTypes: [],
  cnpj: null,
  authorizationType: null,
  gptwLevel: null,
  gptwYear: null,
  jobsTotal: 1,
  jobsActive: 1,
  jobsLast90Days: 1,
  reposts: 0,
  lastJobAt: null,
  avgDaysOpen: null,
  applications: 0,
}

describe('job board da empresa', () => {
  it('detecta o ATS pela página de carreira e salva o link com o ATS e o slug', async () => {
    let saved: unknown
    server.use(
      http.post('/api/companies/ats-check', () =>
        HttpResponse.json({ atsType: 'TEAMTAILOR', atsSlug: 'zeta', supported: true, status: 'ENCONTRADO' }),
      ),
      http.patch('/api/companies/9', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json({ ...company, atsType: 'TEAMTAILOR', atsSlug: 'zeta' })
      }),
    )
    renderWithProviders(<CompanyAtsForm company={company} />)

    await userEvent.type(screen.getByLabelText('Página de carreira'), 'https://zeta.teamtailor.com/jobs')
    await userEvent.click(screen.getByRole('button', { name: 'Detectar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Teamtailor (zeta): job board encontrado')
    expect(screen.getByLabelText('Slug')).toHaveValue('zeta')

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    await vi.waitFor(() =>
      expect(saved).toMatchObject({
        atsType: 'TEAMTAILOR',
        atsSlug: 'zeta',
        careersUrl: 'https://zeta.teamtailor.com/jobs',
      }),
    )
  })
})
