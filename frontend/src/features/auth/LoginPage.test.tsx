import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { LoginPage } from './LoginPage'

describe('LoginPage', () => {
  beforeEach(() => {
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })))
  })

  it('mostra "Entrar com Google" quando a instalação tem o login configurado', async () => {
    server.use(http.get('/api/auth/providers', () => HttpResponse.json({ password: true, google: true })))
    renderWithProviders(<LoginPage />, { route: '/login' })

    expect(await screen.findByRole('link', { name: /entrar com google/i })).toHaveAttribute('href', '/api/auth/google')
  })

  it('sem o Google configurado, fica só o usuário e senha', async () => {
    server.use(http.get('/api/auth/providers', () => HttpResponse.json({ password: true, google: false })))
    renderWithProviders(<LoginPage />, { route: '/login' })

    expect(await screen.findByLabelText('Usuário')).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByRole('link', { name: /entrar com google/i })).not.toBeInTheDocument())
  })

  it('explica quando a conta Google não tem acesso', async () => {
    server.use(http.get('/api/auth/providers', () => HttpResponse.json({ password: true, google: true })))
    renderWithProviders(<LoginPage />, { route: '/login?google=negado' })

    expect(await screen.findByText(/não tem acesso a esta instalação/i)).toBeInTheDocument()
  })
})
