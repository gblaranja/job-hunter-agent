import { useLayoutEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  fetchPagePdfLink,
  fetchResumeLink,
  useAppliedJob,
  useAttachPagePdf,
  type AppliedJob,
} from '@/api/queries'
import { EditSnapshotDialog } from '@/components/EditSnapshotDialog'
import { Markdown } from '@/components/Markdown'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { LinkStatusBadge, StatusBadge } from '@/components/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { formatDate, formatDateTime } from '@/lib/format'
import { channelLabels } from '@/lib/labels'
import { openInNewTab } from '@/lib/open'
import { cn } from '@/lib/utils'
import {
  Archive,
  ChevronDown,
  ChevronUp,
  Copy,
  ExternalLink,
  FileDown,
  FileUp,
  GraduationCap,
  Pencil,
} from 'lucide-react'

/** A vaga aplicada: link com status, descrição congelada, currículo enviado e ações. */
export function AppliedJobCard({ applicationId }: { applicationId: number }) {
  const applied = useAppliedJob(applicationId)
  if (applied.isLoading) return <LoadingBlock lines={2} />
  if (applied.error) return <ErrorBlock error={applied.error} />
  if (!applied.data) return null
  return <AppliedJobCardView applied={applied.data} />
}

const COLLAPSED_HEIGHT = 'max-h-72'

export function AppliedJobCardView({ applied }: { applied: AppliedJob }) {
  const { snapshot, resume } = applied
  const [expanded, setExpanded] = useState(false)
  const [editing, setEditing] = useState(false)
  const attachPdf = useAttachPagePdf()
  const pdfInput = useRef<HTMLInputElement>(null)
  const description = useRef<HTMLDivElement>(null)
  const [overflowing, setOverflowing] = useState(false)

  // Só oferece recolher quando a descrição passa da altura recolhida.
  useLayoutEffect(() => {
    const element = description.current
    if (element && !expanded) setOverflowing(element.scrollHeight > element.clientHeight + 1)
  }, [snapshot.descriptionMd, expanded])

  async function copyLink() {
    if (!snapshot.url) return
    await navigator.clipboard.writeText(snapshot.url)
    toast.success('Link copiado.')
  }

  function openLink(fetchLink: () => Promise<{ url: string }>) {
    openInNewTab(fetchLink).catch((error: Error) => toast.error(error.message))
  }

  function uploadPdf(file: File | undefined) {
    if (!file) return
    attachPdf.mutate(
      { applicationId: applied.applicationId, file },
      {
        onSuccess: () => toast.success('PDF da página salvo.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card>
      <CardHeader className="gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="text-lg">{snapshot.title}</CardTitle>
            <CardDescription>{snapshot.company}</CardDescription>
          </div>
          <StatusBadge status={applied.status} />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {snapshot.url ? (
            <Button asChild size="sm">
              <a href={snapshot.url} target="_blank" rel="noopener noreferrer">
                <ExternalLink /> Abrir vaga
              </a>
            </Button>
          ) : (
            <span className="text-sm text-muted-foreground">Sem link salvo</span>
          )}
          <LinkStatusBadge status={snapshot.linkStatus} />
          <span className="text-xs text-muted-foreground">
            {snapshot.linkCheckedAt ? `verificado em ${formatDateTime(snapshot.linkCheckedAt)}` : 'ainda não verificado'}
          </span>
        </div>
      </CardHeader>

      <CardContent className="space-y-5">
        {snapshot.linkStatus === 'REMOVIDO' && (
          <Alert>
            <Archive />
            <AlertTitle>A vaga saiu do ar</AlertTitle>
            <AlertDescription>
              Você está vendo a cópia salva em {formatDate(snapshot.capturedAt)}, data da aplicação.
              {!applied.hasResponse && ' Sem nenhum retorno até aqui, é provável que a posição já tenha sido preenchida.'}
            </AlertDescription>
          </Alert>
        )}

        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
          <div>
            <dt className="text-xs text-muted-foreground">Aplicada em</dt>
            <dd>{formatDate(applied.appliedAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Canal</dt>
            <dd>{applied.channel ? channelLabels[applied.channel] : '—'}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-xs text-muted-foreground">Currículo enviado</dt>
            <dd>
              {resume ? (
                <button
                  type="button"
                  className="text-left text-primary underline-offset-4 hover:underline"
                  onClick={() => openLink(() => fetchResumeLink(resume.id))}
                >
                  {resume.label} <span className="text-muted-foreground">({resume.fileName})</span>
                </button>
              ) : (
                '—'
              )}
            </dd>
          </div>
        </dl>

        <section aria-label="Descrição salva">
          {snapshot.descriptionMd ? (
            <>
              <div
                ref={description}
                className={cn(
                  !expanded && COLLAPSED_HEIGHT,
                  !expanded && overflowing && 'overflow-hidden [mask-image:linear-gradient(to_bottom,black_70%,transparent)]',
                )}
              >
                <Markdown>{snapshot.descriptionMd}</Markdown>
              </div>
              {(overflowing || expanded) && (
                <Button variant="ghost" size="sm" className="mt-2" onClick={() => setExpanded((value) => !value)}>
                  {expanded ? <ChevronUp /> : <ChevronDown />}
                  {expanded ? 'Recolher descrição' : 'Mostrar descrição completa'}
                </Button>
              )}
            </>
          ) : (
            <p className="text-sm text-muted-foreground">
              Nenhuma descrição salva. Cole o texto da vaga em “Editar descrição”.
            </p>
          )}
        </section>

        <div className="flex flex-wrap gap-2 border-t pt-4">
          <Button variant="outline" size="sm" onClick={copyLink} disabled={!snapshot.url}>
            <Copy /> Copiar link
          </Button>
          {snapshot.hasPagePdf ? (
            <Button variant="outline" size="sm" onClick={() => openLink(() => fetchPagePdfLink(applied.applicationId))}>
              <FileDown /> Baixar PDF da página
            </Button>
          ) : (
            <>
              <Button variant="outline" size="sm" onClick={() => pdfInput.current?.click()} disabled={attachPdf.isPending}>
                <FileUp /> Salvar PDF da página
              </Button>
              <input
                ref={pdfInput}
                type="file"
                accept="application/pdf"
                className="hidden"
                onChange={(event) => uploadPdf(event.target.files?.[0])}
              />
            </>
          )}
          <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
            <Pencil /> Editar descrição
          </Button>
          <Tooltip>
            <TooltipTrigger asChild>
              <span>
                <Button variant="outline" size="sm" disabled>
                  <GraduationCap /> Preparar para a entrevista
                </Button>
              </span>
            </TooltipTrigger>
            <TooltipContent>Chega junto com a análise por IA.</TooltipContent>
          </Tooltip>
        </div>
      </CardContent>

      {editing && <EditSnapshotDialog applied={applied} open={editing} onOpenChange={setEditing} />}
    </Card>
  )
}
