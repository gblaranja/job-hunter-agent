import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { queryKeys } from '@/api/queries'
import { TooltipProvider } from '@/components/ui/tooltip'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { AppLayout } from './AppLayout'

function renderPanel(route = '/board') {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>Tela de login</p>} />
      <Route element={<AppLayout />}>
        <Route path="board" element={<h1>Quadro</h1>} />
        <Route path="companies" element={<h1>Lista de empresas</h1>} />
      </Route>
    </Routes>,
    { route },
  )
}

describe('AppLayout', () => {
  beforeEach(() => {
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json({ username: 'gabriel', displayName: 'Gabriel' })),
      http.get('/api/notifications', () => HttpResponse.json([])),
    )
  })

  it('marca a tela atual com aria-current="page" na sidebar', async () => {
    renderPanel('/board')

    const sidebar = await screen.findByRole('navigation', { name: 'Principal' })

    expect(within(sidebar).getByRole('link', { name: 'Kanban' })).toHaveAttribute('aria-current', 'page')
    expect(within(sidebar).getByRole('link', { name: 'Empresas' })).not.toHaveAttribute('aria-current')
  })

  it('o primeiro Tab chega ao atalho "Pular para o conteúdo"', async () => {
    renderPanel('/board')
    await screen.findByRole('heading', { name: 'Quadro' })

    await userEvent.tab()

    const skip = screen.getByRole('link', { name: 'Pular para o conteúdo' })
    expect(skip).toHaveFocus()
    expect(skip).toHaveAttribute('href', '#conteudo')
  })

  it('o atalho leva o foco para a região principal', async () => {
    renderPanel('/board')
    await screen.findByRole('heading', { name: 'Quadro' })

    await userEvent.click(screen.getByRole('link', { name: 'Pular para o conteúdo' }))

    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('no primeiro carregamento, não move o foco para a região principal', async () => {
    renderPanel('/board')
    await screen.findByRole('heading', { name: 'Quadro' })

    expect(screen.getByRole('main')).not.toHaveFocus()
    expect(document.body).toHaveFocus()
  })

  it('no primeiro carregamento com a sessão já em cache (vindo do login), não move o foco para a região principal', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(queryKeys.me, { username: 'gabriel', displayName: 'Gabriel' })
    render(
      <QueryClientProvider client={client}>
        <TooltipProvider>
          <MemoryRouter initialEntries={['/board']}>
            <Routes>
              <Route element={<AppLayout />}>
                <Route path="board" element={<h1>Quadro</h1>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </TooltipProvider>
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { name: 'Quadro' })).toBeInTheDocument()
    expect(screen.getByRole('main')).not.toHaveFocus()
    expect(document.body).toHaveFocus()
  })

  it('ao trocar de tela pela sidebar, move o foco para a região principal', async () => {
    renderPanel('/board')
    const sidebar = await screen.findByRole('navigation', { name: 'Principal' })

    await userEvent.click(within(sidebar).getByRole('link', { name: 'Empresas' }))

    expect(await screen.findByRole('heading', { name: 'Lista de empresas' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('ao voltar para a tela inicial, também move o foco para a região principal', async () => {
    renderPanel('/board')
    const sidebar = await screen.findByRole('navigation', { name: 'Principal' })

    await userEvent.click(within(sidebar).getByRole('link', { name: 'Empresas' }))
    await screen.findByRole('heading', { name: 'Lista de empresas' })
    screen.getByRole('link', { name: 'Kanban' }).focus()
    await userEvent.click(within(sidebar).getByRole('link', { name: 'Kanban' }))

    expect(await screen.findByRole('heading', { name: 'Quadro' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('depois de navegar pela gaveta, reabrir e fechar com Esc devolve o foco ao botão do menu', async () => {
    renderPanel('/board')

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir menu' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Menu' })).getByRole('link', { name: 'Empresas' }))
    await screen.findByRole('heading', { name: 'Lista de empresas' })
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menu' }))
    await screen.findByRole('dialog', { name: 'Menu' })
    await userEvent.keyboard('{Escape}')

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Abrir menu' })).toHaveFocus()
  })

  it('ao trocar de tela pela gaveta, o foco fica na região principal e não volta ao botão do menu', async () => {
    renderPanel('/board')

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir menu' }))
    const menu = await screen.findByRole('dialog', { name: 'Menu' })
    await userEvent.click(within(menu).getByRole('link', { name: 'Empresas' }))

    expect(await screen.findByRole('heading', { name: 'Lista de empresas' })).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('ao sair pela gaveta, vai para o login sem deixar a gaveta aberta', async () => {
    server.use(http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })))
    renderPanel('/board')

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir menu' }))
    const menu = await screen.findByRole('dialog', { name: 'Menu' })
    await userEvent.click(within(menu).getByRole('button', { name: 'Sair' }))

    expect(await screen.findByText('Tela de login')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
