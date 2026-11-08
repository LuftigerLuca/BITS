/** The tabs of the admin page; the value is what `?tab=` holds, so links elsewhere can open one directly. */
export const ADMIN_TABS = [
  { value: 'funktionen', label: 'Funktionen' },
  { value: 'server', label: 'Server' },
  { value: 'abstimmungen', label: 'Abstimmungen' },
  { value: 'tops', label: 'TOPs' },
] as const

export type AdminTab = (typeof ADMIN_TABS)[number]['value']

/** Link to [tab] of the admin page. */
export function adminTabPath(tab: AdminTab): string {
  return `/admin?tab=${tab}`
}

/** The tab `?tab=` names; without one, or with one the page does not know, the first. */
export function adminTabOf(param: string | null): AdminTab {
  return ADMIN_TABS.find((tab) => tab.value === param)?.value ?? ADMIN_TABS[0].value
}
