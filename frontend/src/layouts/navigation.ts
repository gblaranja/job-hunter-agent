import {
  Building2,
  FileText,
  Inbox,
  KanbanSquare,
  Layers,
  Lightbulb,
  ListChecks,
  MailCheck,
  PlusCircle,
  Settings,
} from 'lucide-react'

/** Telas do painel, na ordem do menu: usadas pela sidebar e pela gaveta do celular. */
export const navigation = [
  { to: '/inbox', label: 'Caixa de vagas', Icon: Inbox },
  { to: '/board', label: 'Kanban', Icon: KanbanSquare },
  { to: '/applications', label: 'Minhas candidaturas', Icon: ListChecks },
  { to: '/responses', label: 'Retornos', Icon: MailCheck },
  { to: '/jobs/new', label: 'Nova vaga', Icon: PlusCircle },
  { to: '/resumes', label: 'Currículos', Icon: FileText },
  { to: '/companies', label: 'Empresas', Icon: Building2 },
  { to: '/niches', label: 'Nichos', Icon: Layers },
  { to: '/insights', label: 'Insights', Icon: Lightbulb },
  { to: '/settings', label: 'Configurações', Icon: Settings },
]
