import { useRef, useState } from 'react'
import { NavLink } from 'react-router'
import { Button } from '@/components/ui/button'
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { cn } from '@/lib/utils'
import { LogOut, Menu, X } from 'lucide-react'
import { navigation } from './navigation'

/** Menu do painel no celular: um botão que abre a gaveta com as telas, o usuário e o "Sair". */
export function MobileNav({ username, onLogout, className }: { username: string; onLogout: () => void; className?: string }) {
  const [open, setOpen] = useState(false)
  // Ao navegar, o foco fica com o conteúdo da tela nova (AppLayout); só Esc e "Fechar" devolvem o foco ao botão.
  const navigated = useRef(false)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon-sm" className={className} aria-label="Abrir menu">
          <Menu />
        </Button>
      </SheetTrigger>
      <SheetContent
        side="left"
        showCloseButton={false}
        className="w-72 gap-0 bg-sidebar text-sidebar-foreground"
        onCloseAutoFocus={(event) => {
          if (navigated.current) event.preventDefault()
          navigated.current = false
        }}
      >
        <SheetHeader className="flex-row items-center justify-between">
          <SheetTitle className="text-sidebar-foreground">Menu</SheetTitle>
          <SheetDescription className="sr-only">Telas do painel</SheetDescription>
          <SheetClose asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              className="text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
              aria-label="Fechar"
            >
              <X />
            </Button>
          </SheetClose>
        </SheetHeader>
        <nav className="flex flex-col gap-1 overflow-y-auto px-3" aria-label="Principal">
          {navigation.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => {
                navigated.current = true
                setOpen(false)
              }}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-2.5 text-sm transition-colors hover:bg-sidebar-accent',
                  isActive && 'bg-sidebar-accent font-medium text-sidebar-accent-foreground',
                )
              }
            >
              <Icon className="size-4" aria-hidden />
              {label}
            </NavLink>
          ))}
        </nav>
        <SheetFooter className="flex-row items-center justify-between border-t border-sidebar-border text-sm">
          <span className="truncate">{username}</span>
          <Button
            variant="ghost"
            size="icon-sm"
            className="text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
            onClick={onLogout}
            aria-label="Sair"
          >
            <LogOut />
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}
