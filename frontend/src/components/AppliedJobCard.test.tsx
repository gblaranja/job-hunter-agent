import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { appliedJob } from '@/test/fixtures'
import { AppliedJobCardView } from './AppliedJobCard'

describe('AppliedJobCard', () => {
  afterEach(() => vi.restoreAllMocks())

  it('mostra o link da vaga, o currículo enviado e a descrição salva', async () => {
    // Descrição mais alta que a área recolhida: o jsdom não calcula layout, então a altura é simulada.
    vi.spyOn(Element.prototype, 'scrollHeight', 'get').mockReturnValue(1000)
    renderWithProviders(<AppliedJobCardView applied={appliedJob()} />)

    const link = screen.getByRole('link', { name: /abrir vaga/i })
    expect(link).toHaveAttribute('href', 'https://empresa.example/vagas/42')
    expect(link).toHaveAttribute('target', '_blank')
    expect(screen.getByText('Link ativo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /backend java/i })).toBeInTheDocument()
    expect(screen.getByText('Apache Kafka')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /mostrar descrição completa/i }))
    expect(screen.getByRole('button', { name: /recolher descrição/i })).toBeInTheDocument()
  })

  it('não oferece recolher uma descrição curta', () => {
    renderWithProviders(<AppliedJobCardView applied={appliedJob()} />)

    expect(screen.queryByRole('button', { name: /mostrar descrição completa/i })).not.toBeInTheDocument()
  })

  it('avisa quando a vaga saiu do ar e você está vendo a cópia salva', () => {
    renderWithProviders(<AppliedJobCardView applied={appliedJob({ linkStatus: 'REMOVIDO' })} />)

    expect(screen.getByText('A vaga saiu do ar')).toBeInTheDocument()
    expect(screen.getByText(/cópia salva em/i)).toBeInTheDocument()
    expect(screen.getByText('Link removido')).toBeInTheDocument()
  })

  it('oferece salvar o PDF da página quando ainda não existe', () => {
    renderWithProviders(<AppliedJobCardView applied={appliedJob({ hasPagePdf: false, url: null })} />)

    expect(screen.getByRole('button', { name: /salvar pdf da página/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /copiar link/i })).toBeDisabled()
    expect(screen.getByText('Sem link salvo')).toBeInTheDocument()
  })
})
