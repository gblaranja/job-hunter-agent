import type { ApplicationStatus } from '@/api/queries'
import { statusesRequiringResume, terminalStatuses } from '@/lib/labels'

export type MovePlan =
  | { kind: 'none' }
  | { kind: 'move' }
  | { kind: 'confirm'; requireResume: boolean; requireNote: boolean }

/**
 * Decide o que acontece ao mover uma candidatura: segue direto, pede o currículo enviado (a partir de
 * Aplicada, se ainda não há um) e/ou pede uma nota (para reabrir uma candidatura encerrada). Espelha as
 * regras do backend para o usuário não esbarrar num erro 422.
 */
export function planMove(
  current: { status: ApplicationStatus; resumeVersionId?: number | null },
  target: ApplicationStatus,
): MovePlan {
  if (current.status === target || target === 'NOVA') return { kind: 'none' }
  const requireResume = statusesRequiringResume.has(target) && current.resumeVersionId == null
  const requireNote = terminalStatuses.has(current.status)
  return requireResume || requireNote ? { kind: 'confirm', requireResume, requireNote } : { kind: 'move' }
}
