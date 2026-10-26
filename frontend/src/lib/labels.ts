import type {
  ApplicationChannel,
  ContactRole,
  InteractionChannel,
  InteractionDirection,
  InteractionKind,
  ApplicationStatus,
  DismissReason,
  EmailClassification,
  FitBand,
  FeedbackReason,
  JobListItem,
  JobScope,
  LinkStatus,
  NextActionKind,
  NicheSource,
  RecognitionLevel,
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
  ENCERRADA: 'Vaga encerrada',
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
export const terminalStatuses = new Set<ApplicationStatus>(['OFERTA', 'RECUSADA', 'DESISTI', 'ENCERRADA'])

/** Etapas em que uma candidatura pode começar. */
export const startableStatuses: ApplicationStatus[] = ['INTERESSE', 'APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO']

/** Nota da candidatura criada ao marcar, antes de aplicar, que a vaga não aceita mais candidaturas. */
export const closedJobNote = 'A vaga já não aceitava candidaturas quando foi aberta.'

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
  ENCERRADA: 'bg-stone-500',
}

export const nextActionKindLabels: Record<NextActionKind, string> = {
  FOLLOW_UP: 'Follow-up',
  RESPONDER: 'Responder',
  ENTREVISTA: 'Entrevista',
  OUTRO: 'Próxima ação',
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

export const scopeLabels: Record<JobScope, string> = {
  NACIONAL: 'Nacional',
  INTERNACIONAL: 'Internacional',
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

export const dismissReasonLabels: Record<DismissReason, string> = {
  SEM_INTERESSE: 'Não tenho interesse',
  SEM_PERFIL_TECNICO: 'Não tenho o perfil técnico',
}

export const originLabels: Record<JobListItem['origin'], string> = {
  MANUAL: 'Manual',
  LINKEDIN_ALERTA: 'LinkedIn',
  GUPY_ALERTA: 'Gupy',
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  ASHBY: 'Ashby',
  REMOTIVE: 'Remotive',
  GITHUB: 'GitHub (backend-br)',
  ADZUNA: 'Adzuna',
}

/** Fonte da coleta: as origens das vagas, mais os alertas do Gmail (que juntam LinkedIn e Gupy). */
export function sourceLabel(source: string): string {
  if (source === 'GMAIL_ALERTAS') return 'Alertas no Gmail'
  return originLabels[source as JobListItem['origin']] ?? source
}

/** Faixas do tempo entre a vaga aparecer no agente e você aplicar. */
export const timeToApplyLabels: Record<string, string> = {
  ATE_1_DIA: 'Até 1 dia',
  '1_A_3_DIAS': '1 a 3 dias',
  '4_A_7_DIAS': '4 a 7 dias',
  MAIS_DE_7_DIAS: 'Mais de 7 dias',
  SEM_DADO: 'Sem dado (vaga manual)',
}

export const tailoringLabels: Record<string, string> = {
  ADAPTADO: 'Currículo adaptado à vaga',
  PADRAO: 'Currículo sem adaptação',
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

/** Senioridade das vagas, como a análise devolve. */
export const seniorityLabels: Record<string, string> = {
  ESTAGIO: 'Estágio',
  JUNIOR: 'Júnior',
  PLENO: 'Pleno',
  SENIOR: 'Sênior',
  ESPECIALISTA: 'Especialista',
  NAO_INFORMADO: 'Não informada',
}

export const companyKindLabels: Record<string, string> = {
  CONTRATANTE: 'Contratante',
  CONSULTORIA_TI: 'Consultoria de TI',
  RECRUTAMENTO: 'Recrutamento',
}

/** ATS com job board público consultado todo dia; os outros ficam só como anotação. */
export const atsLabels: Record<string, string> = {
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  ASHBY: 'Ashby',
  GUPY: 'Gupy',
  OUTRO: 'Outro',
}

export const diagnosisLabels: Record<string, string> = {
  CURRICULO: 'O currículo é o gargalo',
  FILTRO: 'O filtro de vagas é o gargalo',
  AMBOS: 'Currículo e filtro precisam de ajuste',
  EQUILIBRADO: 'Funil saudável',
  POUCOS_DADOS: 'Poucos dados ainda',
}

export const notificationTypeLabels: Record<string, string> = {
  VAGA_ADERENTE: 'Vaga aderente',
  EMPRESA_ALVO_VAGA: 'Empresa alvo',
  EMPRESA_VOLTOU: 'Voltou a contratar',
  VOLUME_CAIU: 'Volume caiu',
  RELATORIO_SEMANAL: 'Relatório semanal',
  CURRICULO_ADAPTADO: 'Currículo adaptado',
  FOLLOW_UP: 'Follow-up',
}

export const recognitionLevelLabels: Record<RecognitionLevel, string> = {
  RANKING_TI: 'Ranking GPTW TI',
  RANKING: 'Ranking GPTW',
  CERTIFICADA: 'Certificada GPTW',
  EXPIRADA: 'GPTW expirado',
  NAO_ENCONTRADA: 'Sem selo encontrado',
}

export const fitBandLabels: Record<FitBand, string> = {
  ALTO: 'Fit alto',
  MEDIO: 'Fit médio',
  BAIXO: 'Fit baixo',
}

export const nicheSourceLabels: Record<NicheSource, string> = {
  BCB: 'Banco Central',
  CNPJ: 'Receita (CNAE)',
  IA: 'Sugestão da IA',
  VAGA: 'Palavras-chave das vagas',
  MANUAL: 'Sua escolha',
}

export const contactRoleLabels: Record<ContactRole, string> = {
  RECRUTADOR: 'Recrutador(a)',
  HEADHUNTER: 'Headhunter',
  GESTOR: 'Gestor(a)',
  INDICACAO: 'Pode indicar',
  CONEXAO: 'Conexão do LinkedIn',
  OUTRO: 'Outro',
}

export const interactionChannelLabels: Record<InteractionChannel, string> = {
  LINKEDIN: 'LinkedIn',
  EMAIL: 'E-mail',
  WHATSAPP: 'WhatsApp',
  TELEFONE: 'Ligação',
  PRESENCIAL: 'Pessoalmente',
  OUTRO: 'Outro',
}

export const interactionDirectionLabels: Record<InteractionDirection, string> = {
  ENVIADA: 'Você mandou',
  RECEBIDA: 'Você recebeu',
}

export const interactionKindLabels: Record<InteractionKind, string> = {
  PEDIDO_INDICACAO: 'Pedido de indicação',
  FOLLOW_UP: 'Follow-up',
  AGRADECIMENTO: 'Agradecimento',
  RESPOSTA_RECRUTADOR: 'Resposta a recrutador',
  ABORDAGEM: 'Abordagem de recrutador',
  CONVERSA: 'Conversa',
  OUTRO: 'Outro',
}
