import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { PlatformProfilesCard } from './PlatformProfilesCard'

describe('PlatformProfilesCard', () => {
  it('mostra os textos dos perfis, o que mudou e copia', async () => {
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    renderWithProviders(
      <PlatformProfilesCard
        recommendations={{
          gaps: [],
          bullets: [],
          linkedinKeywords: [],
          diagnosis: 'CURRICULO',
          diagnosisExplanation: '',
          source: 'IA',
          linkedinProfile: { headline: 'Backend Java Sênior | Kafka', about: 'Desenvolvo crédito.', skills: ['Java'] },
          gupyProfile: {
            skills: 'Java, Kafka',
            experiences: [{ role: 'Desenvolvedora', company: 'Banco Azul', description: 'APIs de crédito.' }],
          },
          profileChanges: ['Headline do LinkedIn mudou.'],
        }}
      />,
    )

    expect(screen.getByText('Headline do LinkedIn mudou.')).toBeInTheDocument()
    expect(screen.getByText('Desenvolvedora · Banco Azul')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Copiar Título (headline)' }))
    expect(writeText).toHaveBeenCalledWith('Backend Java Sênior | Kafka')
  })
})
