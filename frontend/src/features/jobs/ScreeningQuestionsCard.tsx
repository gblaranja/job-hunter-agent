import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useScreeningAnswers } from '@/api/queries'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Copy, ListChecks } from 'lucide-react'

/** Rascunhos das respostas do formulário da vaga e da carta curta, a partir do currículo e dos seus dados. */
export function ScreeningQuestionsCard({ jobId }: { jobId: number }) {
  const draft = useScreeningAnswers(jobId)
  const [questions, setQuestions] = useState('')
  const [coverLetter, setCoverLetter] = useState(false)

  function submit(event: FormEvent) {
    event.preventDefault()
    const list = questions
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
    draft.mutate({ questions: list, coverLetter }, { onError: (error) => toast.error(error.message) })
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast.success('Copiado.')
    } catch {
      toast.error('Não foi possível copiar.')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ListChecks className="size-4" aria-hidden /> Perguntas do formulário
        </CardTitle>
        <CardDescription>
          Cole as perguntas de triagem (uma por linha). Os rascunhos usam só o seu currículo e os seus dados da triagem;
          revise antes de colar no formulário.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="screening-questions">Perguntas</Label>
            <Textarea
              id="screening-questions"
              rows={4}
              value={questions}
              onChange={(e) => setQuestions(e.target.value)}
              placeholder="Qual a sua pretensão salarial?"
            />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={coverLetter} onChange={(e) => setCoverLetter(e.target.checked)} /> Escrever
            também a carta curta
          </label>
          <Button type="submit" disabled={draft.isPending}>
            {draft.isPending ? 'Escrevendo…' : 'Gerar rascunhos'}
          </Button>
        </form>
        {draft.data && (
          <div className="space-y-3">
            {draft.data.answers.map((item, index) => (
              <div key={`${index}-${item.question}`} className="space-y-1 rounded-md border p-3 text-sm">
                <p className="font-medium">{item.question}</p>
                {item.answer ? (
                  <div className="flex items-start justify-between gap-2">
                    <p className="whitespace-pre-line">{item.answer}</p>
                    <Button variant="ghost" size="icon-sm" aria-label="Copiar resposta" onClick={() => copy(item.answer)}>
                      <Copy />
                    </Button>
                  </div>
                ) : null}
                {item.needsInput && <Badge variant="outline">Precisa de você</Badge>}
                {item.basis && <p className="text-xs text-muted-foreground">{item.basis}</p>}
              </div>
            ))}
            {draft.data.coverLetter && (
              <div className="space-y-1 rounded-md border p-3 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-medium">Carta curta</p>
                  <Button variant="ghost" size="sm" onClick={() => copy(draft.data.coverLetter ?? '')}>
                    <Copy /> Copiar
                  </Button>
                </div>
                <p className="whitespace-pre-line">{draft.data.coverLetter}</p>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
