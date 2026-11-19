import { useEffect, useState, useSyncExternalStore } from 'react'
import { getSyncState, subscribeSync } from '../repo/sync'
import { getSettings, subscribeSettings } from '../repo/settings'

export const useSyncState = () => useSyncExternalStore(subscribeSync, getSyncState)
export const useSettings = () => useSyncExternalStore(subscribeSettings, getSettings)

/** Wide layout (tablet / desktop): navigation rail + list + detail side by side. */
export function useWide(): boolean {
  const query = '(min-width: 760px)'
  const [wide, setWide] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const m = window.matchMedia(query)
    const on = () => setWide(m.matches)
    m.addEventListener('change', on)
    return () => m.removeEventListener('change', on)
  }, [])
  return wide
}

/** Re-renders every minute (for "now" lines and relative labels). */
export function useMinuteTick(): number {
  const [n, setN] = useState(Date.now())
  useEffect(() => {
    const id = setInterval(() => setN(Date.now()), 60_000)
    return () => clearInterval(id)
  }, [])
  return n
}
