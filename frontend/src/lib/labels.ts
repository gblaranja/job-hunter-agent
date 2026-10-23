import type { ApplicationChannel, ApplicationStatus, LinkStatus, WorkModel } from '@/api/queries'

export const statusLabels: Record<ApplicationStatus, string> = {
  NOVA: 'Nova',
  INTERESSE: 'Interesse',
  APLICADA: 'Aplicada',
  TRIAGEM: 'Triagem',
  ENTREVISTA: 'Entrevista',
  TESTE_TECNICO: 'Teste técnico',
  OFERTA: 'Oferta',
  RECUSADA: 'Recusada',
  DESISTI: 'Desisti',
}

/** Etapas em que a candidatura já precisa ter o currículo enviado registrado. */
export const statusesRequiringResume = new Set<ApplicationStatus>([
  'APLICADA',
  'TRIAGEM',
  'ENTREVISTA',
  'TESTE_TECNICO',
  'OFERTA',
  'RECUSADA',
])

/** Etapas que encerram a candidatura; sair delas exige uma nota. */
export const terminalStatuses = new Set<ApplicationStatus>(['OFERTA', 'RECUSADA', 'DESISTI'])

/** Etapas em que uma candidatura pode começar. */
export const startableStatuses: ApplicationStatus[] = ['INTERESSE', 'APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO']

/** Cor de destaque de cada coluna do kanban. */
export const statusAccent: Record<ApplicationStatus, string> = {
  NOVA: 'bg-slate-400',
  INTERESSE: 'bg-sky-500',
  APLICADA: 'bg-indigo-500',
  TRIAGEM: 'bg-violet-500',
  ENTREVISTA: 'bg-amber-500',
  TESTE_TECNICO: 'bg-orange-500',
  OFERTA: 'bg-emerald-500',
  RECUSADA: 'bg-rose-500',
  DESISTI: 'bg-zinc-400',
}

export const channelLabels: Record<ApplicationChannel, string> = {
  EASY_APPLY: 'Easy Apply',
  SITE_EMPRESA: 'Site da empresa',
  INDICACAO: 'Indicação',
  EMAIL: 'E-mail',
  OUTRO: 'Outro',
}

export const workModelLabels: Record<WorkModel, string> = {
  REMOTO: 'Remoto',
  HIBRIDO: 'Híbrido',
  PRESENCIAL: 'Presencial',
  NAO_INFORMADO: 'Não informado',
}

export const linkStatusLabels: Record<NonNullable<LinkStatus>, string> = {
  ATIVO: 'Ativo',
  REMOVIDO: 'Removido',
  NAO_VERIFICADO: 'Não verificado',
}
