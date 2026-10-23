import { useState } from 'react'
import { useNavigate } from 'react-router'
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  type NotificationItem,
} from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatDateTime } from '@/lib/format'
import { notificationTypeLabels } from '@/lib/labels'
import { cn } from '@/lib/utils'
import { Bell } from 'lucide-react'

/** Sino com as notificações: vagas aderentes, alertas das empresas e o relatório semanal. */
export function NotificationBell({ className }: { className?: string }) {
  const notifications = useNotifications()
  const markRead = useMarkNotificationRead()
  const markAll = useMarkAllNotificationsRead()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const items = notifications.data ?? []
  const unread = items.filter((item) => !item.readAt).length

  function openItem(item: NotificationItem) {
    if (!item.readAt) markRead.mutate(item.id)
    setOpen(false)
    if (item.link) navigate(item.link)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          className={cn('relative', className)}
          aria-label={unread > 0 ? `Notificações (${unread} não lidas)` : 'Notificações'}
        >
          <Bell />
          {unread > 0 && (
            <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-white">
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80 p-0">
        <div className="flex items-center justify-between border-b px-3 py-2">
          <span className="text-sm font-medium">Notificações</span>
          {unread > 0 && (
            <Button variant="link" size="sm" className="h-auto px-0" onClick={() => markAll.mutate()}>
              Marcar todas como lidas
            </Button>
          )}
        </div>
        {items.length === 0 ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">Nada por aqui.</p>
        ) : (
          <ul className="max-h-96 overflow-y-auto">
            {items.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className="flex w-full flex-col items-start gap-0.5 border-b px-3 py-2 text-left text-sm last:border-0 hover:bg-muted"
                  onClick={() => openItem(item)}
                >
                  <span className="flex w-full items-center gap-2 text-xs text-muted-foreground">
                    {!item.readAt && <span className="size-2 rounded-full bg-primary" aria-label="Não lida" />}
                    {notificationTypeLabels[item.type] ?? item.type} · {formatDateTime(item.createdAt)}
                  </span>
                  <span className={cn(!item.readAt && 'font-medium')}>{item.title}</span>
                  {item.body && <span className="line-clamp-2 text-muted-foreground">{item.body}</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  )
}
