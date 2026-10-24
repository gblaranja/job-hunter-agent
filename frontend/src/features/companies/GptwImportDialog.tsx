import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useConfirmImport, useImportPreview, type ImportItem, type ImportPreview } from '@/api/queries'
import { Badge } from '@/components/ui/badge'
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
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'

type Level = 'RANKING_TI' | 'RANKING' | 'CERTIFICADA'

const levels: Record<Level, string> = {
  RANKING_TI: 'Ranking GPTW TI',
  RANKING: 'Outro ranking GPTW',
  CERTIFICADA: 'Certificação GPTW',
}

const matchLabels: Record<ImportItem['match'], string> = {
  EXATO: 'mesmo nome',
  SIMILAR: 'nome parecido',
  NENHUM: 'não cadastrada',
}

/**
 * Importação da lista anual (GPTW TI, por exemplo): cole o texto ou o link, confira a prévia com as empresas
 * cadastradas e confirme. Os nomes parecidos começam desmarcados.
 */
export function GptwImportDialog({ onClose }: { onClose: () => void }) {
  const preview = useImportPreview()
  const confirm = useConfirmImport()
  const [text, setText] = useState('')
  const [url, setUrl] = useState('')
  const [level, setLevel] = useState<Level>('RANKING_TI')
  const [year, setYear] = useState(String(new Date().getFullYear()))
  const [rankingName, setRankingName] = useState('')
  const [result, setResult] = useState<ImportPreview | null>(null)
  const [chosen, setChosen] = useState<Set<number>>(new Set())

  function read(event: FormEvent) {
    event.preventDefault()
    preview.mutate(
      { text: text.trim() || null, url: url.trim() || null },
      {
        onSuccess: (data) => {
          setResult(data)
          setChosen(new Set(data.items.filter((item) => item.match === 'EXATO').map((item) => item.companyId!)))
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function save() {
    if (!result) return
    const items = result.items
      .filter((item) => item.companyId != null && chosen.has(item.companyId))
      .map((item) => ({ companyId: item.companyId!, position: item.position ?? null }))
    confirm.mutate(
      {
        level,
        year: Number(year),
        rankingName: rankingName.trim() || null,
        evidenceUrl: url.trim() || null,
        items,
      },
      {
        onSuccess: (data) => {
          toast.success(`Selo registrado em ${data.companies} empresas.`)
          onClose()
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function toggle(companyId: number) {
    setChosen((previous) => {
      const next = new Set(previous)
      if (next.has(companyId)) next.delete(companyId)
      else next.add(companyId)
      return next
    })
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Importar lista GPTW</DialogTitle>
          <DialogDescription>
            Cole o texto ou o link da lista de premiadas. As empresas da lista que já estão cadastradas recebem o selo,
            confirmado por você.
          </DialogDescription>
        </DialogHeader>
        <form id="gptw-import" className="space-y-4" onSubmit={read}>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="gptw-level">Selo</Label>
              <Select value={level} onValueChange={(value) => setLevel(value as Level)}>
                <SelectTrigger id="gptw-level" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(levels).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="gptw-year">Ano</Label>
              <Input id="gptw-year" type="number" min={2000} max={2100} value={year} onChange={(event) => setYear(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="gptw-ranking">Nome do ranking</Label>
              <Input
                id="gptw-ranking"
                placeholder="GPTW TI 2026"
                value={rankingName}
                onChange={(event) => setRankingName(event.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="gptw-url">Link da lista</Label>
            <Input id="gptw-url" placeholder="https://" value={url} onChange={(event) => setUrl(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="gptw-text">Ou o texto da lista</Label>
            <Textarea
              id="gptw-text"
              rows={5}
              placeholder={'1. Empresa A\n2. Empresa B'}
              value={text}
              onChange={(event) => setText(event.target.value)}
            />
          </div>
          <Button type="submit" variant="outline" disabled={preview.isPending || (!text.trim() && !url.trim())}>
            Ler a lista
          </Button>
        </form>
        {result && (
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground">
              {result.items.length} nomes na lista, {result.matched} com empresa cadastrada.
            </p>
            <ul className="max-h-72 space-y-1 overflow-y-auto rounded-md border p-2" aria-label="Prévia da importação">
              {result.items.map((item, index) => (
                <li key={`${item.extractedName}-${index}`} className="flex items-center gap-2 text-sm">
                  {item.companyId != null ? (
                    <input
                      type="checkbox"
                      className="size-4"
                      checked={chosen.has(item.companyId)}
                      onChange={() => toggle(item.companyId!)}
                      aria-label={`Marcar ${item.companyName}`}
                    />
                  ) : (
                    <span className="size-4" />
                  )}
                  <span className="min-w-0 flex-1 truncate">
                    {item.position ? `${item.position}. ` : ''}
                    {item.extractedName}
                    {item.companyName && item.match === 'SIMILAR' && (
                      <span className="text-muted-foreground"> → {item.companyName}</span>
                    )}
                  </span>
                  <Badge variant={item.match === 'EXATO' ? 'secondary' : 'outline'}>{matchLabels[item.match]}</Badge>
                </li>
              ))}
            </ul>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={save} disabled={!result || chosen.size === 0 || confirm.isPending}>
            Registrar selo em {chosen.size} {chosen.size === 1 ? 'empresa' : 'empresas'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
