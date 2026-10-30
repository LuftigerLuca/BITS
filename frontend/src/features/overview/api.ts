import { request, type Option } from '@/lib/api'

const OVERVIEW = '/web/admin/overview'

export interface CountryOverview {
  id: string
  name: string
  /** Whether WarEra could be reached; if not, the counts are `null`. */
  available: boolean
  citizens: number | null
  activeCitizens: number | null
}

export interface Overview {
  /** Members of the Discord without bots; `null` while the bot has not reported them since the backend started. */
  members: number | null
  /** Of `members`, those with a linked WarEra account. */
  verifiedMembers: number | null
  /** `null` while no country is set. */
  country: CountryOverview | null
}

export async function getOverview(): Promise<Overview> {
  return (await request(OVERVIEW)).json()
}

export interface OverviewHistoryDay {
  /** ISO date in Europe/Berlin. */
  day: string
  members: number | null
  verifiedMembers: number | null
  /** Of the country set now; `null` on days that counted another one. */
  citizens: number | null
  activeCitizens: number | null
}

export interface OverviewHistory {
  /** First and last day of the range, ISO dates; `to` is today. */
  from: string
  to: string
  /** The days with figures, oldest first; a missing day or a `null` figure is a gap, not 0. */
  days: OverviewHistoryDay[]
}

export async function getOverviewHistory(days: number): Promise<OverviewHistory> {
  return (await request(`${OVERVIEW}/history?days=${days}`)).json()
}

export async function setOverviewCountry(countryId: string): Promise<Option> {
  const response = await request(`${OVERVIEW}/country`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ countryId }),
  })
  return response.json()
}
