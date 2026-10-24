import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Link, useSearchParams } from 'react-router'
import {
  useCompanies,
  useMergeCompany,
  useRecognitionStatus,
  useRefreshMetrics,
  useUpdateCompany,
  type CompanyItem,
  type WatchStatus,
} from '@/api/queries'
import { CompanyBadge } from '@/components/CompanyBadge'
import { PageHeader } from '@/components/PageHeader'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { companyKindLabels, watchStatusLabels } from '@/lib/labels'
import { Award, GitMerge, ListChecks, RefreshCw, Search, Upload } from 'lucide-react'
import { GptwImportDialog } from './GptwImportDialog'
import { GptwReviewDialog } from './GptwReviewDialog'

const watchStatuses = Object.keys(watchStatusLabels) as WatchStatus[]

/** Empresas que apareceram nas vagas: acompanhe, marque como alvo e junte duplicatas. */
export function CompaniesPage() {
  const [params, setParams] = useSearchParams()
  const [gptwOnly, setGptwOnly] = useState(false)
  const companies = useCompanies({ gptw: gptwOnly })
  const recognition = useRecognitionStatus()
  const update = useUpdateCompany()
  const refresh = useRefreshMetrics()
  const [importing, setImporting] = useState(false)
  // a notificação "Confirme o selo" abre a fila direto: /companies?review=gptw
  const reviewing = params.get('review') === 'gptw'
  const [search, setSearch] = useState('')
  const [watch, setWatch] = useState<WatchStatus | 'TODAS'>('TODAS')
  const [merging, setMerging] = useState<CompanyItem | null>(null)
  const visible = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (companies.data ?? []).filter(
      (company) =>
        (watch === 'TODAS' || company.watchStatus === watch) &&
        (company.name.toLowerCase().includes(term) ||
          company.clientSectors.some((sector) => sector.toLowerCase().includes(term))),
    )
  }, [companies.data, search, watch])

  function recompute() {
    refresh.mutate(undefined, {
      onSuccess: () => toast.success('Métricas e alertas recalculados.'),
      onError: (error) => toast.error(error.message),
    })
  }

  function changeStatus(company: CompanyItem, watchStatus: WatchStatus) {
    update.mutate(
      {
        id: company.id,
        watchStatus,
        website: company.website,
        atsType: company.atsType,
        atsSlug: company.atsSlug,
        notes: company.notes,
      },
      {
        onSuccess: () => toast.success(`${company.name}: ${watchStatusLabels[watchStatus]}.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <>
      <PageHeader
        title="Empresas"
        description="Cadastradas a partir das vagas. Marque como alvo e informe o ATS para acompanhar todas as vagas abertas."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setParams({ review: 'gptw' })}>
              <ListChecks /> Revisar selos
              {!!recognition.data?.pendingReview && (
                <span className="rounded-full bg-primary px-1.5 text-xs tabular-nums text-primary-foreground">
                  {recognition.data.pendingReview}
                </span>
              )}
            </Button>
            <Button variant="outline" onClick={() => setImporting(true)}>
              <Upload /> Importar lista GPTW
            </Button>
            <Button variant="outline" onClick={recompute} disabled={refresh.isPending}>
              <RefreshCw className={refresh.isPending ? 'animate-spin' : undefined} /> Recalcular métricas
            </Button>
          </div>
        }
      />
      <div className="mb-4 flex flex-wrap gap-3">
        <div className="relative w-full max-w-md">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            className="pl-9"
            placeholder="Buscar empresa ou setor dos clientes"
            aria-label="Buscar empresa"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <Select value={watch} onValueChange={(value) => setWatch(value as WatchStatus | 'TODAS')}>
          <SelectTrigger className="w-44" aria-label="Filtrar por acompanhamento">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="TODAS">Todas</SelectItem>
            {watchStatuses.map((status) => (
              <SelectItem key={status} value={status}>
                {watchStatusLabels[status]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          variant={gptwOnly ? 'secondary' : 'outline'}
          aria-pressed={gptwOnly}
          onClick={() => setGptwOnly((value) => !value)}
        >
          <Award /> Apenas GPTW
        </Button>
      </div>
      {companies.isLoading && <LoadingBlock />}
      {companies.error && <ErrorBlock error={companies.error} />}
      {companies.isSuccess && visible.length === 0 && <EmptyBlock title="Nenhuma empresa encontrada." />}
      {visible.length > 0 && (
        <div className="rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Empresa</TableHead>
                <TableHead className="text-right">Ativas</TableHead>
                <TableHead className="text-right">Últimos 90 dias</TableHead>
                <TableHead className="text-right">Reposts</TableHead>
                <TableHead className="text-right">Dias no ar</TableHead>
                <TableHead>Última vaga</TableHead>
                <TableHead className="text-right">Candidaturas</TableHead>
                <TableHead>Acompanhamento</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((company) => (
                <TableRow key={company.id}>
                  <TableCell className="font-medium">
                    <Link to={`/companies/${company.id}`} className="hover:underline">
                      {company.name}
                    </Link>
                    <CompanyBadge level={company.gptwLevel} year={company.gptwYear} className="ml-2 align-middle" />
                    {company.kind !== 'CONTRATANTE' && (
                      <span className="ml-2 text-xs text-muted-foreground">{companyKindLabels[company.kind]}</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{company.jobsActive}</TableCell>
                  <TableCell className="text-right tabular-nums">{company.jobsLast90Days}</TableCell>
                  <TableCell className="text-right tabular-nums">{company.reposts}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {company.avgDaysOpen == null ? '—' : company.avgDaysOpen.toLocaleString('pt-BR')}
                  </TableCell>
                  <TableCell>{formatDate(company.lastJobAt)}</TableCell>
                  <TableCell className="text-right">{company.applications}</TableCell>
                  <TableCell>
                    <Select
                      value={company.watchStatus}
                      onValueChange={(value) => changeStatus(company, value as WatchStatus)}
                    >
                      <SelectTrigger className="h-8 w-36" aria-label={`Acompanhamento de ${company.name}`}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {watchStatuses.map((status) => (
                          <SelectItem key={status} value={status}>
                            {watchStatusLabels[status]}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="sm" onClick={() => setMerging(company)}>
                      <GitMerge /> Mesclar
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {reviewing && <GptwReviewDialog onClose={() => setParams({})} />}
      {importing && <GptwImportDialog onClose={() => setImporting(false)} />}
      {merging && (
        <MergeDialog
          source={merging}
          companies={(companies.data ?? []).filter((company) => company.id !== merging.id)}
          onClose={() => setMerging(null)}
        />
      )}
    </>
  )
}

function MergeDialog({
  source,
  companies,
  onClose,
}: {
  source: CompanyItem
  companies: CompanyItem[]
  onClose: () => void
}) {
  const merge = useMergeCompany()
  const [targetId, setTargetId] = useState<string | undefined>()

  function confirm() {
    if (!targetId) return
    merge.mutate(
      { id: source.id, targetId: Number(targetId) },
      {
        onSuccess: () => {
          toast.success(`${source.name} foi mesclada.`)
          onClose()
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Mesclar “{source.name}”</DialogTitle>
          <DialogDescription>
            As vagas passam para a empresa escolhida, e “{source.name}” vira um nome alternativo dela.
          </DialogDescription>
        </DialogHeader>
        <Select value={targetId} onValueChange={setTargetId}>
          <SelectTrigger className="w-full" aria-label="Empresa de destino">
            <SelectValue placeholder="Escolha a empresa de destino" />
          </SelectTrigger>
          <SelectContent>
            {companies.map((company) => (
              <SelectItem key={company.id} value={String(company.id)}>
                {company.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={confirm} disabled={!targetId || merge.isPending}>
            Mesclar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
