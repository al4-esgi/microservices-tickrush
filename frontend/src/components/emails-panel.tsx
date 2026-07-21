import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Inbox, RefreshCw, Trash2 } from 'lucide-react'
import { api } from '@/lib/api'
import type { Email } from '@/lib/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function EmailsPanel() {
  const qc = useQueryClient()
  const { data, isError, isFetching, refetch } = useQuery({
    queryKey: ['emails'],
    queryFn: api.emails,
    refetchInterval: 4000,
    retry: false,
  })
  const clear = useMutation({
    mutationFn: () => api.clearEmails(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['emails'] }),
  })

  const emails: Email[] = data ?? []

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <Inbox className="size-4" /> Boîte MailDev
          </CardTitle>
          <CardDescription>
            Emails captés (rafraîchi toutes les 4 s) — {emails.length} message(s)
          </CardDescription>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className="size-3.5" /> Actualiser
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => clear.mutate()}
            disabled={clear.isPending || emails.length === 0}
          >
            <Trash2 className="size-3.5" /> Vider
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {isError && (
          <p className="rounded-md bg-amber-500/10 p-3 text-sm text-amber-400">
            MailDev injoignable. Lancez le port-forward : <code>task forward</code> (ou{' '}
            <code>task forward:maildev</code>) puis actualisez.
          </p>
        )}
        {!isError && emails.length === 0 && (
          <p className="text-sm text-muted-foreground">
            Aucun email pour l'instant. Envoyez une confirmation depuis l'onglet
            « Notification » ou lancez le scénario complet.
          </p>
        )}
        <div className="space-y-2">
          {emails
            .slice()
            .reverse()
            .map((mail) => (
              <div key={mail.id} className="rounded-lg border p-3">
                <div className="flex items-baseline justify-between gap-2">
                  <p className="font-medium">{mail.subject}</p>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {new Date(mail.date).toLocaleTimeString('fr-FR')}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">
                  {mail.from?.[0]?.address} → {mail.to?.[0]?.address}
                </p>
                {mail.text && (
                  <pre className="mt-2 whitespace-pre-wrap rounded bg-muted p-2 text-xs">
                    {mail.text.trim()}
                  </pre>
                )}
              </div>
            ))}
        </div>
      </CardContent>
    </Card>
  )
}
