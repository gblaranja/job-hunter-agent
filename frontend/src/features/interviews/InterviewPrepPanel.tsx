import { toast } from 'sonner'
import { useInterviewPreps, useRequestInterviewPrep, type InterviewPrep, type InterviewStage } from '@/api/interviews'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatDateTime } from '@/lib/format'
import { statusLabels } from '@/lib/labels'
import { RefreshCw } from 'lucide-react'

/** A preparação mais recente da candidatura, com o botão para gerar de novo. */
export function InterviewPrepPanel({ applicationId, stage }: { applicationId: number; stage?: InterviewStage }) {
  const preps = useInterviewPreps(applicationId)
  const request = useRequestInterviewPrep()
  const prep = preps.data?.[0]

  function generate() {
    request.mutate(
      { applicationId, stage: prep?.stage ?? stage },
      { onError: (error) => toast.error(error.message) },
    )
  }

  if (preps.isLoading) return <LoadingBlock lines={3} />
  if (preps.error) return <ErrorBlock error={preps.error} />
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        {prep && (
          <>
            <Badge variant="secondary">{statusLabels[prep.stage]}</Badge>
            <span className="text-xs text-muted-foreground">
              {prep.status === 'GERANDO'
                ? 'Gerando…'
                : `${prep.source === 'IA' ? 'Gerada pela IA' : 'Montada pelas regras'} em ${formatDateTime(prep.updatedAt)}`}
            </span>
          </>
        )}
        <Button size="sm" variant="outline" className="ml-auto" onClick={generate} disabled={request.isPending || prep?.status === 'GERANDO'}>
          <RefreshCw /> {prep ? 'Gerar de novo' : 'Gerar preparação'}
        </Button>
      </div>
      {!prep && (
        <p className="text-sm text-muted-foreground">
          A preparação é gerada sozinha quando a candidatura chega a Entrevista ou Teste técnico.
        </p>
      )}
      {prep?.status === 'FALHOU' && <p className="text-sm text-destructive">Não foi possível gerar: {prep.lastError}</p>}
      {prep?.content && <PrepContent prep={prep} />}
    </div>
  )
}

function PrepContent({ prep }: { prep: InterviewPrep }) {
  const content = prep.content!
  return (
    <div className="space-y-5 text-sm">
      <section>
        <h4 className="mb-2 font-semibold">Perguntas prováveis</h4>
        <ol className="space-y-3">
          {content.questions.map((question) => (
            <li key={question.question} className="rounded-md border p-3">
              <p className="font-medium">{question.question}</p>
              <p className="text-xs text-muted-foreground">{question.reason}</p>
              {question.storyTitle && (
                <p className="mt-1">
                  <Badge variant="outline">História: {question.storyTitle}</Badge>
                </p>
              )}
              <p className="mt-1">{question.answerOutline}</p>
            </li>
          ))}
        </ol>
      </section>
      <PrepList title="Tópicos para revisar" items={content.technicalTopics} />
      <PrepList title="Sobre a empresa" items={content.companyFacts} />
      <PrepList title="Perguntas para fazer" items={content.questionsToAsk} />
      <PrepList title="Pontos para esclarecer" items={content.pointsToClarify} />
    </div>
  )
}

function PrepList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <section>
      <h4 className="mb-1 font-semibold">{title}</h4>
      <ul className="list-disc space-y-1 pl-5">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
