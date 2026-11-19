/** In-memory fake of the Google Calendar + Drive endpoints the app uses. */
export interface FakeEvent { id: string; summary?: string; description?: string; organizer?: { self?: boolean; email?: string }; start: object; end: object; status?: string; recurringEventId?: string }
interface DriveFile { id: string; name: string; appProperties: Record<string, string>; content: string }

export class FakeGoogle {
  calendars = [{ id: 'cal', summary: 'Ders', accessRole: 'owner', primary: true, selected: true, backgroundColor: '#ff0000' }]
  events = new Map<string, FakeEvent>()
  files = new Map<string, DriveFile>()
  requests: string[] = []
  failNext: number | null = null
  private next = 1

  install() {
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => this.handle(String(input), init ?? {})) as typeof fetch
  }

  private json(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  }

  private async handle(url: string, init: RequestInit): Promise<Response> {
    const method = init.method ?? 'GET'
    const u = new URL(url)
    this.requests.push(`${method} ${u.pathname}`)
    if (this.failNext) {
      const s = this.failNext
      this.failNext = null
      return new Response('', { status: s })
    }
    const path = decodeURIComponent(u.pathname)
    if (path === '/calendar/v3/users/me/calendarList') return this.json({ items: this.calendars })
    const ev = path.match(/^\/calendar\/v3\/calendars\/([^/]+)\/events(?:\/(.+))?$/)
    if (ev) {
      const id = ev[2]
      if (!id && method === 'GET') return this.json({ items: [...this.events.values()] })
      if (!id && method === 'POST') {
        const body = JSON.parse(String(init.body))
        const e = { id: `srv${this.next++}`, ...body }
        this.events.set(e.id, e)
        return this.json(e)
      }
      const e = this.events.get(id!)
      if (!e) return new Response('', { status: 404 })
      if (method === 'GET') return this.json(e)
      if (method === 'PATCH') {
        const body = JSON.parse(String(init.body))
        Object.assign(e, body)
        return this.json(e)
      }
      if (method === 'DELETE') {
        this.events.delete(id!)
        return new Response(null, { status: 204 })
      }
    }
    if (path === '/drive/v3/files') return this.json({ files: [...this.files.values()].map(({ id, name, appProperties }) => ({ id, name, appProperties })) })
    const dl = path.match(/^\/drive\/v3\/files\/(.+)$/)
    if (dl) {
      const f = this.files.get(dl[1])
      return f ? new Response(f.content) : new Response('', { status: 404 })
    }
    const up = path.match(/^\/upload\/drive\/v3\/files(?:\/(.+))?$/)
    if (up) {
      const ct = new Headers(init.headers).get('Content-Type')!
      const boundary = ct.split('boundary=')[1]
      const parts = String(init.body).split(`--${boundary}`).slice(1).filter((p) => !p.startsWith('--'))
      const payload = (p: string) => p.replace(/^\r\n/, '').split('\r\n\r\n').slice(1).join('\r\n\r\n').replace(/\r\n$/, '')
      const meta = JSON.parse(payload(parts[0]))
      const content = payload(parts[1])
      if (up[1]) {
        const f = this.files.get(up[1])!
        f.appProperties = { ...f.appProperties, ...meta.appProperties }
        f.content = content
        return this.json({ id: f.id, name: f.name })
      }
      const f = { id: `f${this.next++}`, name: meta.name, appProperties: meta.appProperties ?? {}, content }
      this.files.set(f.id, f)
      return this.json({ id: f.id, name: f.name })
    }
    return new Response('', { status: 400 })
  }

  file(name: string) {
    return [...this.files.values()].find((f) => f.name === name)!
  }
}

export function signInForTest() {
  localStorage.setItem('calnotes.token', JSON.stringify({ accessToken: 't', expiresAt: Date.now() + 3_600_000, scope: 'drive.appdata' }))
  localStorage.setItem('calnotes.account', JSON.stringify({ sub: 'acc', email: 'a@b.c' }))
}
