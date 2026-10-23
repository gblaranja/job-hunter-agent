import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { useApplications, type AppliedListItem, type ApplicationStatus } from '@/api/queries'
import { PageHeader } from '@/components/PageHeader'
import { EmptyBlock, ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { LinkStatusBadge, StatusBadge } from '@/components/StatusBadge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { formatDate } from '@/lib/format'
import { statusesRequiringResume, statusLabels } from '@/lib/labels'
import { ArrowDownUp, ExternalLink, Search } from 'lucide-react'

const ALL = 'TODAS'
// Uma vaga aplicada pode ser encerrada depois; ela continua na lista das aplicadas.
const appliedStatuses = (Object.keys(statusLabels) as ApplicationStatus[]).filter(
  (status) => statusesRequiringResume.has(status) || status === 'ENCERRADA',
)

const columns: ColumnDef<AppliedListItem>[] = [
  {
    accessorKey: 'company',
    header: 'Empresa',
    cell: ({ row }) => <span className="font-medium">{row.original.company}</span>,
  },
  {
    accessorKey: 'title',
    header: 'Cargo',
    cell: ({ row }) => (
      <div className="max-w-md">
        <p>{row.original.title}</p>
        {row.original.excerpt && (
          <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{row.original.excerpt}</p>
        )}
      </div>
    ),
  },
  {
    accessorKey: 'appliedAt',
    header: 'Aplicada em',
    cell: ({ row }) => formatDate(row.original.appliedAt),
  },
  {
    accessorKey: 'status',
    header: 'Etapa',
    cell: ({ row }) => <StatusBadge status={row.original.status} />,
  },
  {
    id: 'link',
    header: 'Link',
    enableSorting: false,
    cell: ({ row }) => (
      <div className="flex items-center gap-2">
        {row.original.url && (
          <a
            href={row.original.url}
            target="_blank"
            rel="noopener noreferrer"
            onClick={(event) => event.stopPropagation()}
            className="text-primary"
            aria-label={`Abrir a vaga ${row.original.title}`}
          >
            <ExternalLink className="size-4" />
          </a>
        )}
        <LinkStatusBadge status={row.original.linkStatus} />
      </div>
    ),
  },
  {
    accessorKey: 'resumeLabel',
    header: 'Currículo',
    cell: ({ row }) => row.original.resumeLabel ?? '—',
  },
]

export function ApplicationsPage() {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<string>(ALL)
  const [sorting, setSorting] = useState<SortingState>([])
  const query = useDebouncedValue(search.trim())
  const applications = useApplications(status === ALL ? undefined : (status as ApplicationStatus), query)
  const data = useMemo(() => applications.data ?? [], [applications.data])

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  return (
    <>
      <PageHeader
        title="Minhas candidaturas"
        description="Todas as vagas em que você aplicou, com o link e a descrição salvos."
      />
      <div className="mb-4 flex flex-wrap gap-3">
        <div className="relative min-w-64 flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            className="pl-9"
            placeholder="Buscar por empresa, cargo ou texto da vaga (ex.: Kafka)"
            aria-label="Buscar candidaturas"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-48" aria-label="Filtrar por etapa">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todas as etapas</SelectItem>
            {appliedStatuses.map((option) => (
              <SelectItem key={option} value={option}>
                {statusLabels[option]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {applications.isLoading && <LoadingBlock />}
      {applications.error && <ErrorBlock error={applications.error} />}
      {applications.isSuccess && data.length === 0 && (
        <EmptyBlock title={query ? `Nada encontrado para “${query}”.` : 'Nenhuma candidatura registrada ainda.'}>
          {!query && 'Cadastre uma vaga em “Nova vaga” ou mova um card para Aplicada.'}
        </EmptyBlock>
      )}
      {data.length > 0 && (
        <div className="rounded-lg border bg-card">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((group) => (
                <TableRow key={group.id}>
                  {group.headers.map((header) => (
                    <TableHead key={header.id}>
                      {header.column.getCanSort() ? (
                        <button
                          type="button"
                          className="inline-flex items-center gap-1"
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          <ArrowDownUp className="size-3 text-muted-foreground" aria-hidden />
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="cursor-pointer"
                  onClick={() => navigate(`/jobs/${row.original.jobId}`)}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="align-top">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  )
}
