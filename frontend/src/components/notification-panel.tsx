import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { api } from '@/lib/api'
import { useStore } from '@/store'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ResponseViewer } from '@/components/response-viewer'

export function NotificationPanel() {
  const { reservationId, setReservationId } = useStore()
  const [to, setTo] = useState('alexandru@esgi.fr')
  const [eventName, setEventName] = useState('Concert Metallica')
  const [quantity, setQuantity] = useState('2')

  const notify = useMutation({
    mutationFn: () =>
      api.notify({ to, reservationId, eventName, quantity: Number(quantity) }),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Mail className="size-4" /> Envoyer la confirmation
        </CardTitle>
        <CardDescription>
          <code>POST /notifications/ticket-issued</code> — notification-service → MailDev
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="to">Destinataire</Label>
            <Input id="to" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="evt">Événement</Label>
            <Input id="evt" value={eventName} onChange={(e) => setEventName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="notif-rid">Réservation</Label>
            <Input
              id="notif-rid"
              value={reservationId}
              onChange={(e) => setReservationId(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="notif-qty">Quantité</Label>
            <Input
              id="notif-qty"
              type="number"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </div>
        </div>
        <Button onClick={() => notify.mutate()} disabled={notify.isPending}>
          Envoyer l'email
        </Button>
        <p className="text-xs text-muted-foreground">
          Le mail est capté par MailDev (onglet « Emails »).
        </p>
        <ResponseViewer result={notify.data} pending={notify.isPending} />
      </CardContent>
    </Card>
  )
}
