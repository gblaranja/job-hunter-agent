import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('explica o produto para quem não entrou e leva ao LinkedIn do autor', async () => {
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })))
    renderWithProviders(<HomePage />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('A busca de emprego no piloto automático')
    expect(screen.getByRole('heading', { name: 'Score de aderência com IA' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /falar no linkedin/i })).toHaveAttribute(
      'href',
      'https://www.linkedin.com/in/gabriel-laranja',
    )
    expect(screen.getAllByRole('link', { name: /^entrar/i })[0]).toHaveAttribute('href', '/login')
  })

  it('com sessão aberta, oferece voltar ao painel', async () => {
    server.use(http.get('/api/auth/me', () => HttpResponse.json({ username: 'admin' })))
    renderWithProviders(<HomePage />)

    expect((await screen.findAllByRole('link', { name: /abrir o painel/i }))[0]).toHaveAttribute('href', '/board')
  })
})
