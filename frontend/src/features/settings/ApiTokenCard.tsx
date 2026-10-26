import { useState } from 'react'
import { toast } from 'sonner'
import { useApiToken, useGenerateApiToken, useRevokeApiToken } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { formatDateTime } from '@/lib/format'

/** Token pessoal da extensão "Salvar vaga": aparece uma vez, vale só para importar e salvar vagas. */
export function ApiTokenCard() {
  const status = useApiToken()
  const generate = useGenerateApiToken()
  const revoke = useRevokeApiToken()
  const [token, setToken] = useState<string | null>(null)

  function create() {
    generate.mutate(undefined, {
      onSuccess: (result) => setToken(result.token),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Extensão do navegador</CardTitle>
        <CardDescription>
          A extensão "Salvar vaga" lê só a página aberta, quando você clica, e usa este token para importar e salvar a vaga.
          O token não abre nenhuma outra tela da API. Gerar outro invalida o anterior.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {status.data?.exists ? (
          <p className="text-sm text-muted-foreground">
            Token terminado em <strong>{status.data.hint}</strong>, criado em {formatDateTime(status.data.createdAt!)}
            {status.data.lastUsedAt ? `; último uso em ${formatDateTime(status.data.lastUsedAt)}` : '; ainda não usado'}.
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">Nenhum token ativo.</p>
        )}
        {token && (
          <div className="space-y-1">
            <p className="text-sm">Copie agora e cole nas opções da extensão; ele não aparece de novo.</p>
            <Input readOnly value={token} aria-label="Token gerado" onFocus={(event) => event.target.select()} />
          </div>
        )}
        <div className="flex gap-2">
          <Button onClick={create} disabled={generate.isPending}>
            {status.data?.exists ? 'Gerar outro token' : 'Gerar token'}
          </Button>
          {status.data?.exists && (
            <Button
              variant="outline"
              disabled={revoke.isPending}
              onClick={() => revoke.mutate(undefined, { onSuccess: () => setToken(null) })}
            >
              Revogar
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
