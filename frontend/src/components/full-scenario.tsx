import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
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
  const qc = useQueryClient()
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
    const res = reservation.data as { id?: string; status?: string }
    const rid = res?.id ?? ''
    if (rid) setReservationId(rid)
    push('Réservation créée', reservation.ok, rid ? `${rid.slice(0, 8)}… · ${res.status}` : `HTTP ${reservation.status}`)

    const payment = await api.createPayment({ reservationId: rid, amount: 42 })
    const pay = payment.data as { status?: string }
    push('Paiement encaissé', payment.ok, `HTTP ${payment.status} · ${pay?.status ?? ''}`)

    const status = await api.paymentStatus(rid)
    const st = status.data as { paymentStatus?: string }
    push('Statut paiement (circuit breaker)', status.ok, st?.paymentStatus ?? String(status.data))

    const notify = await api.notify({
      to: 'demo@esgi.fr',
      reservationId: rid,
      eventName: 'Concert Metallica',
      quantity: 1,
    })
    push('Email de confirmation envoyé', notify.ok, `HTTP ${notify.status}`)

    qc.invalidateQueries({ queryKey: ['emails'] })
    setRunning(false)
  }

  return (
    <Card className="border-primary/20 bg-gradient-to-br from-primary/5 to-transparent">
      <CardHeader>
        <CardTitle>Scénario HTTP TP4</CardTitle>
        <CardDescription>
          Orchestration manuelle réserver → payer → vérifier → notifier, à travers les 3
          services et le gateway.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <Button onClick={run} disabled={running} size="lg">
          <PlayCircle className="size-4" />
          {running ? 'Exécution…' : 'Lancer le scénario HTTP'}
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
