import { vi } from 'vitest'
import type { Feature } from '@/features/feature-toggles/api'

type Handler = (init?: RequestInit) => Response | Promise<Response>

const FEATURES: Feature[] = ['VERIFICATION', 'ROLE_SYNC', 'NICKNAMES', 'PROFILE', 'VOTES', 'EMBASSIES']

/** Every feature on, unless [off] names it. */
export function features(...off: Feature[]): Response {
  return json(FEATURES.map((feature) => ({ feature, enabled: !off.includes(feature) })))
}

/**
 * Answers fetch calls by method and path, e.g. `'POST /auth/login'`; any other call fails the test. Every page asks
 * which features are on, so they are all on unless [routes] answers `GET /web/features` itself.
 */
export function stubFetch(routes: Record<string, Handler>) {
  const handlers: Record<string, Handler> = { 'GET /web/features': () => features(), ...routes }
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(input.toString(), 'http://localhost')
    const key = `${init?.method ?? 'GET'} ${url.pathname}`
    const handler = handlers[key]
    if (!handler) throw new Error(`unexpected request ${key}`)
    return handler(init)
  })
  vi.stubGlobal('fetch', fetch)
  return fetch
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function status(code: number): Response {
  return new Response(null, { status: code })
}
