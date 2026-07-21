import { useQuery } from '@tanstack/react-query'
import { Radio, Server } from 'lucide-react'
import { api } from '@/lib/api'
import { SEED_EVENTS, SERVICES } from '@/lib/constants'
import { Badge } from '@/components/ui/badge'

export function Header() {
  const ping = useQuery({
    queryKey: ['gateway'],
    queryFn: () => api.getEvent(SEED_EVENTS[0].id),
    refetchInterval: 5000,
    retry: false,
  })
  const online = ping.data?.ok ?? false

  return (
    <header className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">TickRush — Console de démo</h1>
          <p className="text-sm text-muted-foreground">
            Billetterie microservices · appels via le gateway Traefik (<code>/api → :8081</code>)
          </p>
        </div>
        <Badge variant={online ? 'success' : 'destructive'} className="gap-1.5">
          <Radio className="size-3" />
          {online ? 'Gateway en ligne' : 'Gateway hors ligne'}
        </Badge>
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        {SERVICES.map((s) => (
          <div key={s.name} className="flex items-center gap-2 rounded-lg border bg-card p-3">
            <Server className="size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{s.name}</p>
              <p className="truncate text-xs text-muted-foreground">{s.lang}</p>
            </div>
          </div>
        ))}
      </div>
    </header>
  )
}
