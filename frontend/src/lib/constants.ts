// Événements amorcés au démarrage du booking-service (DataSeeder).
export const SEED_EVENTS = [
  { id: '11111111-1111-1111-1111-111111111111', label: 'Concert Metallica — 100 places' },
  { id: '22222222-2222-2222-2222-222222222222', label: 'PSG – OM — 5 places (stock serré)' },
]

export const SERVICES = [
  { name: 'booking-service', lang: 'Java / Spring Boot', routes: '/events, /reservations' },
  { name: 'payment-service', lang: 'Node / NestJS', routes: '/payments' },
  { name: 'notification-service', lang: 'Python / FastAPI', routes: '/notifications' },
]
