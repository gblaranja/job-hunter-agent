import type { ContactView } from '@/api/queries'
import { LinkedinIcon } from '@/components/brand/LinkedinIcon'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { contactRoleLabels } from '@/lib/labels'
import { MessageSquarePlus } from 'lucide-react'
import { InteractionDialog } from './InteractionDialog'

/** Lista compacta de contatos com o papel, o cargo, o perfil e o atalho para registrar uma conversa. */
export function ContactList({ contacts, applicationId }: { contacts: ContactView[]; applicationId?: number }) {
  return (
    <ul className="divide-y">
      {contacts.map((contact) => (
        <li key={contact.id} className="flex items-start gap-2 py-2 first:pt-0 last:pb-0">
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
              {contact.name}
              <Badge variant="secondary" className="font-normal">
                {contactRoleLabels[contact.role]}
              </Badge>
            </p>
            {contact.position && <p className="truncate text-xs text-muted-foreground">{contact.position}</p>}
          </div>
          {contact.linkedinUrl && (
            <Button asChild variant="ghost" size="icon-xs" aria-label={`Perfil de ${contact.name} no LinkedIn`}>
              <a href={contact.linkedinUrl} target="_blank" rel="noopener noreferrer">
                <LinkedinIcon />
              </a>
            </Button>
          )}
          <InteractionDialog
            contact={contact}
            applicationId={applicationId}
            trigger={
              <Button variant="ghost" size="icon-xs" aria-label={`Registrar conversa com ${contact.name}`}>
                <MessageSquarePlus />
              </Button>
            }
          />
        </li>
      ))}
    </ul>
  )
}
