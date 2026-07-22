import { useState } from 'react'
import { CheckCircle2, PlayCircle, XCircle } from 'lucide-react'
import { api } from '@/lib/api'
import { SEED_EVENTS } from '@/lib/constants'
import { useStore } from '@/store-context'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

interface Step {
  label: string
  ok: boolean
  detail: string
}

export function FullScenario() {
  const { setReservationId } = useStore()
  const [steps, setSteps] = useState<Step[]>([])
  const [running, setRunning] = useState(false)

  async function run() {
    setRunning(true)
    setSteps([])
    const out: Step[] = []
    const push = (label: string, ok: boolean, detail: string) => {
      out.push({ label, ok, detail })
      setSteps([...out])
    }

    const reservation = await api.createReservation({
      eventId: SEED_EVENTS[0].id,
      customerRef: 'demo@esgi.fr',
      quantity: 1,
    })
    const res = reservation.data
    const rid = res?.id ?? ''
    if (rid) setReservationId(rid)
    push('Réservation créée', reservation.ok, rid ? `${rid.slice(0, 8)}… · ${res.status}` : `HTTP ${reservation.status}`)
    if (!reservation.ok || !rid) {
      setRunning(false)
      return
    }

    let paid = false
    let current = res
    for (let attempt = 0; attempt < 20; attempt += 1) {
      const followed = await api.getReservation(rid)
      current = followed.data
      if (followed.ok && current.status === 'PAID') {
        paid = true
        break
      }
      await new Promise((resolve) => setTimeout(resolve, 500))
    }
    push(
      'Pipeline Kafka terminé',
      paid,
      paid
        ? `SeatReserved → PaymentReceived · ${current.amount.toFixed(2)} €`
        : 'Délai dépassé',
    )

    const status = await api.paymentStatus(rid)
    const st = status.data as { paymentStatus?: string }
    push('Statut paiement (circuit breaker)', status.ok, st?.paymentStatus ?? String(status.data))

    setRunning(false)
  }

  return (
    <Card className="border-primary/20 bg-gradient-to-br from-primary/5 to-transparent">
      <CardHeader>
        <CardTitle>Pipeline événementiel TP5</CardTitle>
        <CardDescription>
          Réserver via HTTP, puis laisser Kafka déclencher le paiement et confirmer l'état.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <Button onClick={run} disabled={running} size="lg">
          <PlayCircle className="size-4" />
          {running ? 'Exécution…' : 'Lancer le pipeline'}
        </Button>
        {steps.length > 0 && (
          <ol className="space-y-2">
            {steps.map((step, i) => (
              <li key={i} className="flex items-center gap-3 rounded-lg border bg-background p-3">
                {step.ok ? (
                  <CheckCircle2 className="size-5 text-emerald-400" />
                ) : (
                  <XCircle className="size-5 text-destructive" />
                )}
                <div className="min-w-0">
                  <p className="text-sm font-medium">{step.label}</p>
                  <p className="truncate text-xs text-muted-foreground">{step.detail}</p>
                </div>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
