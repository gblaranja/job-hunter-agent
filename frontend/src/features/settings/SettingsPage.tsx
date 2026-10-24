import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import {
  useCollectNow,
  useCollectionRuns,
  useDeleteSearchProfile,
  useDisconnectGmail,
  useGmailStatus,
  useRecognitionStatus,
  useRunSeed,
  useSaveSearchProfile,
  useSearchProfiles,
  useSeedSources,
  useVerifyLinks,
  type CollectionRun,
  type SearchProfile,
} from '@/api/queries'
import { PageHeader } from '@/components/PageHeader'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateTime } from '@/lib/format'
import { useTheme } from '@/lib/useTheme'
import { Database, Link2, Mail, Monitor, Moon, Pencil, Play, Plus, Sun, Trash2 } from 'lucide-react'

export function SettingsPage() {
  const [params, setParams] = useSearchParams()

  // Volta do consentimento do Google: /settings?gmail=conectado|erro
  useEffect(() => {
    const result = params.get('gmail')
    if (!result) return
    if (result === 'conectado') toast.success('Gmail conectado.')
    else toast.error('Não foi possível conectar o Gmail. Tente de novo.')
    params.delete('gmail')
    setParams(params, { replace: true })
  }, [params, setParams])

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Configurações"
        description="Conexão com o Gmail, coleta de vagas, perfis de busca e as bases públicas de empresas."
      />
      <AppearanceCard />
      <GmailCard />
      <CollectionCard />
      <ProfilesCard />
      <SeedsCard />
    </div>
  )
}

function AppearanceCard() {
  const { theme, setTheme } = useTheme()

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Sun className="size-4" aria-hidden /> Aparência
        </CardTitle>
        <CardDescription>Escolha como o aplicativo será exibido. A preferência fica salva neste navegador.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        <Label htmlFor="theme-preference">Tema</Label>
        <Select value={theme} onValueChange={(value) => setTheme(value as 'system' | 'light' | 'dark')}>
          <SelectTrigger id="theme-preference" className="w-full sm:w-72" aria-label="Tema">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="system"><Monitor aria-hidden /> Padrão do sistema</SelectItem>
            <SelectItem value="light"><Sun aria-hidden /> Claro</SelectItem>
            <SelectItem value="dark"><Moon aria-hidden /> Escuro</SelectItem>
          </SelectContent>
        </Select>
      </CardContent>
    </Card>
  )
}

