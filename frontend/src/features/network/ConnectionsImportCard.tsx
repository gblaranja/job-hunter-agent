import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { useImportConnections, type ConnectionsImport } from '@/api/queries'
import { LinkedinIcon } from '@/components/brand/LinkedinIcon'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Upload } from 'lucide-react'

/**
 * Importação do Connections.csv do export oficial do LinkedIn. Nada é feito na sua conta: você baixa o arquivo e
 * envia aqui; reimportar atualiza empresa e cargo sem duplicar.
 */
export function ConnectionsImportCard() {
  const importConnections = useImportConnections()
  const input = useRef<HTMLInputElement>(null)
  const [result, setResult] = useState<ConnectionsImport | null>(null)

  function upload(file: File | undefined) {
    if (!file) return
    importConnections.mutate(file, {
      onSuccess: (summary) => {
        setResult(summary)
        toast.success('Conexões importadas.')
      },
      onError: (error) => toast.error(error.message),
      onSettled: () => {
        if (input.current) input.current.value = ''
      },
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <LinkedinIcon /> Conexões do LinkedIn
        </CardTitle>
        <CardDescription>
          Mostra na caixa de vagas quem você conhece em cada empresa, para pedir indicação.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        <ol className="list-decimal space-y-1 pl-5 text-muted-foreground">
          <li>No LinkedIn, abra Configurações → Privacidade dos dados → Obter uma cópia dos seus dados.</li>
          <li>Marque “Conexões” e peça o arquivo. O LinkedIn manda o link por e-mail, em geral em minutos.</li>
          <li>Descompacte e envie aqui o arquivo Connections.csv.</li>
        </ol>
        <p className="text-muted-foreground">
          Ficam só nome, empresa, cargo, link do perfil e data da conexão (o e-mail só vem quando a pessoa permite). Você
          pode apagar qualquer contato em <Link to="/contacts" className="underline">Contatos</Link>.
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <input
            ref={input}
            type="file"
            accept=".csv,text/csv"
            className="sr-only"
            aria-label="Arquivo Connections.csv"
            onChange={(event) => upload(event.target.files?.[0])}
          />
          <Button variant="outline" onClick={() => input.current?.click()} disabled={importConnections.isPending}>
            <Upload /> {importConnections.isPending ? 'Importando…' : 'Enviar Connections.csv'}
          </Button>
          {result && (
            <p role="status">
              {result.created} novas, {result.updated} atualizadas; {result.matchedCompanies} em empresas que você já
              acompanha.
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
