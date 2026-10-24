import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router'
import { renderWithProviders } from '@/test/render'
import { MobileNav } from './MobileNav'

function CurrentPath() {
  return <p data-testid="path">{useLocation().pathname}</p>
}

function renderNav({ route = '/board', onLogout = () => {} }: { route?: string; onLogout?: () => void } = {}) {
  return renderWithProviders(
    <>
      <button type="button">Notificações</button>
      <MobileNav displayName="Gabriel" onLogout={onLogout} />
      <Routes>
        <Route path="*" element={<CurrentPath />} />
      </Routes>
    </>,
    { route },
  )
}

async function openMenu() {
  await userEvent.click(screen.getByRole('button', { name: 'Abrir menu' }))
  return screen.findByRole('dialog', { name: 'Menu' })
}

describe('MobileNav', () => {
  it('abre a gaveta "Menu" com as 10 telas do painel, na ordem atual', async () => {
    renderNav()

    const menu = await openMenu()

    expect(within(menu).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Caixa de vagas',
      'Kanban',
      'Minhas candidaturas',
      'Retornos',
      'Nova vaga',
      'Currículos',
      'Empresas',
      'Nichos',
      'Insights',
      'Configurações',
    ])
  })

  it('navega para a tela escolhida e fecha a gaveta', async () => {
    renderNav()

    const menu = await openMenu()
    await userEvent.click(within(menu).getByRole('link', { name: 'Empresas' }))

    expect(screen.getByTestId('path')).toHaveTextContent('/companies')
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
  })

  it('fecha com Esc e devolve o foco ao botão "Abrir menu"', async () => {
    renderNav()

    await openMenu()
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir menu' })).toHaveFocus()
  })

  it('fecha pelo botão "Fechar" e devolve o foco ao botão "Abrir menu"', async () => {
    renderNav()

    const menu = await openMenu()
    await userEvent.click(within(menu).getByRole('button', { name: 'Fechar' }))

    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir menu' })).toHaveFocus()
  })

  it('mostra o usuário logado e encerra a sessão pelo "Sair" do rodapé', async () => {
    const onLogout = vi.fn()
    renderNav({ onLogout })

    const menu = await openMenu()
    expect(within(menu).getByText('Gabriel')).toBeInTheDocument()
    await userEvent.click(within(menu).getByRole('button', { name: 'Sair' }))

    expect(onLogout).toHaveBeenCalledTimes(1)
  })

  it('marca a tela atual com aria-current="page"', async () => {
    renderNav({ route: '/board' })

    const menu = await openMenu()

    expect(within(menu).getByRole('link', { name: 'Kanban' })).toHaveAttribute('aria-current', 'page')
    expect(within(menu).getByRole('link', { name: 'Empresas' })).not.toHaveAttribute('aria-current')
  })

  it('deixa o restante da tela inacessível enquanto a gaveta está aberta', async () => {
    renderNav()
    expect(screen.getByRole('button', { name: 'Notificações' })).toBeInTheDocument()

    await openMenu()

    expect(screen.queryByRole('button', { name: 'Notificações' })).not.toBeInTheDocument()
  })

  it('fecha sem erro ao escolher a tela em que já está', async () => {
    renderNav({ route: '/board' })

    const menu = await openMenu()
    await userEvent.click(within(menu).getByRole('link', { name: 'Kanban' }))

    expect(screen.getByTestId('path')).toHaveTextContent('/board')
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
  })
})
