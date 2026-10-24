import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import {
  useChangeWatchStatus,
  useNicheCompanies,
  useNiches,
  useRecomputeNiches,
  type NicheCompany,
  type NicheFilters,
  type NicheView,
} from '@/api/queries'
import { CompanyBadge } from '@/components/CompanyBadge'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { fitBandLabels, nicheSourceLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { RefreshCw, Search, SlidersHorizontal, Target } from 'lucide-react'
import { SegmentMap } from './SegmentMap'
import { Sparkline } from './Sparkline'
import { WeightsDialog } from './WeightsDialog'

const ALL = 'TODOS'

/** Empresas dos nichos (Financeiro, Consultorias e os que você cadastrar), ordenadas pelo fit com o seu perfil. */
export function NichesPage() {
  const niches = useNiches()
  const recompute = useRecomputeNiches()
  const [active, setActive] = useState<string>()
  const [editing, setEditing] = useState<NicheView | null>(null)
  const list = niches.data?.filter((niche) => niche.active) ?? []
  const current = list.find((niche) => String(niche.id) === active) ?? list[0]

  function recalculate() {
    recompute.mutate(undefined, {
      onSuccess: (result) => toast.success(`Fit recalculado para ${result.companies} empresas.`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <>
      <PageHeader
        title="Nichos"
        description="Empresas por fit com o seu perfil, mesmo sem vaga aberta: subsegmento, stack e domínio das vagas, selo GPTW e ritmo de contratação."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={recalculate} disabled={recompute.isPending}>
              <RefreshCw className={recompute.isPending ? 'animate-spin' : undefined} /> Recalcular fit
            </Button>
            {current && (
              <Button variant="outline" onClick={() => setEditing(current)}>
                <SlidersHorizontal /> Pesos
              </Button>
            )}
          </div>
        }
      />
      {niches.isLoading && <LoadingBlock />}
      {niches.error && <ErrorBlock error={niches.error} />}
      {niches.isSuccess && list.length === 0 && <EmptyBlock title="Nenhum nicho ativo." />}
      {current && (
        <Tabs value={String(current.id)} onValueChange={setActive}>
          <TabsList>
            {list.map((niche) => (
              <TabsTrigger key={niche.id} value={String(niche.id)}>
                {niche.name}
                <span className="ml-1 tabular-nums text-muted-foreground">{niche.companies}</span>
              </TabsTrigger>
            ))}
          </TabsList>
          {list.map((niche) => (
            <TabsContent key={niche.id} value={String(niche.id)} className="mt-4">
              {niche.id === current.id && <NicheTab niche={niche} />}
            </TabsContent>
          ))}
        </Tabs>
      )}
      {editing && <WeightsDialog niche={editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function NicheTab({ niche }: { niche: NicheView }) {
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 300)
  const [filters, setFilters] = useState<Omit<NicheFilters, 'q'>>({ sort: 'fit', page: 0, size: 50 })
  const companies = useNicheCompanies(niche.id, { ...filters, q })
  // consultorias: o filtro mais útil é "alocam em bancos e fintechs"
  const consultancies = niche.companyKinds.includes('CONSULTORIA_TI')

  function update(change: Partial<NicheFilters>) {
    setFilters((previous) => ({ ...previous, page: 0, ...change }))
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Mapa por subsegmento</CardTitle>
          <CardDescription>
            Área proporcional ao número de empresas. Clique num bloco para filtrar; passe o mouse para ver o fit alto e as
            vagas abertas.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <SegmentMap segments={niche.segments} selected={filters.segment} onSelect={(segment) => update({ segment })} />
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-full max-w-xs">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            className="pl-9"
            placeholder="Buscar empresa"
            aria-label="Buscar empresa no nicho"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setFilters((previous) => ({ ...previous, page: 0 }))
            }}
          />
        </div>
        <Select
          value={filters.segment === undefined ? ALL : String(filters.segment)}
          onValueChange={(value) => update({ segment: value === ALL ? undefined : Number(value) })}
        >
          <SelectTrigger className="w-60" aria-label="Filtrar por subsegmento">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todos os subsegmentos</SelectItem>
            {niche.segments.map((segment) => (
              <SelectItem key={segment.id} value={String(segment.id)}>
                {segment.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Toggle pressed={!!filters.gptw} onChange={(gptw) => update({ gptw })}>
          Apenas GPTW
        </Toggle>
        <Toggle pressed={!!filters.hasOpenJobs} onChange={(hasOpenJobs) => update({ hasOpenJobs })}>
          Com vaga aberta
        </Toggle>
        <Toggle pressed={!!filters.neverApplied} onChange={(neverApplied) => update({ neverApplied })}>
          Nunca apliquei
        </Toggle>
        {consultancies && (
          <Toggle pressed={!!filters.financialClients} onChange={(financialClients) => update({ financialClients })}>
            Alocam em bancos e fintechs
          </Toggle>
        )}
        <Select value={filters.sort} onValueChange={(sort) => update({ sort: sort as NicheFilters['sort'] })}>
          <SelectTrigger className="w-44" aria-label="Ordenar">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="fit">Maior fit</SelectItem>
            <SelectItem value="jobs">Mais vagas ativas</SelectItem>
            <SelectItem value="name">Nome</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {companies.isLoading && <LoadingBlock />}
      {companies.error && <ErrorBlock error={companies.error} />}
      {companies.data && companies.data.total === 0 && (
        <EmptyBlock title="Nenhuma empresa com esses filtros." />
      )}
      {companies.data && companies.data.items.length > 0 && (
        <>
          <div className="rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Empresa</TableHead>
                  <TableHead>Subsegmento</TableHead>
                  <TableHead className="text-right">Fit</TableHead>
                  <TableHead className="text-right">Vagas ativas</TableHead>
                  <TableHead>12 semanas</TableHead>
                  <TableHead>Por quê</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {companies.data.items.map((company) => (
                  <CompanyRow key={company.id} company={company} />
                ))}
              </TableBody>
            </Table>
          </div>
          <Pagination
            page={companies.data.page}
            size={companies.data.size}
            total={companies.data.total}
            onPageChange={(page) => setFilters((previous) => ({ ...previous, page }))}
            onSizeChange={(size) => update({ size })}
            itemLabel="empresas"
          />
        </>
      )}
    </div>
  )
}

function CompanyRow({ company }: { company: NicheCompany }) {
  const watch = useChangeWatchStatus()

  function target() {
    watch.mutate(
      { id: company.id, watchStatus: 'ALVO' },
      {
        onSuccess: () => toast.success(`${company.name} agora é alvo: o job board é consultado todo dia.`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <TableRow>
      <TableCell className="max-w-64">
        <div className="flex flex-wrap items-center gap-1.5">
          <Link to={`/companies/${company.id}`} className="font-medium hover:underline">
            {company.name}
          </Link>
          <CompanyBadge level={company.gptwLevel} year={company.gptwYear} />
        </div>
        {company.authorizationType && <p className="text-xs text-muted-foreground">{company.authorizationType}</p>}
      </TableCell>
      <TableCell>
        <p>{company.segmentName}</p>
        <p className="text-xs text-muted-foreground">
          {nicheSourceLabels[company.source]}
          {!company.verified && company.source !== 'BCB' && company.source !== 'CNPJ' && ' · a confirmar'}
        </p>
      </TableCell>
      <TableCell className="text-right">
        <FitScore score={company.fitScore} band={company.fitBand} />
      </TableCell>
      <TableCell className="text-right tabular-nums">{company.jobsActive}</TableCell>
      <TableCell>
        <Sparkline values={company.trend} />
      </TableCell>
      <TableCell className="max-w-96 text-sm text-muted-foreground">{company.reasons.join(' · ')}</TableCell>
      <TableCell className="text-right">
        {company.watchStatus === 'ALVO' ? (
          <Badge variant="secondary">
            <Target aria-hidden /> Alvo
          </Badge>
        ) : (
          <Button variant="ghost" size="sm" onClick={target} disabled={watch.isPending}>
            <Target /> Marcar como alvo
          </Button>
        )}
      </TableCell>
    </TableRow>
  )
}

/** Fit de 0 a 100 com a faixa escrita: a cor só reforça o texto. */
function FitScore({ score, band }: { score?: number | null; band?: NicheCompany['fitBand'] }) {
  if (score == null || !band) return <span className="text-muted-foreground">—</span>
  return (
    <span className="inline-flex flex-col items-end">
      <span
        className={cn(
          'text-base font-semibold tabular-nums',
          band === 'ALTO' ? 'text-success' : band === 'MEDIO' ? 'text-foreground' : 'text-muted-foreground',
        )}
      >
        {score}
      </span>
      <span className="text-xs text-muted-foreground">{fitBandLabels[band]}</span>
    </span>
  )
}

function Toggle({
  pressed,
  onChange,
  children,
}: {
  pressed: boolean
  onChange: (pressed: boolean) => void
  children: ReactNode
}) {
  return (
    <Button
      type="button"
      variant={pressed ? 'secondary' : 'outline'}
      size="sm"
      aria-pressed={pressed}
      onClick={() => onChange(!pressed)}
      className={cn(pressed && 'border-primary/40')}
    >
      {children}
    </Button>
  )
}
