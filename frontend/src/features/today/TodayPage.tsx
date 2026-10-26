import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useToday, type TodayAgenda } from '@/api/queries'
import { LegitimacyBadge } from '@/components/LegitimacyBadge'
import { PageHeader } from '@/components/PageHeader'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatAge, formatDate, formatDateTime } from '@/lib/format'
import { nextActionKindLabel } from '@/lib/labels'
import type { ApplicationStatus } from '@/api/queries'
import { CalendarClock, FileText, Mail, Send, Target } from 'lucide-react'

/** Fila do dia: tudo o que pede ação hoje, na ordem em que vale a pena fazer. */
export function TodayPage() {
  const today = useToday()

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Hoje"
        description="Vagas aderentes e frescas para aplicar, follow-ups vencidos, e-mails para revisar, entrevistas da semana e a meta semanal."
      />
      {today.isLoading && <LoadingBlock />}
      {today.error && <ErrorBlock error={today.error} />}
      {today.data && <TodayView agenda={today.data} />}
    </div>
  )
}

function TodayView({ agenda }: { agenda: TodayAgenda }) {
  const goal = agenda.weeklyGoal
  const progress = goal.goal > 0 ? Math.min(100, Math.round((goal.aligned / goal.goal) * 100)) : 0

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Target className="size-4" aria-hidden /> Meta da semana
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <p className="text-sm">
            <strong>{goal.aligned}</strong> de {goal.goal} candidaturas aderentes desde {formatDate(goal.weekStart)}
            <span className="text-muted-foreground"> · {goal.applied} enviadas no total</span>
          </p>
          <div
            className="h-2 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-label="Progresso da meta semanal"
            aria-valuenow={goal.aligned}
            aria-valuemin={0}
            aria-valuemax={goal.goal}
          >
            <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
          </div>
        </CardContent>
      </Card>

      <Section title="Para aplicar hoje" icon={<Send className="size-4" aria-hidden />} empty="Nenhuma vaga com score a partir de 70 nas últimas 72 h.">
        {agenda.jobs.map((job) => (
          <li key={job.id} className="flex flex-wrap items-center gap-3">
            <ScoreBadge score={job.matchScore} />
            <div className="min-w-0 flex-1">
              <Link to={`/jobs/${job.id}`} className="font-medium hover:underline">
                {job.title}
              </Link>
              <p className="text-sm text-muted-foreground">
                {job.company} · {formatAge(job.firstSeenAt)}
              </p>
            </div>
            <LegitimacyBadge level={job.legitimacy} reasons={[]} />
          </li>
        ))}
      </Section>

      <Section title="Follow-ups e próximas ações" icon={<CalendarClock className="size-4" aria-hidden />} empty="Nada vencido.">
        {agenda.followUps.map((item) => (
          <li key={item.applicationId} className="flex flex-wrap items-center gap-3">
            <Badge variant="outline">{nextActionKindLabel(item.nextActionKind)}</Badge>
            <div className="min-w-0 flex-1">
              <Link to={`/jobs/${item.jobId}`} className="font-medium hover:underline">
                {item.company}
              </Link>
              <p className="text-sm text-muted-foreground">
                {item.title} · venceu em {formatDate(item.nextActionAt)}
                {item.followUpsSent > 0 && ` · ${item.followUpsSent} follow-up(s) enviados`}
              </p>
            </div>
            <StatusBadge status={item.status as ApplicationStatus} />
          </li>
        ))}
      </Section>

      <Section title="E-mails para revisar" icon={<Mail className="size-4" aria-hidden />} empty="Nenhum e-mail esperando revisão.">
        {agenda.emails.map((email) => (
          <li key={email.id} className="flex flex-wrap items-center gap-x-3">
            <Link to="/responses" className="font-medium hover:underline">
              {email.subject ?? '(sem assunto)'}
            </Link>
            <span className="text-sm text-muted-foreground">
              {email.sender} · {formatDateTime(email.receivedAt)}
            </span>
          </li>
        ))}
      </Section>

      <Section title="Entrevistas e testes" icon={<CalendarClock className="size-4" aria-hidden />} empty="Nenhuma entrevista ou teste em andamento.">
        {agenda.interviews.map((item) => (
          <li key={item.applicationId} className="flex flex-wrap items-center gap-3">
            <StatusBadge status={item.status as ApplicationStatus} />
            <Link to={`/jobs/${item.jobId}`} className="font-medium hover:underline">
              {item.company}
            </Link>
            <span className="text-sm text-muted-foreground">
              {item.title}
              {item.nextActionAt && ` · ${formatDateTime(item.nextActionAt)}`}
            </span>
          </li>
        ))}
      </Section>

      <Section title="Currículos adaptados prontos" icon={<FileText className="size-4" aria-hidden />} empty="Nenhum currículo adaptado esperando candidatura.">
        {agenda.tailoredResumes.map((item) => (
          <li key={item.id} className="flex flex-wrap items-center gap-x-3">
            <Link to={`/jobs/${item.jobId}`} className="font-medium hover:underline">
              {item.title}
            </Link>
            <span className="text-sm text-muted-foreground">{item.company}</span>
          </li>
        ))}
      </Section>
    </>
  )
}

function Section({ title, icon, empty, children }: { title: string; icon: ReactNode; empty: string; children: ReactNode[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          {icon} {title}
          {children.length > 0 && <Badge variant="secondary">{children.length}</Badge>}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {children.length === 0 ? <p className="text-sm text-muted-foreground">{empty}</p> : <ul className="space-y-3">{children}</ul>}
      </CardContent>
    </Card>
  )
}
