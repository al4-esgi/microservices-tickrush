import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CreditCard } from 'lucide-react'
import { api } from '@/lib/api'
import { useStore } from '@/store'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ResponseViewer } from '@/components/response-viewer'

export function PaymentPanel() {
  const { reservationId, setReservationId } = useStore()
  const [amount, setAmount] = useState('42')

  const pay = useMutation({
    mutationFn: () =>
      api.createPayment({ reservationId, amount: Number(amount) }),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <CreditCard className="size-4" /> Payer une réservation
        </CardTitle>
        <CardDescription>
          <code>POST /payments</code> — payment-service (idempotent, persisté)
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="pay-rid">Réservation</Label>
            <Input
              id="pay-rid"
              placeholder="id de réservation"
              value={reservationId}
              onChange={(e) => setReservationId(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="amount">Montant (€)</Label>
            <Input
              id="amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
        </div>
        <Button onClick={() => pay.mutate()} disabled={!reservationId || pay.isPending}>
          Payer
        </Button>
        <p className="text-xs text-muted-foreground">
          Rejouer le même paiement renvoie <code>200</code> (idempotence), pas un doublon.
        </p>
        <ResponseViewer result={pay.data} pending={pay.isPending} />
      </CardContent>
    </Card>
  )
}