function GmailCard() {
  const status = useGmailStatus()
  const disconnect = useDisconnectGmail()

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Mail className="size-4" aria-hidden /> Gmail
        </CardTitle>
        <CardDescription>
          A coleta lê só a label configurada, com acesso de leitura. Crie filtros no Gmail para aplicar a label aos alertas
          do LinkedIn, da Gupy e aos e-mails de recrutadores.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {status.isLoading && <LoadingBlock lines={1} />}
        {status.error && <ErrorBlock error={status.error} />}
        {status.data && (
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="space-y-1 text-sm">
              <div className="flex items-center gap-2">
                {status.data.connected ? <Badge>Conectado</Badge> : <Badge variant="outline">Desconectado</Badge>}
                {status.data.accountEmail && <span>{status.data.accountEmail}</span>}
              </div>
              <p className="text-muted-foreground">
                Label <strong>{status.data.label}</strong> · última sincronização {formatDateTime(status.data.lastSyncAt)}
              </p>
              {!status.data.configured && (
                <p className="text-destructive">
                  Defina JOBHUNTER_GMAIL_CLIENT_ID e JOBHUNTER_GMAIL_CLIENT_SECRET no backend para habilitar a conexão.
                </p>
              )}
            </div>
            <div className="flex gap-2">
              {status.data.connected && (
                <Button variant="outline" onClick={() => disconnect.mutate()} disabled={disconnect.isPending}>
                  Desconectar
                </Button>
              )}
              {status.data.configured ? (
                // Navegação completa: o backend redireciona para o consentimento do Google.
                <Button asChild>
                  <a href="/api/admin/gmail/connect">{status.data.connected ? 'Reconectar' : 'Conectar Gmail'}</a>
                </Button>
              ) : (
                <Button disabled>Conectar Gmail</Button>
              )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function CollectionCard() {
  const runs = useCollectionRuns()
  const collect = useCollectNow()
  const verify = useVerifyLinks()

  function checkLinks() {
    verify.mutate(undefined, {
      onSuccess: (summary) =>
        toast.success(
          `${summary.checked} links verificados: ${summary.active} no ar, ${summary.removed} fora do ar, ${summary.indeterminate} sem resposta clara.`,
        ),
      onError: (error) => toast.error(error.message),
    })
  }

  function run() {
    collect.mutate(undefined, {
      onSuccess: (result) => {
        const totals = totalsOf(result)
        if (result.status === 'FALHOU') toast.error(result.error ?? 'A coleta falhou.')
        else toast.success(`Coleta concluída: ${totals.created} vagas novas, ${totals.duplicate} repetidas.`)
      },
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Coleta de vagas</CardTitle>
        <CardDescription>
          A coleta roda todo dia às 7h e classifica os retornos; os links das vagas aplicadas são verificados toda segunda.
        </CardDescription>
        <CardAction className="flex gap-2">
          <Button variant="outline" onClick={checkLinks} disabled={verify.isPending}>
            <Link2 /> {verify.isPending ? 'Verificando…' : 'Verificar links'}
          </Button>
          <Button onClick={run} disabled={collect.isPending}>
            <Play /> {collect.isPending ? 'Coletando…' : 'Coletar agora'}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {runs.isLoading && <LoadingBlock lines={1} />}
        {runs.error && <ErrorBlock error={runs.error} />}
        {runs.data && runs.data.length === 0 && <p className="text-sm text-muted-foreground">Nenhuma coleta ainda.</p>}
        {runs.data && runs.data.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Início</TableHead>
                <TableHead>Origem</TableHead>
                <TableHead>Situação</TableHead>
                <TableHead className="text-right">Novas</TableHead>
                <TableHead className="text-right">Repetidas</TableHead>
                <TableHead className="text-right">Excluídas</TableHead>
                <TableHead className="text-right">Erros</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {runs.data.map((item) => {
                const totals = totalsOf(item)
                return (
                  <TableRow key={item.id}>
                    <TableCell>{formatDateTime(item.startedAt)}</TableCell>
                    <TableCell>{item.trigger === 'MANUAL' ? 'Manual' : 'Agendada'}</TableCell>
                    <TableCell title={item.error ?? undefined}>
                      <Badge variant={item.status === 'FALHOU' ? 'destructive' : 'outline'}>{statusLabel(item.status)}</Badge>
                    </TableCell>
                    <TableCell className="text-right">{totals.created}</TableCell>
                    <TableCell className="text-right">{totals.duplicate}</TableCell>
                    <TableCell className="text-right">{totals.excluded}</TableCell>
                    <TableCell className="text-right">{totals.errors}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function totalsOf(run: CollectionRun) {
  return Object.values(run.stats).reduce(
    (sum, source) => ({
      created: sum.created + source.created,
      duplicate: sum.duplicate + source.duplicate,
      excluded: sum.excluded + source.excluded,
      errors: sum.errors + source.errors,
    }),
    { created: 0, duplicate: 0, excluded: 0, errors: 0 },
  )
}

function statusLabel(status: string) {
  return ({ RODANDO: 'Rodando', CONCLUIDA: 'Concluída', FALHOU: 'Falhou' } as Record<string, string>)[status] ?? status
}

function ProfilesCard() {
  const profiles = useSearchProfiles()
  const remove = useDeleteSearchProfile()
  const [editing, setEditing] = useState<SearchProfile | 'new' | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Perfis de busca</CardTitle>
        <CardDescription>
          As palavras-chave orientam os alertas nas plataformas; as exclusões descartam vagas na coleta.
        </CardDescription>
        <CardAction>
          <Button variant="outline" onClick={() => setEditing('new')}>
            <Plus /> Novo perfil
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        {profiles.isLoading && <LoadingBlock lines={1} />}
        {profiles.error && <ErrorBlock error={profiles.error} />}
        {profiles.data?.map((profile) => (
          <div key={profile.id} className="flex flex-wrap items-start justify-between gap-3 rounded-md border p-3">
            <div className="space-y-1 text-sm">
              <p className="font-medium">
                {profile.name} {!profile.active && <Badge variant="outline">Inativo</Badge>}{' '}
                {profile.remoteOnly && <Badge variant="secondary">Só remoto</Badge>}
              </p>
              <p className="text-muted-foreground">Palavras-chave: {profile.keywords.join(', ') || '—'}</p>
              <p className="text-muted-foreground">Exclusões: {profile.excludedKeywords.join(', ') || '—'}</p>
              <p className="text-muted-foreground">Localidades: {profile.locations.join(', ') || '—'}</p>
            </div>
            <div className="flex gap-1">
              <Button variant="ghost" size="icon-sm" aria-label={`Editar ${profile.name}`} onClick={() => setEditing(profile)}>
                <Pencil />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`Excluir ${profile.name}`}
                onClick={() => remove.mutate(profile.id, { onError: (error) => toast.error(error.message) })}
              >
                <Trash2 />
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
      {editing && <ProfileDialog profile={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </Card>
  )
}

const split = (value: string) =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

function ProfileDialog({ profile, onClose }: { profile?: SearchProfile; onClose: () => void }) {
  const save = useSaveSearchProfile()
  const [name, setName] = useState(profile?.name ?? '')
  const [keywords, setKeywords] = useState(profile?.keywords.join(', ') ?? '')
  const [excluded, setExcluded] = useState(profile?.excludedKeywords.join(', ') ?? '')
  const [locations, setLocations] = useState(profile?.locations.join(', ') ?? '')
  const [remoteOnly, setRemoteOnly] = useState(profile?.remoteOnly ?? false)
  const [active, setActive] = useState(profile?.active ?? true)

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate(
      {
        id: profile?.id,
        name,
        keywords: split(keywords),
        excludedKeywords: split(excluded),
        locations: split(locations),
        remoteOnly,
        active,
      },
      {
        onSuccess: () => {
          toast.success('Perfil salvo.')
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
            <DialogTitle>{profile ? 'Editar perfil de busca' : 'Novo perfil de busca'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="profile-name">Nome</Label>
            <Input id="profile-name" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="profile-keywords">Palavras-chave (separadas por vírgula)</Label>
            <Input id="profile-keywords" value={keywords} onChange={(e) => setKeywords(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="profile-excluded">Exclusões</Label>
            <Input id="profile-excluded" value={excluded} onChange={(e) => setExcluded(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="profile-locations">Localidades</Label>
            <Input id="profile-locations" value={locations} onChange={(e) => setLocations(e.target.value)} />
          </div>
          <div className="flex gap-6 text-sm">
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={remoteOnly} onChange={(e) => setRemoteOnly(e.target.checked)} /> Só remoto
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Ativo
            </label>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={save.isPending}>
              Salvar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Bases públicas que populam os nichos (Banco Central todo mês; Receita sob demanda) e o uso da busca na web que
 * verifica os selos GPTW.
 */
function SeedsCard() {
  const sources = useSeedSources()
  const run = useRunSeed()
  const recognition = useRecognitionStatus()

  function start(key: string, label: string) {
    run.mutate(key, {
      onSuccess: () => toast.success(`Importação de ${label} iniciada; o resultado chega no sino.`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Bases de empresas e selos</CardTitle>
        <CardDescription>
          As empresas importadas entram como descobertas, já no subsegmento do nicho, e não disparam buscas pagas. Os
          dados de sócios da Receita nunca são lidos.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {sources.isLoading && <LoadingBlock />}
        {sources.error && <ErrorBlock error={sources.error} />}
        <ul className="divide-y rounded-md border">
          {sources.data?.map((source) => (
            <li key={source.key} className="flex flex-wrap items-center justify-between gap-3 p-3">
              <div className="min-w-0">
                <p className="font-medium">{source.label}</p>
                <p className="text-sm text-muted-foreground">
                  {source.available ? (source.key === 'bcb' ? 'Roda sozinha no dia 1 de cada mês.' : 'Pronta para rodar.') : source.unavailableReason}
                </p>
              </div>
              <Button
                variant="outline"
                onClick={() => start(source.key, source.label)}
                disabled={!source.available || source.running || run.isPending}
              >
                <Database /> {source.running ? 'Importando…' : 'Importar agora'}
              </Button>
            </li>
          ))}
        </ul>
        {recognition.data && (
          <p className="text-sm text-muted-foreground">
            {recognition.data.enabled
              ? `Busca do selo GPTW (${recognition.data.provider}): ${recognition.data.searchesThisMonth} de ${recognition.data.monthlyLimit} consultas usadas neste mês.`
              : 'Busca automática do selo GPTW desligada: configure JOBHUNTER_SEARCH_API_KEY e a Claude API.'}
          </p>
        )}
      </CardContent>
    </Card>
  )
}
