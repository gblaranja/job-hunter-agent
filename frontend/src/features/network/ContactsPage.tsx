import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { useContacts, useDeleteContact, type ContactRole, type ContactView } from '@/api/queries'
import { LinkedinIcon } from '@/components/brand/LinkedinIcon'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { formatDate } from '@/lib/format'
import { contactRoleLabels } from '@/lib/labels'
import { MessageSquarePlus, Pencil, Trash2, UserPlus } from 'lucide-react'
import { ContactDialog } from './ContactDialog'
import { InteractionDialog } from './InteractionDialog'

const ALL = 'TODOS'
const roles = Object.keys(contactRoleLabels) as ContactRole[]

/** Recrutadores, gestores, quem pode indicar e as conexões do LinkedIn, com o histórico de conversas. */
export function ContactsPage() {
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<ContactRole | typeof ALL>(ALL)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const q = useDebouncedValue(search.trim())
  const contacts = useContacts({ q, role: role === ALL ? undefined : role, page, size })

  return (
    <>
      <PageHeader
        title="Contatos"
        description="Quem pode te indicar ou já falou com você. Guarde só o necessário; apagar um contato apaga as conversas dele."
        actions={
          <ContactDialog
            trigger={
              <Button>
                <UserPlus /> Novo contato
              </Button>
            }
          />
        }
      />
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          aria-label="Buscar contatos"
          placeholder="Nome, empresa ou cargo"
          className="max-w-xs"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPage(0)
          }}
        />
        <Select
          value={role}
          onValueChange={(value) => {
            setRole(value as ContactRole | typeof ALL)
            setPage(0)
          }}
        >
          <SelectTrigger aria-label="Papel" className="w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todos os papéis</SelectItem>
            {roles.map((option) => (
              <SelectItem key={option} value={option}>
                {contactRoleLabels[option]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {contacts.isLoading && <LoadingBlock />}
      {contacts.error && <ErrorBlock error={contacts.error} />}
      {contacts.data && (
        <>
          {contacts.data.items.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Nenhum contato. Cadastre um ou importe as suas conexões do LinkedIn em{' '}
              <Link to="/settings" className="underline">
                Configurações
              </Link>
              .
            </p>
          ) : (
            <Card>
              <CardContent className="divide-y">
                {contacts.data.items.map((contact) => (
                  <ContactRow key={contact.id} contact={contact} />
                ))}
              </CardContent>
            </Card>
          )}
          <Pagination
            className="mt-4"
            page={page}
            size={size}
            total={contacts.data.total}
            onPageChange={setPage}
            onSizeChange={(value) => {
              setSize(value)
              setPage(0)
            }}
            itemLabel="contatos"
          />
        </>
      )}
    </>
  )
}

function ContactRow({ contact }: { contact: ContactView }) {
  return (
    <div className="flex flex-wrap items-center gap-3 py-3 first:pt-0 last:pb-0">
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-2 font-medium">
          {contact.name}
          <Badge variant="secondary" className="font-normal">
            {contactRoleLabels[contact.role]}
          </Badge>
        </p>
        <p className="text-sm text-muted-foreground">
          {[contact.position, contact.companyName].filter(Boolean).join(' · ') || 'Sem empresa'}
          {contact.companyId != null && (
            <>
              {' · '}
              <Link to={`/companies/${contact.companyId}`} className="underline">
                ver empresa
              </Link>
            </>
          )}
        </p>
        {contact.lastInteractionAt && (
          <p className="text-xs text-muted-foreground">Última conversa em {formatDate(contact.lastInteractionAt)}</p>
        )}
      </div>
      <div className="flex items-center gap-1">
        {contact.linkedinUrl && (
          <Button asChild variant="ghost" size="icon-sm" aria-label={`Perfil de ${contact.name} no LinkedIn`}>
            <a href={contact.linkedinUrl} target="_blank" rel="noopener noreferrer">
              <LinkedinIcon />
            </a>
          </Button>
        )}
        <InteractionDialog
          contact={contact}
          trigger={
            <Button variant="ghost" size="icon-sm" aria-label={`Registrar conversa com ${contact.name}`}>
              <MessageSquarePlus />
            </Button>
          }
        />
        <ContactDialog
          contact={contact}
          trigger={
            <Button variant="ghost" size="icon-sm" aria-label={`Editar ${contact.name}`}>
              <Pencil />
            </Button>
          }
        />
        <DeleteContact contact={contact} />
      </div>
    </div>
  )
}

function DeleteContact({ contact }: { contact: ContactView }) {
  const remove = useDeleteContact()
  const [open, setOpen] = useState(false)
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={`Apagar ${contact.name}`}>
          <Trash2 />
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Apagar {contact.name}?</DialogTitle>
          <DialogDescription>O contato e todas as conversas registradas com ele saem do agente.</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancelar
          </Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() =>
              remove.mutate(contact.id, {
                onSuccess: () => {
                  toast.success('Contato apagado.')
                  setOpen(false)
                },
                onError: (error) => toast.error(error.message),
              })
            }
          >
            Apagar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
