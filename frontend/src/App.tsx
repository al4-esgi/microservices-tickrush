import { Header } from '@/components/header'
import { FullScenario } from '@/components/full-scenario'
import { ReservationPanel } from '@/components/reservation-panel'
import { PaymentPanel } from '@/components/payment-panel'
import { NotificationPanel } from '@/components/notification-panel'
import { EmailsPanel } from '@/components/emails-panel'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

export default function App() {
  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <Header />
      <FullScenario />
      <Tabs defaultValue="reservation">
        <TabsList>
          <TabsTrigger value="reservation">Réservations</TabsTrigger>
          <TabsTrigger value="payment">Paiement</TabsTrigger>
          <TabsTrigger value="notification">Notification</TabsTrigger>
          <TabsTrigger value="emails">Emails</TabsTrigger>
        </TabsList>
        <TabsContent value="reservation">
          <ReservationPanel />
        </TabsContent>
        <TabsContent value="payment">
          <PaymentPanel />
        </TabsContent>
        <TabsContent value="notification">
          <NotificationPanel />
        </TabsContent>
        <TabsContent value="emails">
          <EmailsPanel />
        </TabsContent>
      </Tabs>
      <footer className="pt-4 text-center text-xs text-muted-foreground">
        TickRush · ESGI 4AL · k3s + Traefik · saga Kafka chorégraphiée (TP6)
      </footer>
    </div>
  )
}
