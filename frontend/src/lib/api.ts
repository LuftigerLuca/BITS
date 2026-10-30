// Talks to the backend on the same origin (nginx in production, the Vite proxy in development), so the
// HttpOnly session cookie is sent along without any CORS setup.

export type Role = 'USER' | 'ADMIN'

export interface Me {
  name: string
  avatarUrl: string | null
  role: Role
  wareraUsername: string | null
  /** `false` for the admin account from the environment, which has no Discord account and no page "Mein Account". */
  discordUser: boolean
}

export interface AuthOptions {
  adminLogin: boolean
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed with status ${status}`)
    this.status = status
  }
}

let onUnauthorized: () => void = () => {}

/** Called whenever the backend rejects the session, e.g. because it expired. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export async function request(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(path, { credentials: 'same-origin', ...init })
  if (response.status === 401 && path.startsWith('/web/')) onUnauthorized()
  if (!response.ok) throw new ApiError(response.status)
  return response
}

export async function getMe(): Promise<Me> {
  return (await request('/web/me')).json()
}

export async function getAuthOptions(): Promise<AuthOptions> {
  return (await request('/auth/options')).json()
}

export async function adminLogin(username: string, password: string): Promise<void> {
  await request('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export async function logout(): Promise<void> {
  await request('/auth/logout', { method: 'POST' })
}

export interface Option {
  id: string
  name: string
}

export const DISCORD_LOGIN_URL = '/auth/oauth2/authorization/discord'
