import { toast } from 'sonner'
import { useCheckRecognition, useCompanyRecognitions, useRecognitionStatus } from '@/api/queries'
import { LoadingBlock } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate } from '@/lib/format'
import { recognitionLevelLabels } from '@/lib/labels'
import { ExternalLink, RefreshCw } from 'lucide-react'

const reviewLabels: Record<string, string> = {
  AUTOMATICO: 'verificado pela IA',
  CONFIRMADO: 'confirmado por você',
  PENDENTE: 'aguardando revisão',
}

/** Histórico de certificações e rankings GPTW da empresa, com o link de cada evidência. */
export function GptwHistoryCard({ companyId }: { companyId: number }) {
  const history = useCompanyRecognitions(companyId)
  const status = useRecognitionStatus()
  const check = useCheckRecognition()

  function verify() {
    check.mutate(companyId, {
      onSuccess: (result) => toast.success(`Verificação concluída: ${recognitionLevelLabels[result.level].toLowerCase()}.`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Selo GPTW</CardTitle>
        <CardDescription>
          Busca na web verificada pela IA; sem menção explícita à empresa e ao ano, conta como não encontrado.
          {status.data && !status.data.enabled && ' A busca automática está desligada: use a importação da lista na tela de Empresas.'}
        </CardDescription>
        <CardAction>
          <Button variant="outline" size="sm" onClick={verify} disabled={check.isPending || !status.data?.enabled}>
            <RefreshCw className={check.isPending ? 'animate-spin' : undefined} /> Verificar agora
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {history.isLoading && <LoadingBlock />}
        {history.data && history.data.length === 0 && (
          <p className="text-sm text-muted-foreground">Ainda não verificada.</p>
        )}
        <ul className="space-y-3">
          {history.data?.map((item) => (
            <li key={item.id} className="flex flex-wrap items-start justify-between gap-2 border-b pb-3 last:border-0 last:pb-0">
              <div className="min-w-0">
                <p className="font-medium">
                  {recognitionLevelLabels[item.level]}
                  {item.year ? ` ${item.year}` : ''}
                  {item.rankingName ? <span className="font-normal text-muted-foreground"> · {item.rankingName}</span> : null}
                  {item.rankingPosition ? (
                    <span className="font-normal text-muted-foreground"> · {item.rankingPosition}º</span>
                  ) : null}
                </p>
                <p className="text-xs text-muted-foreground">
                  {item.source === 'IMPORTACAO' ? 'Importado da lista' : 'Busca na web'} · {formatDate(item.checkedAt)}
                  {item.validUntil ? ` · válido até ${formatDate(item.validUntil)}` : ''}
                </p>
                {item.rationale && <p className="mt-1 text-sm text-muted-foreground">{item.rationale}</p>}
                {item.evidenceUrl && (
                  <a
                    href={item.evidenceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-1 inline-flex items-center gap-1 text-sm text-primary hover:underline"
                  >
                    <ExternalLink className="size-3.5" aria-hidden /> Evidência
                  </a>
                )}
              </div>
              {reviewLabels[item.reviewStatus] && <Badge variant="outline">{reviewLabels[item.reviewStatus]}</Badge>}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
