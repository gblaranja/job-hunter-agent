import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { useCompanies, useMergeCompany, useUpdateCompany, type CompanyItem, type WatchStatus } from '@/api/queries'
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
import { watchStatusLabels } from '@/lib/labels'
import { GitMerge, Search } from 'lucide-react'

const watchStatuses = Object.keys(watchStatusLabels) as WatchStatus[]

/** Empresas que apareceram nas vagas: acompanhe, marque como alvo e junte duplicatas. */
export function CompaniesPage() {
  const companies = useCompanies()
  const update = useUpdateCompany()
  const [search, setSearch] = useState('')
  const [merging, setMerging] = useState<CompanyItem | null>(null)
  const visible = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (companies.data ?? []).filter((company) => company.name.toLowerCase().includes(term))
  }, [companies.data, search])

  function changeStatus(company: CompanyItem, watchStatus: WatchStatus) {
    update.mutate(
      { id: company.id, watchStatus, website: company.website, atsType: company.atsType, atsSlug: company.atsSlug, notes: company.notes },
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
        description="Cadastradas automaticamente a partir das vagas. O histórico de volume e frequência chega na Fase 5."
      />
      <div className="relative mb-4 max-w-md">
        <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          className="pl-9"
          placeholder="Buscar empresa"
          aria-label="Buscar empresa"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
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
                <TableHead className="text-right">Vagas</TableHead>
                <TableHead className="text-right">Últimos 90 dias</TableHead>
                <TableHead>Última vaga</TableHead>
                <TableHead className="text-right">Candidaturas</TableHead>
                <TableHead>Acompanhamento</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((company) => (
                <TableRow key={company.id}>
                  <TableCell className="font-medium">{company.name}</TableCell>
                  <TableCell className="text-right">{company.jobsTotal}</TableCell>
                  <TableCell className="text-right">{company.jobsLast90Days}</TableCell>
                  <TableCell>{formatDate(company.lastJobAt)}</TableCell>
                  <TableCell className="text-right">{company.applications}</TableCell>
                  <TableCell>
                    <Select value={company.watchStatus} onValueChange={(value) => changeStatus(company, value as WatchStatus)}>
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

function MergeDialog({ source, companies, onClose }: { source: CompanyItem; companies: CompanyItem[]; onClose: () => void }) {
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
