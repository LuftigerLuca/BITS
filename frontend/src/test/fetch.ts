import { vi } from 'vitest'

type Handler = (init?: RequestInit) => Response | Promise<Response>

/** Answers fetch calls by method and path, e.g. `'POST /auth/login'`; any other call fails the test. */
export function stubFetch(routes: Record<string, Handler>) {
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(input.toString(), 'http://localhost')
    const key = `${init?.method ?? 'GET'} ${url.pathname}`
    const handler = routes[key]
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
