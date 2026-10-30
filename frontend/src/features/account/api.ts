import { request } from '@/lib/api'

export interface Account {
  /** `null` if the member has not linked a WarEra account. */
  link: { wareraUsername: string; verifiedAt: number } | null
  /** Epoch milliseconds of the last sync of all members; `null` if none is known. */
  lastSyncAt: number | null
}

export type AccountRoleOutcome = 'MET' | 'NOT_MET' | 'UNDECIDED'

export interface AccountRole {
  id: string
  name: string
  color: number | null
  outcome: AccountRoleOutcome
  /** The blocks of the rule of the role, any one of which is enough. */
  rules: { label: string; met: boolean | null; detail: string | null }[]
}

export interface AccountRoles {
  linked: boolean
  /** Every role BITS manages, highest first; judged with fresh WarEra data. */
  roles: AccountRole[]
}

const ACCOUNT = '/web/account'

/** A plain link downloads the file; the browser sends the session cookie along. */
export const ACCOUNT_EXPORT_URL = `${ACCOUNT}/export`

export async function getAccount(): Promise<Account> {
  return (await request(ACCOUNT)).json()
}

export async function getAccountRoles(): Promise<AccountRoles> {
  return (await request(`${ACCOUNT}/roles`)).json()
}

export async function unlinkAccount(): Promise<void> {
  await request(`${ACCOUNT}/link`, { method: 'DELETE' })
}
