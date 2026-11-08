import { request } from '@/lib/api'

const TOPS = '/web/admin/tops'

export interface TopTag {
  id: string
  name: string
}

export interface TopForum {
  id: string
  name: string
  category: string | null
  /** Whether the bot may lock, archive and tag the posts there. */
  usable: boolean
  tags: TopTag[]
}

export interface TopForumSetting {
  forumId: string
  /** `null` for no tag. */
  doneTagId: string | null
}

export interface TopSettings {
  /** Forum channels in the order of Discord. */
  forums: TopForum[]
  selected: TopForumSetting[]
}

export interface TopRole {
  id: string
  name: string
  color: number | null
}

export interface TopManagerRoles {
  /** The roles of the guild, highest first. */
  roles: TopRole[]
  /** The roles whose members may manage TOPs besides the admins. */
  roleIds: string[]
}

const TOP_MANAGER_ROLES = `${TOPS}/manager-roles`

export async function getTopManagerRoles(): Promise<TopManagerRoles> {
  return (await request(TOP_MANAGER_ROLES)).json()
}

export async function setTopManagerRoles(roleIds: string[]): Promise<void> {
  await request(TOP_MANAGER_ROLES, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roleIds }),
  })
}

export async function getTopSettings(): Promise<TopSettings> {
  return (await request(TOPS)).json()
}

export async function setTopForums(forums: TopForumSetting[]): Promise<void> {
  await request(TOPS, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ forums }),
  })
}
