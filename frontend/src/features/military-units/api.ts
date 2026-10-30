import { request, type Option } from '@/lib/api'

/** A role a DSK may get: only one without a rule, no status role and of no other DSK. */
export interface DskRoleOption {
  id: string
  name: string
  color: number | null
  hasRule: boolean
  statusRole: boolean
  dskId: number | null
}

export type DskRoleStatus = 'ACTIVE' | 'ORPHANED' | 'NOT_ASSIGNABLE'

/** A DSK: the linked members of its military units get its role. */
export interface Dsk {
  id: number
  name: string
  /** `null` without a role; the DSK then changes nothing. */
  roleId: string | null
  /** `null` without a role or if it was deleted in Discord. */
  roleName: string | null
  roleColor: number | null
  roleStatus: DskRoleStatus | null
  /** By name, as of the time they were assigned. */
  militaryUnits: Option[]
}

export interface MilitaryUnits {
  /** The roles the bot can give, highest first. */
  roles: DskRoleOption[]
  dsks: Dsk[]
}

export interface MilitaryUnitOption {
  id: string
  name: string
  /** The country the unit is based in, `null` if WarEra names none. */
  countryName: string | null
  /** The DSK the unit belongs to already, `null` if none. */
  dskName: string | null
}

export interface DskRequest {
  name: string
  roleId: string | null
}

const MILITARY_UNITS = '/web/admin/military-units'

export async function getMilitaryUnits(): Promise<MilitaryUnits> {
  return (await request(MILITARY_UNITS)).json()
}

export async function createDsk(dsk: DskRequest): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dsk),
  })
}

/** Renames the DSK and sets its role. */
export async function updateDsk(id: number, dsk: DskRequest): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dsk),
  })
}

export async function deleteDsk(id: number): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${id}`, { method: 'DELETE' })
}

/** Military units of all countries whose name matches [query]. */
export async function searchDskMilitaryUnits(query: string): Promise<MilitaryUnitOption[]> {
  return (await request(`${MILITARY_UNITS}/search?${new URLSearchParams({ query })}`)).json()
}

export async function addDskMilitaryUnit(dskId: number, muId: string): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${dskId}/military-units`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ muId }),
  })
}

export async function removeDskMilitaryUnit(dskId: number, muId: string): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${dskId}/military-units/${encodeURIComponent(muId)}`, { method: 'DELETE' })
}
