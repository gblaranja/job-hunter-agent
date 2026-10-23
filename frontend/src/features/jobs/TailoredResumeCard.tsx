import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import {
  downloadTailoredDocx,
  useEditTailoredResume,
  useRequestTailoredResume,
  useResumes,
  useSaveTailoredResume,
  useTailoredResumes,
  useTailoringAvailability,
  type TailoredResume,
} from '@/api/queries'
import { Markdown } from '@/components/Markdown'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { ScoreBadge } from '@/components/ScoreBadge'
import { SkillChips } from '@/components/SkillChips'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from '@/lib/format'
import { ArrowRight, Check, Copy, Download, FileText, KeyRound, Loader2, Save, Sparkles, TriangleAlert } from 'lucide-react'

/**
 * Versões do currículo reescritas pelo agente para a vaga: cobertura das palavras-chave e score antes e depois,
 * o texto (editável), o que mudou e o DOCX, que pode virar uma versão do currículo para a candidatura.
 */
export function TailoredResumeCard({ jobId }: { jobId: number }) {
  const versions = useTailoredResumes(jobId)
  const availability = useTailoringAvailability()
  const resumes = useResumes()
  const request = useRequestTailoredResume()
  const [selectedId, setSelectedId] = useState<number>()
  const [baseId, setBaseId] = useState<number>()

  const list = versions.data ?? []
  const current = list.find((version) => version.id === selectedId) ?? list[0]
  const generating = list.some((version) => version.status === 'GERANDO')
  const bases = (resumes.data ?? []).filter((resume) => !resume.archived && resume.hasText)
  const chosenBase = baseId ?? bases.find((resume) => resume.isDefault)?.id ?? bases[0]?.id
  const disabled = availability.data?.enabled === false

  function generate() {
    request.mutate(
      { jobId, baseResumeId: chosenBase },
      {
        onSuccess: (created) => {
          setSelectedId(created.id)
          toast.success('O agente começou a reescrever o currículo para esta vaga.')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <FileText className="size-4" aria-hidden /> Currículo para esta vaga
        </CardTitle>
        <CardDescription>
          O agente reescreve seu currículo com os termos da vaga para passar no ATS, sem inventar experiência.
        </CardDescription>
        <CardAction>
          <Button
            size="sm"
            onClick={generate}
            disabled={disabled || request.isPending || generating || chosenBase === undefined}
          >
            <Sparkles /> {list.length ? 'Gerar nova versão' : 'Gerar currículo adaptado'}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-4">
        {disabled && (
          <Alert>
            <KeyRound />
            <AlertTitle>Precisa da chave da Claude API</AlertTitle>
            <AlertDescription>
              <p>
                Defina <code className="font-mono">JOBHUNTER_AI_API_KEY</code> no ambiente do backend e reinicie. Com
                ela, a análise das vagas também passa a usar a IA.
              </p>
            </AlertDescription>
          </Alert>
        )}
        {!disabled && bases.length > 1 && (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <Label htmlFor="tailor-base" className="text-muted-foreground">
              A partir de
            </Label>
            <Select value={chosenBase != null ? String(chosenBase) : undefined} onValueChange={(value) => setBaseId(Number(value))}>
              <SelectTrigger id="tailor-base" size="sm" className="w-64">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {bases.map((resume) => (
                  <SelectItem key={resume.id} value={String(resume.id)}>
                    {resume.label}
                    {resume.isDefault ? ' (padrão)' : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        {resumes.isSuccess && bases.length === 0 && (
          <p className="text-sm text-muted-foreground">
            <Link to="/resumes" className="text-primary underline">
              Envie um currículo
            </Link>{' '}
            com texto legível para gerar a versão adaptada.
          </p>
        )}
        {versions.isLoading && <LoadingBlock lines={2} />}
        {versions.error && <ErrorBlock error={versions.error} />}
        {!disabled && versions.isSuccess && list.length === 0 && bases.length > 0 && (
          <p className="text-sm text-muted-foreground">
            Nenhuma versão ainda.
            {availability.data?.autoGenerate ? ' Ao marcar interesse na vaga, o agente gera a primeira sozinho.' : ''}
          </p>
        )}
        {list.length > 1 && current && (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <Label htmlFor="tailor-version" className="text-muted-foreground">
              Versão
            </Label>
            <Select value={String(current.id)} onValueChange={(value) => setSelectedId(Number(value))}>
              <SelectTrigger id="tailor-version" size="sm" className="w-64">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {list.map((version, index) => (
                  <SelectItem key={version.id} value={String(version.id)}>
                    {`#${list.length - index} · ${formatDateTime(version.createdAt)}`}
                    {version.status === 'PRONTO' && version.scoreAfter != null ? ` · score ${version.scoreAfter}` : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        {current && <TailoredVersion key={current.id} version={current} />}
      </CardContent>
    </Card>
  )
}

function TailoredVersion({ version }: { version: TailoredResume }) {
  if (version.status === 'GERANDO') {
    return (
      <div role="status" className="flex items-center gap-2 rounded-md border border-dashed p-4 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden />
        O agente está reescrevendo o currículo{version.baseResumeLabel ? ` “${version.baseResumeLabel}”` : ''} para
        esta vaga. Leva de um a três minutos.
      </div>
    )
  }
  if (version.status === 'FALHOU') {
    return (
      <Alert variant="destructive">
        <TriangleAlert />
        <AlertTitle>Não foi possível gerar esta versão</AlertTitle>
        <AlertDescription>{version.lastError ?? 'Tente gerar de novo.'}</AlertDescription>
      </Alert>
    )
  }

  const covered = new Set(version.coveredAfter.map((keyword) => keyword.toLowerCase()))
  const missing = version.keywords.filter((keyword) => !covered.has(keyword.toLowerCase()))

  return (
    <div className="space-y-4">
      <dl className="grid gap-3 sm:grid-cols-2">
        <Comparison label="Score de aderência">
          <ScoreBadge score={version.scoreBefore} />
          <ArrowRight className="size-4 text-muted-foreground" aria-label="para" />
          <ScoreBadge score={version.scoreAfter} />
        </Comparison>
        <Comparison label="Palavras-chave da vaga no texto">
          <span className="tabular-nums">
            {version.coveredBefore.length}/{version.keywords.length}
          </span>
          <ArrowRight className="size-4 text-muted-foreground" aria-label="para" />
          <strong className="tabular-nums">
            {version.coveredAfter.length}/{version.keywords.length}
          </strong>
        </Comparison>
      </dl>
      {version.keywords.length > 0 && <SkillChips skills={version.keywords} owned={version.coveredAfter} gaps={missing} />}
      {version.unsupportedSkills.length > 0 && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Confira antes de enviar</AlertTitle>
          <AlertDescription>
            {version.unsupportedSkills.join(', ')} {version.unsupportedSkills.length > 1 ? 'aparecem' : 'aparece'} no
            texto adaptado, mas não no currículo de partida. Mantenha só o que você consegue comprovar numa entrevista.
          </AlertDescription>
        </Alert>
      )}
      {version.notes && <p className="text-sm">{version.notes}</p>}
      {version.notCovered.length > 0 && (
        <p className="text-sm text-muted-foreground">
          Ficaram de fora, porque o currículo não comprova: {version.notCovered.join(', ')}.
        </p>
      )}

      <Tabs defaultValue="preview">
        <TabsList>
          <TabsTrigger value="preview">Prévia</TabsTrigger>
          <TabsTrigger value="edit">Editar</TabsTrigger>
          <TabsTrigger value="changes">O que mudou ({version.changes.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="preview" className="rounded-md border p-4">
          <Markdown>{version.content ?? ''}</Markdown>
        </TabsContent>
        <TabsContent value="edit">
          <EditForm version={version} />
        </TabsContent>
        <TabsContent value="changes">
          <ChangeList version={version} />
        </TabsContent>
      </Tabs>

      <VersionActions version={version} />
      <p className="text-xs text-muted-foreground">
        {version.edited ? 'Editado por você · ' : ''}
        {version.baseResumeLabel ? `A partir de “${version.baseResumeLabel}” · ` : ''}
        Atualizado em {formatDateTime(version.updatedAt)}
      </p>
    </div>
  )
}

function Comparison({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-md border p-3">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 flex items-center gap-2 text-sm">{children}</dd>
    </div>
  )
}

function EditForm({ version }: { version: TailoredResume }) {
  const edit = useEditTailoredResume()
  const [draft, setDraft] = useState(version.content ?? '')
  const changed = draft.trim() !== (version.content ?? '').trim()

  function save() {
    edit.mutate(
      { id: version.id, content: draft },
      {
        onSuccess: () => toast.success('Texto salvo; a cobertura das palavras-chave foi recalculada.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <div className="space-y-2">
      <Label htmlFor={`tailored-content-${version.id}`}>Texto em Markdown (é a base do DOCX)</Label>
      <Textarea
        id={`tailored-content-${version.id}`}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        className="min-h-96 font-mono text-xs"
      />
      <div className="flex justify-end">
        <Button size="sm" onClick={save} disabled={!changed || !draft.trim() || edit.isPending}>
          <Save /> Salvar texto
        </Button>
      </div>
    </div>
  )
}

function ChangeList({ version }: { version: TailoredResume }) {
  if (version.changes.length === 0) {
    return <p className="text-sm text-muted-foreground">O agente não listou mudanças.</p>
  }
  return (
    <ol className="space-y-3">
      {version.changes.map((change, index) => (
        <li key={index} className="space-y-2 rounded-md border p-3 text-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{change.section}</p>
          {change.before && (
            <p className="text-muted-foreground line-through decoration-muted-foreground/50">{change.before}</p>
          )}
          <p>{change.after}</p>
          <p className="text-xs text-muted-foreground">{change.reason}</p>
        </li>
      ))}
    </ol>
  )
}

function VersionActions({ version }: { version: TailoredResume }) {
  const save = useSaveTailoredResume()
  const [downloading, setDownloading] = useState(false)

  async function download() {
    setDownloading(true)
    try {
      await downloadTailoredDocx(version.id)
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setDownloading(false)
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(version.content ?? '')
      toast.success('Texto copiado.')
    } catch {
      toast.error('Não foi possível copiar; selecione o texto na aba Editar.')
    }
  }

  function saveAsResume() {
    save.mutate(
      { id: version.id, jobId: version.jobId },
      {
        onSuccess: (saved) => toast.success(`Salvo em Currículos como “${saved.label}”.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button size="sm" variant="outline" onClick={download} disabled={downloading}>
        <Download /> Baixar DOCX
      </Button>
      <Button size="sm" variant="outline" onClick={copy}>
        <Copy /> Copiar texto
      </Button>
      {version.resumeVersionId != null ? (
        <Button size="sm" variant="ghost" asChild>
          <Link to="/resumes">
            <Check /> Salvo em Currículos{version.resumeLabel ? `: ${version.resumeLabel}` : ''}
          </Link>
        </Button>
      ) : (
        <Button size="sm" onClick={saveAsResume} disabled={save.isPending}>
          <Save /> Salvar em Currículos
        </Button>
      )}
    </div>
  )
}
