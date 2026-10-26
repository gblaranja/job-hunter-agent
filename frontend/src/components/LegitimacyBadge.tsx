import { Badge } from '@/components/ui/badge'
import { legitimacyLabels } from '@/lib/labels'
import { ShieldAlert } from 'lucide-react'

/** Selo de legitimidade; não aparece para as vagas confiáveis. Os motivos ficam no title. */
export function LegitimacyBadge({ level, reasons }: { level?: string | null; reasons: string[] }) {
  if (!level || level === 'ALTA') return null
  const variant = level === 'MEDIA' ? 'outline' : 'destructive'
  return (
    <Badge variant={variant} title={reasons.join(' · ')}>
      <ShieldAlert aria-hidden /> {legitimacyLabels[level] ?? level}
    </Badge>
  )
}
