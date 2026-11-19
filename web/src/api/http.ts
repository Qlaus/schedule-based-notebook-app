import { getAccessToken } from '../auth/session'

export class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

/** Thrown when there is no valid access token; the UI offers to renew the session. */
export class AuthRequiredError extends Error {}

/** Thrown for network failures (offline etc.). */
export class OfflineError extends Error {}

export async function googleFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = getAccessToken()
  if (!token) throw new AuthRequiredError('no token')
  let res: Response
  try {
    res = await fetch(url, { ...init, headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` } })
  } catch {
    throw new OfflineError('network')
  }
  if (res.status === 401) throw new AuthRequiredError('401')
  return res
}

export async function googleJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const res = await googleFetch(url, init)
  if (!res.ok) throw new HttpError(res.status, `HTTP ${res.status}`)
  return (await res.json()) as T
}
