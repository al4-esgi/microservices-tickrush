import { useState } from 'react'
import { CheckCircle2, TicketCheck, Undo2, XCircle } from 'lucide-react'
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

type SagaMode = 'nominal' | 'compensation'

export function FullScenario() {
  const { setReservationId } = useStore()
  const [steps, setSteps] = useState<Step[]>([])
  const [runningMode, setRunningMode] = useState<SagaMode | null>(null)

  async function run(mode: SagaMode) {
    setRunningMode(mode)
    setSteps([])
    const out: Step[] = []
    const push = (label: string, ok: boolean, detail: string) => {
      out.push({ label, ok, detail })
      setSteps([...out])
    }

    const quantity = mode === 'nominal' ? 1 : 3
    const before = await api.getEvent(SEED_EVENTS[0].id)
    const stockBefore = before.data?.availableSeats ?? Number.NaN
    push('Stock initial', before.ok, before.ok ? `${stockBefore} place(s)` : `HTTP ${before.status}`)

    const reservation = await api.createReservation({
      eventId: SEED_EVENTS[0].id,
      customerRef: 'demo@esgi.fr',
      quantity,
    })
    const res = reservation.data
    const rid = res?.id ?? ''
    if (rid) setReservationId(rid)
    push(
      'Réservation créée',
      reservation.ok,
      rid ? `${rid.slice(0, 8)}… · ${res.status}` : `HTTP ${reservation.status}`,
    )
    if (!reservation.ok || !rid) {
      setRunningMode(null)
      return
    }

    const expectedStatus = mode === 'nominal' ? 'TICKET_ISSUED' : 'CANCELLED'
    let completed = false
    let current = res
    for (let attempt = 0; attempt < 30; attempt += 1) {
      const followed = await api.getReservation(rid)
      current = followed.data
      if (followed.ok && current.status === expectedStatus) {
        completed = true
        break
      }
      await new Promise((resolve) => setTimeout(resolve, 500))
    }
    push(
      mode === 'nominal' ? 'Billet émis' : 'Compensation terminée',
      completed,
      completed
        ? mode === 'nominal'
          ? `SeatReserved → PaymentReceived → TicketIssued · ${current.ticketId?.slice(0, 8)}…`
          : `SeatReserved → PaymentFailed → SeatReleased · ${current.amount.toFixed(2)} €`
        : 'Délai dépassé',
    )

    const after = await api.getEvent(SEED_EVENTS[0].id)
    const stockAfter = after.data?.availableSeats ?? Number.NaN
    const expectedStock = mode === 'nominal' ? stockBefore - quantity : stockBefore
    push(
      'Invariant de stock',
      after.ok && stockAfter === expectedStock,
      `${stockBefore} → ${stockAfter} place(s)`,
    )

    const status = await api.paymentStatus(rid)
    const payment = status.data as { paymentStatus?: string }
    push('Statut paiement (circuit breaker)', status.ok, payment?.paymentStatus ?? String(status.data))

    setRunningMode(null)
  }

  return (
    <Card className="border-primary/20 bg-gradient-to-br from-primary/5 to-transparent">
      <CardHeader>
        <CardTitle>Saga chorégraphiée TP6</CardTitle>
        <CardDescription>
          Démonstration du chemin nominal et de la remise en stock après refus du paiement.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => run('nominal')} disabled={runningMode !== null} size="lg">
            <TicketCheck className="size-4" />
            {runningMode === 'nominal' ? 'Émission…' : 'Chemin nominal'}
          </Button>
          <Button
            onClick={() => run('compensation')}
            disabled={runningMode !== null}
            size="lg"
            variant="outline"
          >
            <Undo2 className="size-4" />
            {runningMode === 'compensation' ? 'Compensation…' : 'Chemin compensé'}
          </Button>
        </div>
        {steps.length > 0 && (
          <ol className="space-y-2">
            {steps.map((step, index) => (
              <li key={index} className="flex items-center gap-3 rounded-lg border bg-background p-3">
                {step.ok ? (
                  <CheckCircle2 className="size-5 shrink-0 text-emerald-400" />
                ) : (
                  <XCircle className="size-5 shrink-0 text-destructive" />
                )}
                <div className="min-w-0">
                  <p className="text-sm font-medium">{step.label}</p>
                  <p className="break-words text-xs text-muted-foreground">{step.detail}</p>
                </div>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
