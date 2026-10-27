import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { TailoredResume } from '@/api/queries'
import { renderWithProviders } from '@/test/render'
import { resume, tailoredResume } from '@/test/fixtures'
import { server } from '@/test/server'
import { TailoredResumeCard } from './TailoredResumeCard'

function serve(versions: TailoredResume[], enabled = true) {
  server.use(
    http.get('/api/tailored-resumes/availability', () => HttpResponse.json({ enabled, autoGenerate: enabled })),
    http.get('/api/resumes', () => HttpResponse.json([resume()])),
    http.get('/api/jobs/:jobId/tailored-resumes', () => HttpResponse.json(versions)),
  )
}

describe('TailoredResumeCard', () => {
  it('mostra o ganho em score e palavras-chave e alerta sobre skills que o currículo não comprova', async () => {
    serve([tailoredResume({ ats: { weightedCoverage: 89, titleInHeadline: false, overusedKeywords: ['Java'], cliches: ['proativo'] } })])
    renderWithProviders(<TailoredResumeCard jobId={20} />)

    expect(await screen.findByLabelText('Aderência 68')).toBeInTheDocument()
    expect(screen.getByText('89%')).toBeInTheDocument()
    expect(screen.getByText(/O título da vaga não aparece no headline/)).toBeInTheDocument()
    expect(screen.getByText('Repetidas mais de 4 vezes: Java.')).toBeInTheDocument()
    expect(screen.getByText(/Clichês para trocar por um fato: proativo/)).toBeInTheDocument()
    expect(screen.getByLabelText('Aderência 85')).toBeInTheDocument()
    expect(screen.getByText('3/5')).toBeInTheDocument()
    expect(screen.getByText('4/5')).toBeInTheDocument()
    expect(screen.getByText('Confira antes de enviar')).toBeInTheDocument()
    expect(screen.getByText(/AWS aparece no texto adaptado/)).toBeInTheDocument()
    expect(screen.getByText(/porque o currículo não comprova: Kubernetes/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ana Lima' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /o que mudou/i }))
    expect(screen.getByText('A vaga pede Spring Boot.')).toBeInTheDocument()
  })

  it('pede uma versão nova e acompanha a geração', async () => {
    let versions: TailoredResume[] = []
    let requested: unknown
    serve([])
    server.use(
      http.get('/api/jobs/:jobId/tailored-resumes', () => HttpResponse.json(versions)),
      http.post('/api/jobs/:jobId/tailored-resumes', async ({ request }) => {
        requested = await request.json()
        versions = [tailoredResume({ status: 'GERANDO', content: null, changes: [], keywords: [], coveredBefore: [], coveredAfter: [], unsupportedSkills: [], notCovered: [] })]
        return HttpResponse.json(versions[0], { status: 202 })
      }),
    )
    renderWithProviders(<TailoredResumeCard jobId={20} />)

    expect(await screen.findByText(/Nenhuma versão ainda/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /gerar currículo adaptado/i }))

    expect(await screen.findByRole('status')).toHaveTextContent('reescrevendo o currículo “Backend Java”')
    expect(requested).toEqual({ baseResumeId: 1 })
    expect(screen.getByRole('button', { name: /gerar nova versão/i })).toBeDisabled()
  })

  it('salva o texto adaptado como versão do currículo', async () => {
    let saved = false
    serve([tailoredResume()])
    server.use(
      http.post('/api/tailored-resumes/:id/resume-version', () => {
        saved = true
        return HttpResponse.json({ id: 9, label: 'Desenvolvedora Java · Banco' }, { status: 201 })
      }),
    )
    renderWithProviders(<TailoredResumeCard jobId={20} />)

    await userEvent.click(await screen.findByRole('button', { name: /salvar em currículos/i }))

    await vi.waitFor(() => expect(saved).toBe(true))
  })

  it('mostra o erro de uma geração que falhou', async () => {
    serve([tailoredResume({ status: 'FALHOU', content: null, lastError: 'Falha na chamada à Claude API: 500' })])
    renderWithProviders(<TailoredResumeCard jobId={20} />)

    expect(await screen.findByText('Não foi possível gerar esta versão')).toBeInTheDocument()
    expect(screen.getByText(/Claude API: 500/)).toBeInTheDocument()
  })

  it('explica que falta a chave da Claude API e não deixa pedir', async () => {
    serve([], false)
    renderWithProviders(<TailoredResumeCard jobId={20} />)

    expect(await screen.findByText('Precisa da chave da Claude API')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /gerar currículo adaptado/i })).toBeDisabled()
    expect(screen.queryByText(/Nenhuma versão ainda/)).not.toBeInTheDocument()
  })
})
