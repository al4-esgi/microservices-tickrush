import { createContext, useContext } from 'react'

interface Store {
  reservationId: string
  setReservationId: (value: string) => void
}

export const StoreContext = createContext<Store | null>(null)

export function useStore() {
  const context = useContext(StoreContext)
  if (!context) throw new Error('useStore doit être utilisé dans un StoreProvider')
  return context
}
