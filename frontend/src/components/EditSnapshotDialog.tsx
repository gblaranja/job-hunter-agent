import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useSaveSnapshot, type AppliedJob } from '@/api/queries'
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
import { Textarea } from '@/components/ui/textarea'

type Props = { applied: AppliedJob; open: boolean; onOpenChange: (open: boolean) => void }

/** Cola ou corrige o link e a descrição salvos da vaga aplicada. */
export function EditSnapshotDialog({ applied, open, onOpenChange }: Props) {
  const save = useSaveSnapshot()
  const [url, setUrl] = useState(applied.snapshot.url ?? '')
  const [title, setTitle] = useState(applied.snapshot.title)
  const [company, setCompany] = useState(applied.snapshot.company)
  const [description, setDescription] = useState(applied.snapshot.descriptionMd ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate(
      { applicationId: applied.applicationId, url: url || null, title, company, descriptionMd: description || null },
      {
        onSuccess: () => {
          toast.success('Vaga salva atualizada.')
          onOpenChange(false)
        },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Editar a vaga salva</DialogTitle>
            <DialogDescription>
              A cópia guarda o que a empresa pediu no momento da aplicação. Corrija aqui se o alerta veio cortado.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="snapshot-title">Cargo</Label>
              <Input id="snapshot-title" value={title} onChange={(e) => setTitle(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="snapshot-company">Empresa</Label>
              <Input id="snapshot-company" value={company} onChange={(e) => setCompany(e.target.value)} required />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="snapshot-url">Link da vaga</Label>
            <Input
              id="snapshot-url"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="snapshot-description">Descrição (Markdown)</Label>
            <Textarea
              id="snapshot-description"
              className="min-h-64 font-mono text-xs"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
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
