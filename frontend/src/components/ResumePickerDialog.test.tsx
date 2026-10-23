import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@/test/render'
import { resume } from '@/test/fixtures'
import { server } from '@/test/server'
import { ResumePickerDialog } from './ResumePickerDialog'

function serveResumes() {
  server.use(
    http.get('/api/resumes', () =>
      HttpResponse.json([
        resume({ id: 1, label: 'Backend Java', isDefault: true }),
        resume({ id: 2, label: 'Dados', isDefault: false }),
        resume({ id: 3, label: 'Antigo', isDefault: false, archived: true }),
      ]),
    ),
  )
}

describe('ResumePickerDialog', () => {
  it('sugere o currículo padrão, esconde os arquivados e confirma a escolha', async () => {
    serveResumes()
    const onConfirm = vi.fn()
    renderWithProviders(
      <ResumePickerDialog open title="Mover para Aplicada" description="" onCancel={() => {}} onConfirm={onConfirm} />,
    )

    expect(await screen.findByRole('radio', { name: /backend java/i })).toBeChecked()
    expect(screen.queryByText('Antigo')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('radio', { name: /dados/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(onConfirm).toHaveBeenCalledWith({ resumeVersionId: 2, note: undefined })
  })

  it('só confirma a reabertura com uma nota', async () => {
    serveResumes()
    const onConfirm = vi.fn()
    renderWithProviders(
      <ResumePickerDialog
        open
        title="Mover para Entrevista"
        description=""
        requireResume={false}
        requireNote
        onCancel={() => {}}
        onConfirm={onConfirm}
      />,
    )

    const confirm = screen.getByRole('button', { name: 'Confirmar' })
    expect(confirm).toBeDisabled()

    await userEvent.type(screen.getByLabelText(/por que reabrir/i), 'Recrutadora retomou o processo')
    await userEvent.click(confirm)

    expect(onConfirm).toHaveBeenCalledWith({ resumeVersionId: undefined, note: 'Recrutadora retomou o processo' })
  })
})
