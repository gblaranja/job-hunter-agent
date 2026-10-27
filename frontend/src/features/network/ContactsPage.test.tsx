import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { ContactsPage } from './ContactsPage'

const ana = {
  id: 3,
  name: 'Ana Lima',
  role: 'RECRUTADOR',
  companyId: 5,
  companyName: 'Banco Exemplo',
  position: 'Tech Recruiter',
  linkedinUrl: 'https://www.linkedin.com/in/ana-lima',
  source: 'MANUAL',
  createdAt: '2026-10-20T12:00:00Z',
}

describe('ContactsPage', () => {
  it('lista os contatos e apaga um com as conversas', async () => {
    let deleted = false
    server.use(
      http.get('/api/contacts', () =>
        HttpResponse.json({ items: deleted ? [] : [ana], page: 0, size: 20, total: deleted ? 0 : 1 }),
      ),
      http.delete('/api/contacts/:id', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderWithProviders(<ContactsPage />)

    expect(await screen.findByText('Ana Lima')).toBeInTheDocument()
    expect(screen.getByText('Recrutador(a)')).toBeInTheDocument()
    expect(screen.getByText(/Tech Recruiter · Banco Exemplo/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Apagar Ana Lima' }))
    await userEvent.click(screen.getByRole('button', { name: 'Apagar' }))

    expect(await screen.findByText(/Nenhum contato/)).toBeInTheDocument()
  })
})
