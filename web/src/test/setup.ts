import 'fake-indexeddb/auto'

/** Minimal Web Storage for Node. */
class MemoryStorage {
  private m = new Map<string, string>()
  getItem(k: string) { return this.m.has(k) ? this.m.get(k)! : null }
  setItem(k: string, v: string) { this.m.set(k, String(v)) }
  removeItem(k: string) { this.m.delete(k) }
  clear() { this.m.clear() }
}
Object.assign(globalThis, { localStorage: new MemoryStorage(), sessionStorage: new MemoryStorage() })
