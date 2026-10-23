import { useRef, useState, type DragEvent, type FormEvent } from 'react'
import { toast } from 'sonner'
import { fetchResumeLink, useResumes, useUpdateResume, useUploadResume, type ResumeItem } from '@/api/queries'
import { PageHeader } from '@/components/PageHeader'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatDate, formatFileSize, formatPercent } from '@/lib/format'
import { openInNewTab } from '@/lib/open'
import { cn } from '@/lib/utils'
import { Archive, ArchiveRestore, Eye, Pencil, Star, Upload } from 'lucide-react'

const ACCEPTED = '.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document'

export function ResumesPage() {
  const resumes = useResumes()
  return (
    <>
      <PageHeader
        title="Currículos"
        description="Cada candidatura registra a versão enviada, e a taxa de entrevistas mostra qual funciona melhor."
      />
      <div className="grid gap-6 lg:grid-cols-[22rem_minmax(0,1fr)]">
        <UploadCard />
        <section className="space-y-3" aria-label="Versões do currículo">
          {resumes.isLoading && <LoadingBlock />}
          {resumes.error && <ErrorBlock error={resumes.error} />}
          {resumes.isSuccess && resumes.data.length === 0 && (
            <EmptyBlock title="Nenhum currículo enviado ainda.">
              A primeira versão enviada vira o currículo padrão.
            </EmptyBlock>
          )}
          {resumes.data?.map((resume) => <ResumeRow key={resume.id} resume={resume} />)}
        </section>
      </div>
    </>
  )
}

function UploadCard() {
  const upload = useUploadResume()
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [label, setLabel] = useState('')
  const [targetRole, setTargetRole] = useState('')
  const [dragOver, setDragOver] = useState(false)

  function onDrop(event: DragEvent) {
    event.preventDefault()
    setDragOver(false)
    const dropped = event.dataTransfer.files[0]
    if (dropped) setFile(dropped)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!file) return
    upload.mutate(
      { file, label: label.trim() || undefined, targetRole: targetRole.trim() || undefined },
      {
        onSuccess: (resume) => {
          toast.success(`Currículo “${resume.label}” enviado.`)
          setFile(null)
          setLabel('')
          setTargetRole('')
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Card className="h-fit">
      <CardHeader>
        <CardTitle className="text-base">Enviar nova versão</CardTitle>
        <CardDescription>PDF ou DOCX, até 5 MB.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <button
            type="button"
            onClick={() => input.current?.click()}
            onDragOver={(event) => {
              event.preventDefault()
              setDragOver(true)
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={onDrop}
            className={cn(
              'flex w-full flex-col items-center gap-2 rounded-lg border-2 border-dashed p-6 text-center text-sm transition-colors',
              dragOver ? 'border-primary bg-accent' : 'hover:bg-muted',
            )}
          >
            <Upload className="size-6 text-muted-foreground" aria-hidden />
            {file ? (
              <span className="font-medium">
                {file.name} <span className="text-muted-foreground">({formatFileSize(file.size)})</span>
              </span>
            ) : (
              <span>Arraste o arquivo aqui ou clique para escolher</span>
            )}
          </button>
          <input
            ref={input}
            type="file"
            accept={ACCEPTED}
            className="hidden"
            aria-label="Arquivo do currículo"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
          <div className="space-y-2">
            <Label htmlFor="resume-label">Rótulo</Label>
            <Input
              id="resume-label"
              placeholder="Ex.: Backend Java — crédito"
              value={label}
              onChange={(event) => setLabel(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="resume-role">Cargo-alvo</Label>
            <Input
              id="resume-role"
              placeholder="Ex.: Desenvolvedor Java Sênior"
              value={targetRole}
              onChange={(event) => setTargetRole(event.target.value)}
            />
          </div>
          <Button type="submit" className="w-full" disabled={!file || upload.isPending}>
            Enviar currículo
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function ResumeRow({ resume }: { resume: ResumeItem }) {
  const update = useUpdateResume()
  const [renaming, setRenaming] = useState(false)

  function change(body: Parameters<typeof update.mutate>[0], success: string) {
    update.mutate(body, {
      onSuccess: () => toast.success(success),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card className={cn(resume.archived && 'opacity-60')}>
      <CardContent className="flex flex-wrap items-center gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-medium">{resume.label}</h3>
            {resume.isDefault && <Badge>Padrão</Badge>}
            {resume.archived && <Badge variant="outline">Arquivado</Badge>}
          </div>
          <p className="text-xs text-muted-foreground">
            {resume.targetRole ? `${resume.targetRole} · ` : ''}
            {resume.fileName} · {formatFileSize(resume.sizeBytes)} · enviado em {formatDate(resume.createdAt)}
          </p>
        </div>
        <dl className="flex gap-6 text-center text-sm">
          <div>
            <dt className="text-xs text-muted-foreground">Candidaturas</dt>
            <dd className="font-semibold">{resume.applications}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Entrevistas</dt>
            <dd className="font-semibold">{formatPercent(resume.interviewRate)}</dd>
          </div>
        </dl>
        <div className="flex gap-1">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Visualizar"
            title="Visualizar"
            onClick={() => openInNewTab(() => fetchResumeLink(resume.id)).catch((error: Error) => toast.error(error.message))}
          >
            <Eye />
          </Button>
          <Button variant="ghost" size="icon-sm" aria-label="Renomear" title="Renomear" onClick={() => setRenaming(true)}>
            <Pencil />
          </Button>
          {!resume.isDefault && !resume.archived && (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Definir como padrão"
              title="Definir como padrão"
              onClick={() => change({ id: resume.id, makeDefault: true }, `“${resume.label}” agora é o padrão.`)}
            >
              <Star />
            </Button>
          )}
          {!resume.isDefault && (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={resume.archived ? 'Desarquivar' : 'Arquivar'}
              title={resume.archived ? 'Desarquivar' : 'Arquivar'}
              onClick={() =>
                change(
                  { id: resume.id, archived: !resume.archived },
                  resume.archived ? 'Currículo desarquivado.' : 'Currículo arquivado.',
                )
              }
            >
              {resume.archived ? <ArchiveRestore /> : <Archive />}
            </Button>
          )}
        </div>
      </CardContent>
      {renaming && <RenameDialog resume={resume} onClose={() => setRenaming(false)} />}
    </Card>
  )
}

function RenameDialog({ resume, onClose }: { resume: ResumeItem; onClose: () => void }) {
  const update = useUpdateResume()
  const [label, setLabel] = useState(resume.label)
  const [targetRole, setTargetRole] = useState(resume.targetRole ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    update.mutate(
      { id: resume.id, label, targetRole },
      {
        onSuccess: () => {
          toast.success('Currículo atualizado.')
          onClose()
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Editar currículo</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="rename-label">Rótulo</Label>
            <Input id="rename-label" value={label} onChange={(event) => setLabel(event.target.value)} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="rename-role">Cargo-alvo</Label>
            <Input id="rename-role" value={targetRole} onChange={(event) => setTargetRole(event.target.value)} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={update.isPending}>
              Salvar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
