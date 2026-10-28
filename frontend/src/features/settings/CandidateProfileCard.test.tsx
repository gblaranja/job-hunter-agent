import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { CandidateProfile, CandidateProfileCommand } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { CandidateProfileCard } from './CandidateProfileCard'

const profile: CandidateProfile = {
  acceptedContracts: ['CLT', 'PJ'],
  workAuthorizations: ['BR'],
  certifications: [],
  weeklyGoal: 12,
  suggestedYearsOfExperience: 7,
  suggestedEnglishLevel: 'AVANCADO',
  suggestedFrom: 'Backend Java',
}

describe('CandidateProfileCard', () => {
  it('usa a sugestão do currículo e salva os dados', async () => {
    let saved: CandidateProfileCommand | undefined
    server.use(
      http.get('/api/candidate-profile', () => HttpResponse.json(profile)),
      http.put('/api/candidate-profile', async ({ request }) => {
        saved = (await request.json()) as CandidateProfileCommand
        return HttpResponse.json({ ...profile, ...saved, updatedAt: '2026-10-24T12:00:00Z' })
      }),
    )
    const user = userEvent.setup()
    renderWithProviders(<CandidateProfileCard />)

    expect(await screen.findByText(/7 anos de experiência e inglês avançado/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Usar sugestão' }))
    expect(screen.getByLabelText('Anos de experiência')).toHaveValue('7')
    await user.type(screen.getByLabelText('Pretensão CLT (R$ por mês, bruto)'), '15000')
    await user.click(screen.getByLabelText('PJ'))
    await user.click(screen.getByRole('button', { name: 'Salvar dados' }))

    await vi.waitFor(() => expect(saved).toBeDefined())
    expect(saved).toMatchObject({
      yearsOfExperience: 7,
      englishLevel: 'AVANCADO',
      desiredSalaryClt: 15000,
      acceptedContracts: ['CLT'],
      workAuthorizations: ['BR'],
      weeklyGoal: 12,
    })
  })
})
