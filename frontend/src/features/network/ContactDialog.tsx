import { useState, type FormEvent, type ReactNode } from 'react'
import { toast } from 'sonner'
import { useSaveContact, type ContactRole, type ContactView } from '@/api/queries'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { contactRoleLabels } from '@/lib/labels'

const roles = Object.keys(contactRoleLabels) as ContactRole[]

type Props = {
  trigger: ReactNode
  contact?: ContactView
  /** Empresa já preenchida ao cadastrar a partir de uma vaga. */
  defaultCompany?: string
}

/** Cadastro ou edição de um contato, só com o necessário: e-mail apenas se você quiser anotar. */
export function ContactDialog({ trigger, contact, defaultCompany }: Props) {
  const save = useSaveContact()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(contact?.name ?? '')
  const [role, setRole] = useState<ContactRole>(contact?.role ?? 'RECRUTADOR')
  const [company, setCompany] = useState(contact?.companyName ?? defaultCompany ?? '')
  const [position, setPosition] = useState(contact?.position ?? '')
  const [linkedinUrl, setLinkedinUrl] = useState(contact?.linkedinUrl ?? '')
  const [email, setEmail] = useState(contact?.email ?? '')
  const [notes, setNotes] = useState(contact?.notes ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate(
      {
        id: contact?.id,
        name,
        role,
        company: company || null,
        position: position || null,
        linkedinUrl: linkedinUrl || null,
        email: email || null,
        notes: notes || null,
      },
      {
        onSuccess: () => {
          toast.success(contact ? 'Contato atualizado.' : 'Contato cadastrado.')
          setOpen(false)
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{contact ? 'Editar contato' : 'Novo contato'}</DialogTitle>
            <DialogDescription>
              Guarde só o que ajuda na busca. A empresa é ligada ao cadastro pelo nome, sem criar empresa nova.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="contact-name">Nome</Label>
              <Input id="contact-name" required value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="contact-role">Papel</Label>
              <Select value={role} onValueChange={(value) => setRole(value as ContactRole)}>
                <SelectTrigger id="contact-role" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {roles.map((option) => (
                    <SelectItem key={option} value={option}>
                      {contactRoleLabels[option]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="contact-company">Empresa</Label>
              <Input id="contact-company" value={company} onChange={(event) => setCompany(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="contact-position">Cargo</Label>
              <Input id="contact-position" value={position} onChange={(event) => setPosition(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="contact-linkedin">Perfil do LinkedIn</Label>
              <Input
                id="contact-linkedin"
                type="url"
                placeholder="https://www.linkedin.com/in/…"
                value={linkedinUrl}
                onChange={(event) => setLinkedinUrl(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="contact-email">E-mail (opcional)</Label>
              <Input id="contact-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="contact-notes">Notas</Label>
            <Textarea id="contact-notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={save.isPending}>
              Salvar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
