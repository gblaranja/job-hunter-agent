import { cn } from '@/lib/utils'
import { Check, X } from 'lucide-react'

/** Skills da vaga: verde quando o currículo tem, vermelho quando é lacuna, neutro quando ainda não há comparação. */
export function SkillChips({
  skills,
  owned = [],
  gaps = [],
  limit,
}: {
  skills: string[]
  owned?: string[]
  gaps?: string[]
  limit?: number
}) {
  const ownedSet = new Set(owned.map((skill) => skill.toLowerCase()))
  const gapSet = new Set(gaps.map((skill) => skill.toLowerCase()))
  const visible = limit ? skills.slice(0, limit) : skills
  return (
    <ul className="flex flex-wrap gap-1.5" aria-label="Skills">
      {visible.map((skill) => {
        const has = ownedSet.has(skill.toLowerCase())
        const missing = gapSet.has(skill.toLowerCase())
        return (
          <li
            key={skill}
            className={cn(
              'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs',
              has && 'border-success/30 bg-success/10 text-success',
              missing && 'border-destructive/30 bg-destructive/10 text-destructive',
              !has && !missing && 'text-muted-foreground',
            )}
          >
            {has && <Check className="size-3" aria-label="você tem" />}
            {missing && <X className="size-3" aria-label="lacuna" />}
            {skill}
          </li>
        )
      })}
      {limit && skills.length > limit && <li className="text-xs text-muted-foreground">+{skills.length - limit}</li>}
    </ul>
  )
}
