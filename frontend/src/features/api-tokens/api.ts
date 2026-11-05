import { request } from '@/lib/api'

/** `EXPIRING` expires within two weeks. */
export type ApiTokenStatus = 'ACTIVE' | 'EXPIRING' | 'EXPIRED'

/** A token of the third-party API; the secret itself is only in the answer that creates it. */
export interface ApiToken {
  id: number
  name: string
  /** The start of the token, e.g. `bits_AbCd`. */
  hint: string
  /** IDs of its scopes. */
  scopes: string[]
  createdAt: number
  createdBy: string
  /** The first moment the token is no longer valid: midnight after its last day. */
  expiresAt: number
  /** To the minute; `null` if it was never used. */
  lastUsedAt: number | null
  status: ApiTokenStatus
}

/** The right to call one endpoint of the API, always with GET. */
export interface ApiScope {
  id: string
  path: string
  summary: string
}

export interface ApiTokens {
  /** The newest first. */
  tokens: ApiToken[]
  scopes: ApiScope[]
}

export interface CreateApiTokenRequest {
  name: string
  /** The last day the token is valid, e.g. `2026-12-31`. */
  expiresOn: string
  scopes: string[]
}

export interface CreatedApiToken {
  token: ApiToken
  secret: string
}

const API_TOKENS = '/web/admin/api-tokens'

export async function getApiTokens(): Promise<ApiTokens> {
  return (await request(API_TOKENS)).json()
}

export async function createApiToken(token: CreateApiTokenRequest): Promise<CreatedApiToken> {
  return (
    await request(API_TOKENS, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(token),
    })
  ).json()
}

/** Deletes the token; it stops working at once. */
export async function revokeApiToken(id: number): Promise<void> {
  await request(`${API_TOKENS}/${id}`, { method: 'DELETE' })
}
