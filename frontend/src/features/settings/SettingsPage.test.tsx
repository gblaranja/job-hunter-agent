import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
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

function serveSettings(runs: unknown[] = []) {
  server.use(
    http.get('/api/admin/gmail/status', () =>
      HttpResponse.json({ configured: true, connected: true, accountEmail: 'eu@example.com', label: 'jobs', lastSyncAt: null }),
    ),
    http.get('/api/admin/collection-runs', () => HttpResponse.json(runs)),
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
