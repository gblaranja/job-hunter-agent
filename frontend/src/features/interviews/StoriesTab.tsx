import { useState } from 'react'
import { toast } from 'sonner'
import { useDeleteStory, useStories, type StarStory } from '@/api/interviews'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { StoryDialog } from './StoryDialog'

/** Banco de histórias STAR: o material das respostas comportamentais. */
export function StoriesTab() {
  const stories = useStories()
  const remove = useDeleteStory()
  const [editing, setEditing] = useState<StarStory | 'new' | null>(null)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          Escreva as suas histórias uma vez; a preparação de cada entrevista sugere a certa para cada pergunta.
        </p>
        <Button onClick={() => setEditing('new')}>
          <Plus /> Nova história
        </Button>
      </div>
      {stories.isLoading && <LoadingBlock lines={3} />}
      {stories.error && <ErrorBlock error={stories.error} />}
      {stories.data?.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Nenhuma história ainda. Comece por um projeto difícil, um conflito no time e um erro que você corrigiu.
        </p>
      )}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {stories.data?.map((story) => (
          <Card key={story.id}>
            <CardHeader>
              <CardTitle className="text-base">{story.title}</CardTitle>
              {story.skills.length > 0 && (
                <CardDescription className="flex flex-wrap gap-1">
                  {story.skills.map((skill) => (
                    <Badge key={skill} variant="secondary">
                      {skill}
                    </Badge>
                  ))}
                </CardDescription>
              )}
              <CardAction className="flex gap-1">
                <Button size="icon" variant="ghost" aria-label={`Editar ${story.title}`} onClick={() => setEditing(story)}>
                  <Pencil />
                </Button>
                <Button
                  size="icon"
                  variant="ghost"
                  aria-label={`Excluir ${story.title}`}
                  onClick={() =>
                    remove.mutate(story.id, {
                      onSuccess: () => toast.success('História excluída.'),
                      onError: (error) => toast.error(error.message),
                    })
                  }
                >
                  <Trash2 />
                </Button>
              </CardAction>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              {story.situation && <p className="line-clamp-2 text-muted-foreground">{story.situation}</p>}
              {story.result && (
                <p>
                  <span className="font-medium">Resultado: </span>
                  {story.result}
                </p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
      {editing && <StoryDialog story={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
