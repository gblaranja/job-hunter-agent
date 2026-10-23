import { useState } from 'react'
import { Link } from 'react-router'
import { useResumes } from '@/api/queries'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'

type Props = {
  open: boolean
  title: string
  description: string
  /** Pede também uma nota (reabrir candidatura encerrada). */
  requireNote?: boolean
  /** Pede a escolha do currículo enviado. */
  requireResume?: boolean
  pending?: boolean
  onCancel: () => void
  onConfirm: (choice: { resumeVersionId?: number; note?: string }) => void
}

/** Confirmação de uma mudança de etapa que precisa do currículo enviado e/ou de uma nota. */
export function ResumePickerDialog({
  open,
  title,
  description,
  requireNote = false,
  requireResume = true,
  pending = false,
  onCancel,
  onConfirm,
}: Props) {
  const resumes = useResumes()
  const selectable = (resumes.data ?? []).filter((resume) => !resume.archived)
  const defaultId = selectable.find((resume) => resume.isDefault)?.id
  const [chosen, setChosen] = useState<number | undefined>()
  const [note, setNote] = useState('')
  const resumeVersionId = chosen ?? defaultId

  const canConfirm = (!requireResume || resumeVersionId !== undefined) && (!requireNote || note.trim() !== '')

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        {requireResume && (
          <fieldset className="space-y-2">
            <legend className="mb-2 text-sm font-medium">Versão do currículo enviada</legend>
            {resumes.isLoading && <p className="text-sm text-muted-foreground">Carregando currículos…</p>}
            {resumes.isSuccess && selectable.length === 0 && (
              <p className="text-sm text-muted-foreground">
                Nenhum currículo cadastrado.{' '}
                <Link to="/resumes" className="text-primary underline">
                  Envie uma versão
                </Link>{' '}
                antes de registrar a aplicação.
              </p>
            )}
            {selectable.map((resume) => (
              <label
                key={resume.id}
                className={cn(
                  'flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm transition-colors',
                  resumeVersionId === resume.id ? 'border-primary bg-accent' : 'hover:bg-muted',
                )}
              >
                <input
                  type="radio"
                  name="resume"
                  className="mt-1 accent-[var(--primary)]"
                  checked={resumeVersionId === resume.id}
                  onChange={() => setChosen(resume.id)}
                />
                <span>
                  <span className="font-medium">{resume.label}</span>
                  {resume.isDefault && <span className="ml-2 text-xs text-muted-foreground">padrão</span>}
                  <span className="block text-xs text-muted-foreground">
                    {resume.targetRole ? `${resume.targetRole} · ` : ''}enviado em {formatDate(resume.createdAt)}
                  </span>
                </span>
              </label>
            ))}
          </fieldset>
        )}

        {requireNote && (
          <div className="space-y-2">
            <Label htmlFor="move-note">Por que reabrir?</Label>
            <Textarea
              id="move-note"
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Ex.: a recrutadora retomou o processo"
            />
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            disabled={!canConfirm || pending}
            onClick={() =>
              onConfirm({
                resumeVersionId: requireResume ? resumeVersionId : undefined,
                note: requireNote ? note.trim() : undefined,
              })
            }
          >
            Confirmar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
