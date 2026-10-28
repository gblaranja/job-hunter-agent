import { useState } from 'react'
import { toast } from 'sonner'
import { usePolishStory, useSaveStory, type StarStory, type StarStoryCommand, type StarStoryPolish } from '@/api/interviews'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Sparkles } from 'lucide-react'

type Field = 'situation' | 'task' | 'action' | 'result' | 'reflection'

const fields: { key: Field; label: string; hint: string }[] = [
  { key: 'situation', label: 'Situação', hint: 'O contexto, em duas ou três frases.' },
  { key: 'task', label: 'Tarefa', hint: 'O que cabia a você.' },
  { key: 'action', label: 'Ação', hint: 'O que você fez, e por quê.' },
  { key: 'result', label: 'Resultado', hint: 'O efeito, com números quando tiver.' },
  { key: 'reflection', label: 'Reflexão (opcional)', hint: 'O que aprendeu ou faria diferente.' },
]

function initial(story?: StarStory): StarStoryCommand {
  return {
    title: story?.title ?? '',
    situation: story?.situation ?? '',
    task: story?.task ?? '',
    action: story?.action ?? '',
    result: story?.result ?? '',
    reflection: story?.reflection ?? '',
    skills: story?.skills ?? [],
  }
}

/** Escreve ou edita uma história STAR. "Lapidar com IA" sugere um texto mais claro, que você aceita ou descarta. */
export function StoryDialog({ story, onClose }: { story?: StarStory; onClose: () => void }) {
  const [form, setForm] = useState<StarStoryCommand>(() => initial(story))
  const [skills, setSkills] = useState((story?.skills ?? []).join(', '))
  const [suggestion, setSuggestion] = useState<StarStoryPolish | null>(null)
  const save = useSaveStory()
  const polish = usePolishStory()

  const body = (): StarStoryCommand => ({
    ...form,
    skills: skills
      .split(',')
      .map((skill) => skill.trim())
      .filter(Boolean),
  })

  function submit(event: React.FormEvent) {
    event.preventDefault()
    save.mutate(
      { id: story?.id, body: body() },
      {
        onSuccess: () => {
          toast.success(story ? 'História atualizada.' : 'História salva.')
          onClose()
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function requestPolish() {
    polish.mutate(body(), { onSuccess: setSuggestion, onError: (error) => toast.error(error.message) })
  }

  function accept() {
    if (!suggestion) return
    setForm((current) => ({
      ...current,
      situation: suggestion.situation,
      task: suggestion.task,
      action: suggestion.action,
      result: suggestion.result,
      reflection: suggestion.reflection ?? '',
    }))
    setSuggestion(null)
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{story ? 'Editar história' : 'Nova história'}</DialogTitle>
          <DialogDescription>
            Situação, tarefa, ação e resultado, com as suas palavras. A preparação sugere a história nas perguntas
            sobre as skills dela.
          </DialogDescription>
        </DialogHeader>
        <form id="story-form" onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="story-title">Título</Label>
            <Input
              id="story-title"
              required
              maxLength={200}
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="story-skills">Skills (separadas por vírgula)</Label>
            <Input id="story-skills" value={skills} onChange={(event) => setSkills(event.target.value)} />
          </div>
          {fields.map(({ key, label, hint }) => (
            <div key={key} className="space-y-2">
              <Label htmlFor={`story-${key}`}>{label}</Label>
              <Textarea
                id={`story-${key}`}
                rows={key === 'action' ? 4 : 3}
                placeholder={hint}
                value={form[key] ?? ''}
                onChange={(event) => setForm({ ...form, [key]: event.target.value })}
              />
            </div>
          ))}
        </form>
        {suggestion && (
          <section aria-label="Versão lapidada" className="space-y-3 rounded-md border p-3">
            <h3 className="text-sm font-semibold">Versão lapidada</h3>
            {suggestion.warnings.map((warning) => (
              <Alert key={warning} variant="destructive">
                <AlertDescription>{warning}</AlertDescription>
              </Alert>
            ))}
            {fields.map(({ key, label }) =>
              suggestion[key] ? (
                <div key={key}>
                  <p className="text-xs font-medium text-muted-foreground">{label}</p>
                  <p className="text-sm whitespace-pre-line">{suggestion[key]}</p>
                </div>
              ) : null,
            )}
            {suggestion.notes && <p className="text-sm text-muted-foreground">{suggestion.notes}</p>}
            <div className="flex gap-2">
              <Button type="button" size="sm" onClick={accept}>
                Usar esta versão
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setSuggestion(null)}>
                Descartar
              </Button>
            </div>
          </section>
        )}
        <DialogFooter className="gap-2 sm:justify-between">
          <Button type="button" variant="outline" onClick={requestPolish} disabled={polish.isPending}>
            <Sparkles /> {polish.isPending ? 'Lapidando…' : 'Lapidar com IA'}
          </Button>
          <Button type="submit" form="story-form" disabled={save.isPending}>
            Salvar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
