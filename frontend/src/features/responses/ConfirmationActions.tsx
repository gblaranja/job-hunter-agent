import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useChangeStatus, useRegisterFromEmail, useResumes, useReviewEmail, type EmailItem } from '@/api/queries'
import { ResumePickerDialog } from '@/components/ResumePickerDialog'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ArrowRight, FilePlus2 } from 'lucide-react'

/**
 * Ações do e-mail "recebemos sua candidatura": mover a candidatura em Interesse para Aplicada ou registrar a
 * candidatura que não estava no agente, com a data do e-mail.
 */
export function ConfirmationActions({ email }: { email: EmailItem }) {
  if (email.applicationId != null && email.applicationStatus === 'INTERESSE')
    return <MoveToApplied email={email} applicationId={email.applicationId} />
  if (email.applicationId == null) return <RegisterFromEmail email={email} />
  return null
}

function MoveToApplied({ email, applicationId }: { email: EmailItem; applicationId: number }) {
  const changeStatus = useChangeStatus()
  const review = useReviewEmail()
  const [picking, setPicking] = useState(false)

  return (
    <>
      <p className="text-sm">
        A candidatura em <strong>{email.applicationTitle}</strong> ainda está em Interesse.
      </p>
      <Button size="sm" variant="secondary" onClick={() => setPicking(true)}>
        <ArrowRight /> Mover para Aplicada
      </Button>
      {picking && (
        <ResumePickerDialog
          open
          title="Mover para Aplicada"
          description="Qual versão do currículo você enviou para esta vaga?"
          pending={changeStatus.isPending}
          onCancel={() => setPicking(false)}
          onConfirm={({ resumeVersionId }) =>
            changeStatus.mutate(
              { applicationId, status: 'APLICADA', resumeVersionId: resumeVersionId ?? null, note: null },
              {
                onSuccess: () => {
                  setPicking(false)
                  review.mutate({ id: email.id, classification: 'CONFIRMACAO_CANDIDATURA', applicationId })
                  toast.success('Candidatura movida para Aplicada.')
                },
                onError: (error) => toast.error(error.message),
              },
            )
          }
        />
      )}
    </>
  )
}

function RegisterFromEmail({ email }: { email: EmailItem }) {
  const register = useRegisterFromEmail()
  const resumes = useResumes()
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState(email.jobGuess ?? '')
  const [company, setCompany] = useState(email.companyGuess ?? '')
  const selectable = (resumes.data ?? []).filter((resume) => !resume.archived)
  const [resumeId, setResumeId] = useState<string>('')
  const chosen = resumeId || String(selectable.find((resume) => resume.isDefault)?.id ?? selectable[0]?.id ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    register.mutate(
      { emailId: email.id, resumeVersionId: Number(chosen), title: title || null, company: company || null },
      {
        onSuccess: () => {
          toast.success('Candidatura registrada em Aplicada.')
          setOpen(false)
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <>
      <p className="text-sm">Candidatura não registrada no agente.</p>
      <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
        <FilePlus2 /> Registrar candidatura
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <form onSubmit={submit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Registrar candidatura</DialogTitle>
              <DialogDescription>
                A vaga entra como cadastro manual e a candidatura em Aplicada, com a data do e-mail.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor={`from-email-title-${email.id}`}>Cargo</Label>
              <Input id={`from-email-title-${email.id}`} required value={title} onChange={(event) => setTitle(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor={`from-email-company-${email.id}`}>Empresa</Label>
              <Input
                id={`from-email-company-${email.id}`}
                required
                value={company}
                onChange={(event) => setCompany(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor={`from-email-resume-${email.id}`}>Currículo enviado</Label>
              <Select value={chosen} onValueChange={setResumeId}>
                <SelectTrigger id={`from-email-resume-${email.id}`} className="w-full">
                  <SelectValue placeholder="Cadastre um currículo antes" />
                </SelectTrigger>
                <SelectContent>
                  {selectable.map((resume) => (
                    <SelectItem key={resume.id} value={String(resume.id)}>
                      {resume.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={register.isPending || !chosen}>
                Registrar
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
