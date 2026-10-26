import { cn } from '@/lib/utils'
import { Check, Equal, X } from 'lucide-react'

/**
 * Skills da vaga: verde quando o currículo tem, âmbar quando tem uma equivalente (vale metade), vermelho quando é
 * lacuna, neutro quando ainda não há comparação.
 */
export function SkillChips({
  skills,
  owned = [],
  gaps = [],
  partial = [],
  limit,
}: {
  skills: string[]
  owned?: string[]
  gaps?: string[]
  /** Skill pedida e a equivalente do currículo que a cobre em parte. */
  partial?: { skill: string; via: string }[]
  limit?: number
}) {
  const ownedSet = new Set(owned.map((skill) => skill.toLowerCase()))
  const gapSet = new Set(gaps.map((skill) => skill.toLowerCase()))
  const partialVia = new Map(partial.map((match) => [match.skill.toLowerCase(), match.via]))
  const visible = limit ? skills.slice(0, limit) : skills
  return (
    <ul className="flex flex-wrap gap-1.5" aria-label="Skills">
      {visible.map((skill) => {
        const has = ownedSet.has(skill.toLowerCase())
        const via = has ? undefined : partialVia.get(skill.toLowerCase())
        const missing = !via && gapSet.has(skill.toLowerCase())
        return (
          <li
            key={skill}
            title={via ? `Em parte: o currículo tem ${via}` : undefined}
            className={cn(
              'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs',
              has && 'border-success/30 bg-success/10 text-success',
              via && 'border-warning/40 bg-warning/15 text-amber-700 dark:text-warning',
              missing && 'border-destructive/30 bg-destructive/10 text-destructive',
              !has && !via && !missing && 'text-muted-foreground',
            )}
          >
            {has && <Check className="size-3" aria-label="você tem" />}
            {via && <Equal className="size-3" aria-label="em parte" />}
            {missing && <X className="size-3" aria-label="lacuna" />}
            {skill}
            {via && <span className="opacity-80">≈ {via}</span>}
          </li>
        )
      })}
      {limit && skills.length > limit && <li className="text-xs text-muted-foreground">+{skills.length - limit}</li>}
    </ul>
  )
}
