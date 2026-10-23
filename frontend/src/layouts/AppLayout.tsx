import { useEffect, useRef } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router'
import { useCurrentUser, useLogout } from '@/api/queries'
import { Logo } from '@/components/brand/Logo'
import { NotificationBell } from '@/components/NotificationBell'
import { LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { LogOut } from 'lucide-react'
import { MobileNav } from './MobileNav'
import { navigation } from './navigation'

/** Casca do backoffice; sem sessão, manda para o login e volta para a mesma tela depois. */
export function AppLayout() {
  const me = useCurrentUser()
  const logout = useLogout()
  const location = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  const previousPath = useRef(location.pathname)

  // Ao trocar de tela, o foco vai para o conteúdo (leitores de tela anunciam a tela nova); no primeiro carregamento, não.
  useEffect(() => {
    if (previousPath.current === location.pathname) return
    previousPath.current = location.pathname
    mainRef.current?.focus()
  }, [location.pathname])

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
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-background focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-foreground focus:shadow-lg"
        onClick={(event) => {
          event.preventDefault()
          mainRef.current?.focus()
        }}
      >
        Pular para o conteúdo
      </a>
      {/* No celular a sidebar vira uma barra superior, e as telas ficam na gaveta do MobileNav. */}
      <aside className="sticky top-0 z-40 flex shrink-0 flex-col gap-1 bg-sidebar px-3 py-2 text-sidebar-foreground md:static md:w-60 md:p-4">
        <div className="flex items-center gap-2 px-2 py-1 md:mb-6">
          <Link to="/" aria-label="Página inicial">
            <Logo compact />
          </Link>
          <NotificationBell className="ml-auto text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground" />
          <MobileNav
            username={me.data.username}
            onLogout={() => logout.mutate()}
            className="text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground md:hidden"
          />
        </div>
        <nav className="hidden gap-1 md:flex md:flex-col" aria-label="Principal">
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
      <main id="conteudo" ref={mainRef} tabIndex={-1} className="min-w-0 flex-1 p-4 outline-none md:p-8">
        <Outlet />
      </main>
    </div>
  )
}
