import { useState, type ReactNode } from 'react'
import { StoreContext } from '@/store-context'

export function StoreProvider({ children }: { children: ReactNode }) {
  const [reservationId, setReservationId] = useState('')
  return (
    <StoreContext.Provider value={{ reservationId, setReservationId }}>
      {children}
    </StoreContext.Provider>
  )
}
