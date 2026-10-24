import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { CandidateProfile, SourceHealth } from '@/api/queries'
import { ThemeProvider } from '@/lib/theme'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { SettingsPage } from './SettingsPage'

const run = {
  id: 1,
  trigger: 'MANUAL',
  status: 'CONCLUIDA',
  startedAt: '2026-10-23T10:00:00Z',
  finishedAt: '2026-10-23T10:00:05Z',
  stats: { GMAIL_ALERTAS: { created: 4, duplicate: 1, excluded: 1, errors: 0 } },
  error: null,
}

function renderSettingsPage() {
  return renderWithProviders(
    <ThemeProvider>
      <SettingsPage />
    </ThemeProvider>,
  )
}

const emptyCandidateProfile: CandidateProfile = {
  acceptedContracts: ['CLT', 'PJ'],
  workAuthorizations: ['BR'],
  certifications: [],
  weeklyGoal: 12,
}

const emptyHealth: SourceHealth = { sources: [], unrecognizedAlerts: [] }

function serveSettings(runs: unknown[] = [], health: SourceHealth = emptyHealth) {
  server.use(
    http.get('/api/admin/sources/health', () => HttpResponse.json(health)),
    http.get('/api/notifications/telegram', () =>
      HttpResponse.json({ configured: true, types: ['VAGA_ALVO', 'FOLLOW_UP'] }),
    ),
    http.get('/api/admin/gmail/status', () =>
      HttpResponse.json({ configured: true, connected: true, accountEmail: 'eu@example.com', label: 'jobs', lastSyncAt: null }),
    ),
    http.get('/api/admin/collection-runs', () => HttpResponse.json(runs)),
    http.get('/api/candidate-profile', () => HttpResponse.json(emptyCandidateProfile)),
    http.get('/api/search-profiles', () =>
      HttpResponse.json([
        {
          id: 1,
          name: 'Java backend sênior',
          keywords: ['Java', 'Spring Boot'],
          excludedKeywords: ['PHP'],
          locations: ['Remoto'],
          remoteOnly: false,
          active: true,
        },
      ]),
    ),
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    window.localStorage.removeItem('job-hunter-theme')
    document.documentElement.classList.remove('dark')
  })

  it('mostra a conexão do Gmail e o perfil de busca', async () => {
    serveSettings()
    renderSettingsPage()

    expect(await screen.findByText('eu@example.com')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reconectar' })).toHaveAttribute('href', '/api/admin/gmail/connect')
    expect(await screen.findByText('Exclusões: PHP')).toBeInTheDocument()
  })

  it('dispara a coleta e lista o resultado', async () => {
    serveSettings()
    let collected = false
    server.use(
      http.post('/api/admin/collect', () => {
        collected = true
        serveSettings([run])
        return HttpResponse.json(run)
      }),
    )
    renderSettingsPage()

    await userEvent.click(await screen.findByRole('button', { name: /coletar agora/i }))

    await vi.waitFor(() => expect(collected).toBe(true))
    expect(await screen.findByText('Concluída')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('mostra a saúde das fontes e os alertas que não viraram vaga', async () => {
    serveSettings([], {
      sources: [
        {
          source: 'GREENHOUSE',
          available: true,
          lastRunAt: '2026-10-23T10:00:00Z',
          lastNewJobAt: null,
          newJobsLast7Days: 0,
          errorsLast7Days: 3,
          failingRunsInRow: 3,
        },
      ],
      unrecognizedAlerts: [
        { emailId: 7, gmailId: '18a2b3c', sender: 'alertas@gupy.io', subject: 'Novas vagas', receivedAt: '2026-10-23T09:00:00Z' },
      ],
    })
    renderSettingsPage()

    expect(await screen.findByText('falhou nas últimas 3 coletas')).toBeInTheDocument()
    expect(screen.getByText('nenhuma em 30 dias')).toBeInTheDocument()
    expect(screen.getByText('alertas@gupy.io')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /abrir no gmail/i })).toHaveAttribute(
      'href',
      'https://mail.google.com/mail/u/0/#all/18a2b3c',
    )
  })

  it('mostra o Telegram configurado e envia a mensagem de teste', async () => {
    serveSettings()
    let tested = false
    server.use(
      http.post('/api/notifications/telegram/test', () => {
        tested = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderSettingsPage()

    expect(await screen.findByText(/Vaga de empresa-alvo, Follow-up/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /enviar teste/i }))
    await vi.waitFor(() => expect(tested).toBe(true))
  })

  it('permite escolher e salva o tema escuro', async () => {
    serveSettings()
    const user = userEvent.setup()
    renderSettingsPage()

    const themeSelect = await screen.findByRole('combobox', { name: 'Tema' })
    expect(themeSelect).toHaveTextContent('Padrão do sistema')
    await user.click(themeSelect)
    await user.click(screen.getByRole('option', { name: 'Escuro' }))

    expect(window.localStorage.getItem('job-hunter-theme')).toBe('dark')
    expect(document.documentElement).toHaveClass('dark')
  })
})
