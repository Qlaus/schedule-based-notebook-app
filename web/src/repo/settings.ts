/** Per-device settings (same defaults as Android). */
export interface AppSettings { titleMarker: boolean; descriptionMarker: boolean }

const KEY = 'calnotes.settings'
const defaults: AppSettings = { titleMarker: true, descriptionMarker: false }
const listeners = new Set<() => void>()
let cached: AppSettings | null = null

export function getSettings(): AppSettings {
  if (!cached) {
    try {
      cached = { ...defaults, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
    } catch {
      cached = { ...defaults }
    }
  }
  return cached!
}

export function setSettings(patch: Partial<AppSettings>) {
  cached = { ...getSettings(), ...patch }
  localStorage.setItem(KEY, JSON.stringify(cached))
  listeners.forEach((l) => l())
}

export function subscribeSettings(l: () => void) {
  listeners.add(l)
  return () => listeners.delete(l)
}
