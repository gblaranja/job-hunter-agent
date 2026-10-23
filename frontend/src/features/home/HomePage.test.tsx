import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('explica o produto para quem não entrou e leva ao código e ao LinkedIn do autor', async () => {
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })))
    renderWithProviders(<HomePage />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('A busca de emprego no piloto automático')
    expect(screen.getByRole('heading', { name: 'Score de aderência com IA' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /ver o código no github/i })).toHaveAttribute(
      'href',
      'https://github.com/gblaranja/job-hunter-agent',
    )
    expect(screen.getByRole('link', { name: /falar no linkedin/i })).toHaveAttribute(
      'href',
      'https://www.linkedin.com/in/gabriel-laranja',
    )
    expect(screen.getByRole('link', { name: /^entrar$/i })).toHaveAttribute('href', '/login')
  })

  it('aponta cada link do menu para uma seção que existe', () => {
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })))
    const { container } = renderWithProviders(<HomePage />)

    const nav = screen.getByRole('navigation', { name: 'Seções' })
    for (const link of nav.querySelectorAll('a')) {
      expect(container.querySelector(link.getAttribute('href')!)).not.toBeNull()
    }
  })

  it('com sessão aberta, oferece voltar ao painel no lugar do GitHub', async () => {
    server.use(http.get('/api/auth/me', () => HttpResponse.json({ username: 'admin' })))
    renderWithProviders(<HomePage />)

    const links = await screen.findAllByRole('link', { name: /abrir o painel/i })
    expect(links).toHaveLength(2)
    for (const link of links) expect(link).toHaveAttribute('href', '/board')
    expect(screen.queryByRole('link', { name: /ver o código no github/i })).not.toBeInTheDocument()
  })
})
