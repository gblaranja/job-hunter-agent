import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Download } from 'lucide-react'

const datasets = [
  { key: 'VAGAS', label: 'Vagas' },
  { key: 'CANDIDATURAS', label: 'Candidaturas' },
  { key: 'EVENTOS', label: 'Eventos das candidaturas' },
  { key: 'CONTATOS', label: 'Contatos' },
  { key: 'CONVERSAS', label: 'Conversas com contatos' },
] as const

/** Exportar os seus dados (LGPD) e onde apagar os de um contato. */
export function DataExportCard() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Seus dados</CardTitle>
        <CardDescription>
          Baixe vagas, candidaturas, eventos e contatos em JSON ou CSV (abre no Excel). Para apagar os dados de um
          contato e as conversas com ele, use “Excluir” em{' '}
          <Link to="/contacts" className="text-primary underline">
            Contatos
          </Link>
          .
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {datasets.map((dataset) => (
          <div key={dataset.key} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span>{dataset.label}</span>
            <div className="flex gap-1">
              {(['json', 'csv'] as const).map((format) => (
                <Button key={format} asChild variant="ghost" size="sm">
                  <a href={`/api/export/${dataset.key}?format=${format}`} download>
                    <Download /> {format.toUpperCase()}
                  </a>
                </Button>
              ))}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
