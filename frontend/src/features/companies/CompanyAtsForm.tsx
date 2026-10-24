import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { useCheckCompanyAts, useUpdateCompany, type CompanyAtsCheck, type CompanyItem } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { atsLabels, atsSlugStatusLabels } from '@/lib/labels'

const NO_ATS = 'NENHUM'

/**
 * Página de carreira, ATS e slug do job board: com eles, as empresas monitoradas e alvo são consultadas todo dia. O
 * ATS e o slug podem ser detectados pelo link da página de carreira.
 */
export function CompanyAtsForm({ company }: { company: CompanyItem }) {
  const update = useUpdateCompany()
  const check = useCheckCompanyAts()
  const [careersUrl, setCareersUrl] = useState(company.careersUrl ?? '')
  const [atsType, setAtsType] = useState(company.atsType ?? NO_ATS)
  const [atsSlug, setAtsSlug] = useState(company.atsSlug ?? '')
  const [website, setWebsite] = useState(company.website ?? '')
  const [notes, setNotes] = useState(company.notes ?? '')
  const [checked, setChecked] = useState<CompanyAtsCheck | null>(null)

  function detect() {
    check.mutate(
      { careersUrl: careersUrl.trim() || null },
      {
        onSuccess: (result) => {
          setAtsType(result.atsType)
          setAtsSlug(result.atsSlug)
          setChecked(result)
        },
        onError: (error) => {
          setChecked(null)
          toast.error(error.message)
        },
      },
    )
  }

  function save(event: FormEvent) {
    event.preventDefault()
    update.mutate(
      {
        id: company.id,
        watchStatus: company.watchStatus,
        atsType: atsType === NO_ATS ? null : atsType,
        atsSlug: atsSlug.trim() || null,
        website: website.trim() || null,
        notes: notes.trim() || null,
        careersUrl: careersUrl.trim(),
      },
      { onSuccess: () => toast.success('Empresa atualizada.'), onError: (error) => toast.error(error.message) },
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Job board e notas</CardTitle>
        <CardDescription>
          Greenhouse, Lever, Ashby, SmartRecruiters, Workable, Teamtailor e Recruitee são consultados todo dia para empresas
          monitoradas ou alvo. Cole o link da página de carreira e use "Detectar" (ou salve com o ATS em branco); o slug é o
          trecho da URL do job board (jobs.lever.co/<strong>slug</strong>).
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={save}>
          <div className="space-y-2">
            <Label htmlFor="careers-url">Página de carreira</Label>
            <div className="flex gap-2">
              <Input
                id="careers-url"
                value={careersUrl}
                onChange={(event) => {
                  setCareersUrl(event.target.value)
                  setChecked(null)
                }}
                placeholder="https://jobs.lever.co/empresa"
              />
              <Button type="button" variant="outline" onClick={detect} disabled={!careersUrl.trim() || check.isPending}>
                Detectar
              </Button>
            </div>
            {checked && (
              <p className="text-sm text-muted-foreground" role="status">
                {atsLabels[checked.atsType] ?? checked.atsType} ({checked.atsSlug}):{' '}
                {checked.supported ? atsSlugStatusLabels[checked.status] : 'sem coleta automática'}
              </p>
            )}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="ats-type">ATS</Label>
              <Select value={atsType} onValueChange={setAtsType}>
                <SelectTrigger id="ats-type" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_ATS}>Não informado</SelectItem>
                  {Object.entries(atsLabels).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="ats-slug">Slug</Label>
              <Input id="ats-slug" value={atsSlug} onChange={(event) => setAtsSlug(event.target.value)} placeholder="nubank" />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="website">Site</Label>
            <Input id="website" value={website} onChange={(event) => setWebsite(event.target.value)} placeholder="https://" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="notes">Notas</Label>
            <Textarea id="notes" value={notes} onChange={(event) => setNotes(event.target.value)} rows={3} />
          </div>
          <Button type="submit" disabled={update.isPending}>
            Salvar
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
