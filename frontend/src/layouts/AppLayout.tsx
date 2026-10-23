import { Navigate, NavLink, Outlet, useLocation } from 'react-router'
import { useCurrentUser, useLogout } from '@/api/queries'
import { NotificationBell } from '@/components/NotificationBell'
import { LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import {
  BriefcaseBusiness,
  Building2,
  FileText,
  Inbox,
  KanbanSquare,
  Lightbulb,
  ListChecks,
  LogOut,
  MailCheck,
  PlusCircle,
  Settings,
} from 'lucide-react'

const navigation = [
  { to: '/inbox', label: 'Caixa de vagas', Icon: Inbox },
  { to: '/board', label: 'Kanban', Icon: KanbanSquare },
  { to: '/applications', label: 'Minhas candidaturas', Icon: ListChecks },
  { to: '/responses', label: 'Retornos', Icon: MailCheck },
  { to: '/jobs/new', label: 'Nova vaga', Icon: PlusCircle },
  { to: '/resumes', label: 'Currículos', Icon: FileText },
  { to: '/companies', label: 'Empresas', Icon: Building2 },
  { to: '/insights', label: 'Insights', Icon: Lightbulb },
  { to: '/settings', label: 'Configurações', Icon: Settings },
]

/** Casca do backoffice; sem sessão, manda para o login e volta para a mesma tela depois. */
export function AppLayout() {
  const me = useCurrentUser()
  const logout = useLogout()
  const location = useLocation()

  if (me.isLoading) {
    return (
      <div className="p-8">
        <LoadingBlock />
      </div>
    )
  }
  if (!me.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />

  return (
    <div className="flex min-h-svh flex-col md:flex-row">
      <aside className="flex shrink-0 flex-col gap-1 bg-sidebar p-3 text-sidebar-foreground md:w-60 md:p-4">
        <div className="mb-2 flex items-center gap-2 px-2 py-1 md:mb-6">
          <BriefcaseBusiness className="size-5" aria-hidden />
          <span className="font-semibold tracking-tight">Job Hunter</span>
          <NotificationBell className="ml-auto text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground" />
          <Button
            variant="ghost"
            size="icon-sm"
            className="text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground md:hidden"
            onClick={() => logout.mutate()}
            aria-label="Sair"
          >
            <LogOut />
          </Button>
        </div>
        <nav className="flex gap-1 overflow-x-auto md:flex-col" aria-label="Principal">
          {navigation.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 whitespace-nowrap rounded-md px-3 py-2 text-sm transition-colors hover:bg-sidebar-accent',
                  isActive && 'bg-sidebar-accent font-medium text-sidebar-accent-foreground',
                )
              }
            >
              <Icon className="size-4" aria-hidden />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto hidden items-center justify-between border-t border-sidebar-border px-2 pt-4 text-sm md:flex">
          <span className="truncate">{me.data.username}</span>
          <Button
            variant="ghost"
            size="icon-sm"
            className="text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
            onClick={() => logout.mutate()}
            aria-label="Sair"
          >
            <LogOut />
          </Button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 p-4 md:p-8">
        <Outlet />
      </main>
    </div>
  )
}
