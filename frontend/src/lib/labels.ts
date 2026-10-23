import type {
  ApplicationChannel,
  ApplicationStatus,
  EmailClassification,
  FeedbackReason,
  JobListItem,
  LinkStatus,
  ResponseChannel,
  ResponseType,
  WatchStatus,
  WorkModel,
} from '@/api/queries'

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

export const feedbackReasonLabels: Record<FeedbackReason, string> = {
  SALARIO: 'Salário',
  STACK: 'Stack',
  SENIORIDADE: 'Senioridade',
  MODELO_TRABALHO: 'Modelo de trabalho',
  EMPRESA: 'Empresa',
  LOCALIZACAO: 'Localização',
}

export const originLabels: Record<JobListItem['origin'], string> = {
  MANUAL: 'Manual',
  LINKEDIN_ALERTA: 'LinkedIn',
  GUPY_ALERTA: 'Gupy',
}

export const watchStatusLabels: Record<WatchStatus, string> = {
  DESCOBERTA: 'Descoberta',
  MONITORADA: 'Monitorada',
  ALVO: 'Alvo',
  IGNORADA: 'Ignorada',
}

/** A localidade só repete o modelo de trabalho ("Remoto" com modelo REMOTO); nesse caso basta um selo. */
export function sameAsWorkModel(location: string, workModel: WorkModel): boolean {
  const normalize = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase()
  return normalize(location) === normalize(workModelLabels[workModel])
}

export const classificationLabels: Record<EmailClassification, string> = {
  ALERTA_VAGA: 'Alerta de vaga',
  CONVITE_ENTREVISTA: 'Convite para entrevista',
  TESTE_TECNICO: 'Teste técnico',
  PEDIDO_INFORMACAO: 'Pedido de informação',
  OFERTA: 'Oferta',
  RECUSA: 'Recusa',
  OUTRO: 'Outro',
  IRRELEVANTE: 'Irrelevante',
}

export const responseTypeLabels: Record<ResponseType, string> = {
  CONVITE_ENTREVISTA: 'Convite para entrevista',
  TESTE_TECNICO: 'Teste técnico',
  PEDIDO_INFORMACAO: 'Pedido de informação',
  OFERTA: 'Oferta',
  RECUSA: 'Recusa',
  OUTRO: 'Outro retorno',
}

export const responseChannelLabels: Record<ResponseChannel, string> = {
  EMAIL: 'E-mail',
  WHATSAPP: 'WhatsApp',
  LINKEDIN: 'LinkedIn',
  TELEFONE: 'Ligação',
  OUTRO: 'Outro',
}
