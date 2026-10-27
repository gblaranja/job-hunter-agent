import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import {
  useRecordInteraction,
  useRecordResponse,
  type ContactView,
  type InteractionChannel,
  type ResponseChannel,
  type ResponseType,
} from '@/api/queries'
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
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { contactRoleLabels, responseChannelLabels, responseTypeLabels } from '@/lib/labels'
import { MessageSquarePlus } from 'lucide-react'

const types = Object.keys(responseTypeLabels) as ResponseType[]
const channels = (Object.keys(responseChannelLabels) as ResponseChannel[]).filter((channel) => channel !== 'EMAIL')
const moving = new Set<ResponseType>(['CONVITE_ENTREVISTA', 'TESTE_TECNICO', 'OFERTA', 'RECUSA'])
const NO_CONTACT = 'NENHUM'
const interactionChannel: Record<ResponseChannel, InteractionChannel> = {
  EMAIL: 'EMAIL',
  WHATSAPP: 'WHATSAPP',
  LINKEDIN: 'LINKEDIN',
  TELEFONE: 'TELEFONE',
  OUTRO: 'OUTRO',
}

/**
 * Retorno que não chegou por e-mail (WhatsApp, mensagem no LinkedIn, ligação), para o funil contar todos. Com um
 * contato escolhido, a conversa também fica no histórico dele.
 */
export function ResponseDialog({ applicationId, contacts = [] }: { applicationId: number; contacts?: ContactView[] }) {
  const record = useRecordResponse()
  const recordInteraction = useRecordInteraction()
  const [contactId, setContactId] = useState(NO_CONTACT)
  const [open, setOpen] = useState(false)
  const [type, setType] = useState<ResponseType>('CONVITE_ENTREVISTA')
  const [channel, setChannel] = useState<ResponseChannel>('WHATSAPP')
  const [note, setNote] = useState('')
  const [moveStatus, setMoveStatus] = useState(true)

  function submit(event: FormEvent) {
    event.preventDefault()
    record.mutate(
      { applicationId, responseType: type, channel, note: note || null, moveStatus: moving.has(type) && moveStatus },
      {
        onSuccess: () => {
          if (contactId !== NO_CONTACT) {
            recordInteraction.mutate({
              contactId: Number(contactId),
              applicationId,
              channel: interactionChannel[channel],
              direction: 'RECEBIDA',
              kind: 'CONVERSA',
              summary: note || responseTypeLabels[type],
            })
          }
          toast.success('Retorno registrado.')
          setOpen(false)
          setNote('')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="w-full">
          <MessageSquarePlus /> Registrar retorno
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Registrar retorno</DialogTitle>
            <DialogDescription>Para retornos que não chegaram por e-mail. Os de e-mail entram sozinhos.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="response-type">Tipo</Label>
              <Select value={type} onValueChange={(value) => setType(value as ResponseType)}>
                <SelectTrigger id="response-type" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {types.map((option) => (
                    <SelectItem key={option} value={option}>
                      {responseTypeLabels[option]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="response-channel">Canal</Label>
              <Select value={channel} onValueChange={(value) => setChannel(value as ResponseChannel)}>
                <SelectTrigger id="response-channel" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {channels.map((option) => (
                    <SelectItem key={option} value={option}>
                      {responseChannelLabels[option]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          {contacts.length > 0 && (
            <div className="space-y-2">
              <Label htmlFor="response-contact">Quem falou com você</Label>
              <Select value={contactId} onValueChange={setContactId}>
                <SelectTrigger id="response-contact" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_CONTACT}>Não registrar contato</SelectItem>
                  {contacts.map((contact) => (
                    <SelectItem key={contact.id} value={String(contact.id)}>
                      {contact.name} · {contactRoleLabels[contact.role]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="response-note">Nota</Label>
            <Textarea id="response-note" value={note} onChange={(event) => setNote(event.target.value)} />
          </div>
          {moving.has(type) && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={moveStatus} onChange={(event) => setMoveStatus(event.target.checked)} />
              Mover o card para a etapa correspondente
            </label>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={record.isPending}>
              Registrar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
