import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Ticket, Search } from 'lucide-react'
import { api } from '@/lib/api'
import { SEED_EVENTS } from '@/lib/constants'
import { useStore } from '@/store'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ResponseViewer } from '@/components/response-viewer'

export function ReservationPanel() {
  const { reservationId, setReservationId } = useStore()
  const [eventId, setEventId] = useState(SEED_EVENTS[0].id)
  const [customerRef, setCustomerRef] = useState('alex@esgi.fr')
  const [quantity, setQuantity] = useState('1')

  const event = useMutation({ mutationFn: () => api.getEvent(eventId) })
  const create = useMutation({
    mutationFn: () =>
      api.createReservation({ eventId, customerRef, quantity: Number(quantity) }),
    onSuccess: (r) => {
      const id = (r.data as { id?: string })?.id
      if (id) setReservationId(id)
    },
  })
  const get = useMutation({ mutationFn: () => api.getReservation(reservationId) })
  const payStatus = useMutation({ mutationFn: () => api.paymentStatus(reservationId) })

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Search className="size-4" /> Consulter le stock
          </CardTitle>
          <CardDescription>
            <code>GET /events/{'{id}'}</code> — booking-service
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1.5">
            <Label>Événement</Label>
            <div className="flex flex-wrap gap-1.5">
              {SEED_EVENTS.map((e) => (
                <Button
                  key={e.id}
                  size="sm"
                  variant={eventId === e.id ? 'default' : 'outline'}
                  onClick={() => setEventId(e.id)}
                >
                  {e.label}
                </Button>
              ))}
            </div>
          </div>
          <Button onClick={() => event.mutate()} disabled={event.isPending}>
            Voir le stock
          </Button>
          <ResponseViewer result={event.data} pending={event.isPending} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Ticket className="size-4" /> Réserver des places
          </CardTitle>
          <CardDescription>
            <code>POST /reservations</code> — décrément sûr + TTL
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="cust">Client</Label>
              <Input
                id="cust"
                value={customerRef}
                onChange={(e) => setCustomerRef(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="qty">Quantité</Label>
              <Input
                id="qty"
                type="number"
                min={1}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
            </div>
          </div>
          <Button onClick={() => create.mutate()} disabled={create.isPending}>
            Réserver
          </Button>
          <ResponseViewer result={create.data} pending={create.isPending} />

          <div className="space-y-1.5 border-t pt-3">
            <Label htmlFor="rid">Réservation courante</Label>
            <Input
              id="rid"
              placeholder="id de réservation"
              value={reservationId}
              onChange={(e) => setReservationId(e.target.value)}
            />
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => get.mutate()}
                disabled={!reservationId || get.isPending}
              >
                Suivre
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => payStatus.mutate()}
                disabled={!reservationId || payStatus.isPending}
              >
                Statut paiement (circuit breaker)
              </Button>
            </div>
            <ResponseViewer
              result={get.data ?? payStatus.data}
              pending={get.isPending || payStatus.isPending}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
