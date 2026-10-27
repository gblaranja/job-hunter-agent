import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  useApplicationContacts,
  useDraftMessage,
  useJobContacts,
  useRecordInteraction,
  useRegisterFollowUp,
  type DraftKind,
  type InteractionChannel,
  type MessageDraft,
} from '@/api/queries'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { contactRoleLabels, draftKindLabels, interactionChannelLabels } from '@/lib/labels'
import { Check, Copy, Sparkles } from 'lucide-react'

const kinds = Object.keys(draftKindLabels) as DraftKind[]
const channels: InteractionChannel[] = ['LINKEDIN', 'EMAIL', 'WHATSAPP', 'OUTRO']
const NO_CONTACT = 'NENHUM'
const NOTE_LIMIT = 300

type Props = {
  jobId: number
  applicationId?: number
  /** Gera direto este tipo ao abrir (por exemplo, o agradecimento depois de uma entrevista). */
  initialKind?: DraftKind
}

/**
 * Rascunhos de pedido de indicação, follow-up, agradecimento e resposta a recrutador. Você copia, ajusta e envia;
 * "Marcar como enviada" registra a conversa com o contato e, no follow-up, conta o envio na candidatura.
 */
export function MessageDrafts({ jobId, applicationId, initialKind }: Props) {
  const forApplication = useApplicationContacts(applicationId)
  const forJob = useJobContacts(jobId, applicationId === undefined)
  const contacts = (applicationId !== undefined ? forApplication.data?.contacts : forJob.data) ?? []
  const generate = useDraftMessage()
  const recordInteraction = useRecordInteraction()
  const registerFollowUp = useRegisterFollowUp()
  const [contactId, setContactId] = useState(NO_CONTACT)
  const [channel, setChannel] = useState<InteractionChannel>('LINKEDIN')
  const [draft, setDraft] = useState<MessageDraft | null>(null)
  const [text, setText] = useState('')
  const [note, setNote] = useState('')
  const started = useRef(false)

  function run(kind: DraftKind) {
    generate.mutate(
      { jobId, kind, contactId: contactId === NO_CONTACT ? null : Number(contactId) },
      {
        onSuccess: (result) => {
          setDraft(result)
          setText(result.text)
          setNote(result.linkedinNote ?? '')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  useEffect(() => {
    if (initialKind && !started.current) {
      started.current = true
      run(initialKind)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialKind])

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value)
      toast.success('Copiado. Revise antes de enviar.')
    } catch {
      toast.error('Não consegui copiar; selecione o texto e copie à mão.')
    }
  }

  const contact = contacts.find((item) => String(item.id) === contactId)
  const countsFollowUp = draft?.kind === 'FOLLOW_UP' && applicationId !== undefined
  const canMark = draft != null && (contact != null || countsFollowUp)

  async function markSent() {
    if (!draft) return
    try {
      if (contact) {
        await recordInteraction.mutateAsync({
          contactId: contact.id,
          applicationId: applicationId ?? null,
          channel,
          direction: 'ENVIADA',
          kind: draft.kind,
          summary: text.slice(0, 500),
        })
      }
      if (countsFollowUp) await registerFollowUp.mutateAsync(applicationId)
      toast.success('Registrado como enviada.')
      setDraft(null)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Mensagens</CardTitle>
        <CardDescription>Rascunhos para você revisar e enviar. O agente não manda nada sozinho.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {contacts.length > 0 && (
          <div className="space-y-2">
            <Label htmlFor={`draft-contact-${jobId}`}>Para quem</Label>
            <Select value={contactId} onValueChange={setContactId}>
              <SelectTrigger id={`draft-contact-${jobId}`} className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NO_CONTACT}>Sem contato (saudação neutra)</SelectItem>
                {contacts.map((item) => (
                  <SelectItem key={item.id} value={String(item.id)}>
                    {item.name} · {contactRoleLabels[item.role]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          {kinds.map((kind) => (
            <Button key={kind} size="sm" variant="outline" disabled={generate.isPending} onClick={() => run(kind)}>
              <Sparkles /> {draftKindLabels[kind]}
            </Button>
          ))}
        </div>
        {generate.isPending && <p className="text-sm text-muted-foreground">Escrevendo o rascunho…</p>}
        {draft && (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Badge variant="secondary">{draftKindLabels[draft.kind]}</Badge>
              {draft.source === 'MODELO' ? 'Modelo de texto (sem a Claude API): complete os colchetes.' : 'Escrito pela IA com o seu currículo.'}
            </div>
            <Label htmlFor={`draft-text-${jobId}`} className="sr-only">
              Mensagem
            </Label>
            <Textarea id={`draft-text-${jobId}`} rows={8} value={text} onChange={(event) => setText(event.target.value)} />
            <Button size="sm" variant="secondary" onClick={() => copy(text)}>
              <Copy /> Copiar mensagem
            </Button>
            {draft.linkedinNote != null && (
              <div className="space-y-2">
                <Label htmlFor={`draft-note-${jobId}`}>Convite de conexão do LinkedIn</Label>
                <Textarea
                  id={`draft-note-${jobId}`}
                  rows={3}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                />
                <div className="flex items-center justify-between gap-2">
                  <span className={note.length > NOTE_LIMIT ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}>
                    {note.length}/{NOTE_LIMIT} caracteres
                  </span>
                  <Button size="sm" variant="secondary" onClick={() => copy(note)}>
                    <Copy /> Copiar convite
                  </Button>
                </div>
              </div>
            )}
            {canMark && (
              <div className="flex flex-wrap items-end gap-2 border-t pt-3">
                {contact && (
                  <div className="space-y-2">
                    <Label htmlFor={`draft-channel-${jobId}`}>Enviada por</Label>
                    <Select value={channel} onValueChange={(value) => setChannel(value as InteractionChannel)}>
                      <SelectTrigger id={`draft-channel-${jobId}`} className="w-36">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {channels.map((option) => (
                          <SelectItem key={option} value={option}>
                            {interactionChannelLabels[option]}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}
                <Button size="sm" onClick={markSent} disabled={recordInteraction.isPending || registerFollowUp.isPending}>
                  <Check /> Marcar como enviada
                </Button>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
