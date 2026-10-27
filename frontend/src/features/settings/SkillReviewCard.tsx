import { useState } from 'react'
import { toast } from 'sonner'
import {
  useAcceptSkill,
  useEscoSearch,
  useIgnoreSkill,
  useRemoveSkill,
  useReviewedSkills,
  useSkillSuggestions,
  type EscoSkill,
  type SkillSuggestion,
} from '@/api/queries'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Check, Search, Trash2, X } from 'lucide-react'

/**
 * Fila de revisão do dicionário de skills: as skills pedidas nas vagas que o dicionário não conhece. Aceitar faz a
 * skill contar no score determinístico; ignorar tira da fila.
 */
export function SkillReviewCard() {
  const suggestions = useSkillSuggestions()
  const reviewed = useReviewedSkills()
  const remove = useRemoveSkill()

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Dicionário de skills</CardTitle>
        <CardDescription>
          Skills pedidas em 3 vagas ou mais que o dicionário ainda não reconhece. Aceita, a skill passa a contar no score
          sem a Claude, e as outras grafias viram apelidos.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {suggestions.isLoading && <LoadingBlock lines={1} />}
        {suggestions.error && <ErrorBlock error={suggestions.error} />}
        {suggestions.data?.length === 0 && <p className="text-sm text-muted-foreground">Nenhuma sugestão por enquanto.</p>}
        {suggestions.data?.map((suggestion) => <SuggestionRow key={suggestion.key} suggestion={suggestion} />)}
        {reviewed.data && reviewed.data.length > 0 && (
          <div className="space-y-2 border-t pt-3">
            <p className="text-sm font-medium">Aceitas na revisão</p>
            <ul className="flex flex-wrap gap-2">
              {reviewed.data.map((skill) => (
                <li key={skill.id}>
                  <Badge variant="secondary" title={skill.aliases.join(', ') || undefined}>
                    {skill.canonical}
                    <button
                      type="button"
                      className="ml-1"
                      aria-label={`Tirar ${skill.canonical} do dicionário`}
                      onClick={() => remove.mutate(skill.id, { onError: (error) => toast.error(error.message) })}
                    >
                      <Trash2 className="size-3" aria-hidden />
                    </button>
                  </Badge>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function SuggestionRow({ suggestion }: { suggestion: SkillSuggestion }) {
  const accept = useAcceptSkill()
  const ignore = useIgnoreSkill()
  const esco = useEscoSearch()
  const [match, setMatch] = useState<EscoSkill | null>(null)
  const busy = accept.isPending || ignore.isPending

  function confirm() {
    accept.mutate(
      {
        canonical: suggestion.name,
        aliases: suggestion.spellings.filter((spelling) => spelling !== suggestion.name),
        escoUri: match?.uri,
        labelPt: match?.labelPt,
        labelEn: match?.labelEn,
      },
      {
        onSuccess: () => toast.success(`${suggestion.name} entrou no dicionário.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function lookup() {
    esco.mutate(suggestion.name, {
      onSuccess: (results) => {
        setMatch(results[0] ?? null)
        if (results.length === 0) toast.info('A ESCO não tem essa skill.')
      },
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3 text-sm">
      <div className="min-w-0 space-y-1">
        <p>
          <span className="font-medium">{suggestion.name}</span>{' '}
          <span className="text-muted-foreground">
            · {suggestion.jobCount} {suggestion.jobCount === 1 ? 'vaga' : 'vagas'}
          </span>
        </p>
        {suggestion.spellings.length > 1 && (
          <p className="text-xs text-muted-foreground">Grafias: {suggestion.spellings.join(', ')}</p>
        )}
        {match && (
          <p className="text-xs text-muted-foreground">
            ESCO: {match.labelPt ?? match.labelEn ?? match.uri}
            {match.labelEn && match.labelPt && ` (${match.labelEn})`}
          </p>
        )}
      </div>
      <div className="flex gap-1">
        <Button variant="ghost" size="sm" onClick={lookup} disabled={esco.isPending}>
          <Search /> ESCO
        </Button>
        <Button variant="ghost" size="sm" onClick={() => ignore.mutate(suggestion.name)} disabled={busy}>
          <X /> Ignorar
        </Button>
        <Button variant="outline" size="sm" onClick={confirm} disabled={busy}>
          <Check /> Aceitar
        </Button>
      </div>
    </div>
  )
}
