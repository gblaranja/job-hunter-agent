import { toast } from 'sonner'
import type { InsightReport } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Copy } from 'lucide-react'

type Recommendations = InsightReport['recommendations']

/** Textos prontos para colar nos perfis da Gupy e do LinkedIn, com o que mudou desde o relatório anterior. */
export function PlatformProfilesCard({ recommendations }: { recommendations: Recommendations }) {
  const { gupyProfile, linkedinProfile, profileChanges } = recommendations
  if (!gupyProfile && !linkedinProfile) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Perfis nas plataformas</CardTitle>
        <CardDescription>
          A Gupy lê os campos do perfil junto com o currículo, e recrutadores buscam no LinkedIn por palavra-chave.
          Confira antes de colar: os textos usam só o que o seu currículo mostra.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {profileChanges && profileChanges.length > 0 && (
          <ul className="list-disc space-y-0.5 pl-5 text-sm text-muted-foreground" aria-label="O que mudou">
            {profileChanges.map((change) => (
              <li key={change}>{change}</li>
            ))}
          </ul>
        )}
        {linkedinProfile && (
          <section className="space-y-3">
            <h3 className="text-sm font-medium">LinkedIn</h3>
            <CopyField label="Título (headline)" value={linkedinProfile.headline} />
            {linkedinProfile.about && <CopyField label="Sobre" value={linkedinProfile.about} />}
            {linkedinProfile.skills.length > 0 && (
              <CopyField label="Competências" value={linkedinProfile.skills.join(', ')} />
            )}
          </section>
        )}
        {gupyProfile && (
          <section className="space-y-3">
            <h3 className="text-sm font-medium">Gupy</h3>
            <CopyField label="Habilidades" value={gupyProfile.skills} />
            {gupyProfile.experiences.map((experience) => (
              <CopyField
                key={`${experience.role}-${experience.company}`}
                label={`${experience.role} · ${experience.company}`}
                value={experience.description}
              />
            ))}
          </section>
        )}
      </CardContent>
    </Card>
  )
}

function CopyField({ label, value }: { label: string; value: string }) {
  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
      toast.success(`${label}: copiado.`)
    } catch {
      toast.error('Não foi possível copiar.')
    }
  }

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</span>
        <Button variant="ghost" size="sm" onClick={copy} aria-label={`Copiar ${label}`}>
          <Copy /> Copiar
        </Button>
      </div>
      <p className="whitespace-pre-line rounded-md border bg-muted/30 p-2 text-sm">{value}</p>
    </div>
  )
}
