import { createContext, useContext, useState, type ReactNode } from 'react'

interface Store {
  reservationId: string
  setReservationId: (v: string) => void
}

const StoreContext = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [reservationId, setReservationId] = useState('')
  return (
    <StoreContext.Provider value={{ reservationId, setReservationId }}>
      {children}
    </StoreContext.Provider>
  )
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error('useStore doit être utilisé dans un StoreProvider')
  return ctx
}
