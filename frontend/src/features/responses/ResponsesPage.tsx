import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
  useBoard,
  useEmailAccuracy,
  useEmails,
  useReviewEmail,
  type EmailClassification,
  type EmailItem,
} from '@/api/queries'
import { PageHeader } from '@/components/PageHeader'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDateTime, formatPercent } from '@/lib/format'
import { classificationLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { Check, EyeOff } from 'lucide-react'

const classifications = Object.keys(classificationLabels) as EmailClassification[]
const NONE = 'NENHUMA'

/** Retornos lidos do Gmail: confirme ou corrija a classificação da IA e ligue o e-mail à candidatura certa. */
export function ResponsesPage() {
  const [tab, setTab] = useState('pending')
  const emails = useEmails(tab === 'pending')
  const accuracy = useEmailAccuracy()
  const board = useBoard()
  const applications = useMemo(
    () => (board.data?.columns ?? []).flatMap((column) => column.cards),
    [board.data],
  )

  return (
    <>
      <PageHeader
        title="Retornos"
        description="E-mails de empresas e recrutadores. Os de baixa confiança aparecem primeiro; nada muda de etapa sem confiança alta ou sem a sua confirmação."
        actions={
          accuracy.data && (
            <div className="text-right text-sm">
              <p className="font-semibold">{formatPercent(accuracy.data.rate)}</p>
              <p className="text-xs text-muted-foreground">acerto da IA em {accuracy.data.reviewed} revisões</p>
            </div>
          )
        }
      />
      <Tabs value={tab} onValueChange={setTab} className="mb-4">
        <TabsList>
          <TabsTrigger value="pending">
            Para revisar {accuracy.data && accuracy.data.pending > 0 && <Badge className="ml-1">{accuracy.data.pending}</Badge>}
          </TabsTrigger>
          <TabsTrigger value="all">Todos</TabsTrigger>
        </TabsList>
      </Tabs>
      {emails.isLoading && <LoadingBlock />}
      {emails.error && <ErrorBlock error={emails.error} />}
      {emails.isSuccess && emails.data.length === 0 && (
        <EmptyBlock title={tab === 'pending' ? 'Nada para revisar.' : 'Nenhum e-mail classificado ainda.'}>
          Os retornos chegam com a coleta, junto dos alertas da label do Gmail.
        </EmptyBlock>
      )}
      <div className="space-y-3">
        {emails.data?.map((email) => (
          <EmailCard
            key={email.id}
            email={email}
            applications={applications.map((card) => ({ id: card.applicationId, label: `${card.title} · ${card.company}` }))}
          />
        ))}
      </div>
    </>
  )
}

function EmailCard({ email, applications }: { email: EmailItem; applications: { id: number; label: string }[] }) {
  const review = useReviewEmail()
  const current = email.finalClassification ?? email.aiClassification ?? 'OUTRO'
  const [classification, setClassification] = useState<EmailClassification>(current)
  const [applicationId, setApplicationId] = useState<string>(email.applicationId != null ? String(email.applicationId) : NONE)
  const lowConfidence = (email.aiConfidence ?? 0) < 0.7

  function submit(target: EmailClassification, application: string) {
    review.mutate(
      { id: email.id, classification: target, applicationId: application === NONE ? null : Number(application) },
      {
        onSuccess: () => toast.success('Retorno revisado.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card className={cn(email.needsReview && 'border-l-4 border-l-amber-500')}>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-medium">{email.subject ?? '(sem assunto)'}</p>
            <p className="text-sm text-muted-foreground">
              {email.sender} · {formatDateTime(email.receivedAt)}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {email.aiClassification && (
              <Badge variant="outline" title={email.rationale ?? undefined}>
                IA: {classificationLabels[email.aiClassification]}
                {email.aiConfidence != null && (
                  <span className={cn('ml-1', lowConfidence ? 'text-amber-600' : 'text-success')}>
                    {Math.round(email.aiConfidence * 100)}%
                  </span>
                )}
              </Badge>
            )}
            {email.finalClassification && <Badge>{classificationLabels[email.finalClassification]}</Badge>}
          </div>
        </div>
        {email.snippet && <p className="line-clamp-2 text-sm text-muted-foreground">{email.snippet}</p>}
        {email.rationale && <p className="text-xs text-muted-foreground">Por quê: {email.rationale}</p>}
        <div className="flex flex-wrap items-center gap-2 border-t pt-3">
          <Select value={classification} onValueChange={(value) => setClassification(value as EmailClassification)}>
            <SelectTrigger className="h-8 w-52" aria-label="Classificação">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {classifications.map((option) => (
                <SelectItem key={option} value={option}>
                  {classificationLabels[option]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={applicationId} onValueChange={setApplicationId}>
            <SelectTrigger className="h-8 w-72" aria-label="Candidatura">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>Sem candidatura</SelectItem>
              {applications.map((application) => (
                <SelectItem key={application.id} value={String(application.id)}>
                  {application.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div className="ml-auto flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => submit('IRRELEVANTE', NONE)} disabled={review.isPending}>
              <EyeOff /> Irrelevante
            </Button>
            <Button size="sm" onClick={() => submit(classification, applicationId)} disabled={review.isPending}>
              <Check /> {classification === email.aiClassification ? 'Confirmar' : 'Salvar correção'}
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
