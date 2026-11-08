import { Link } from 'react-router'
import { useAuth } from '@/auth/context'
import { ADMIN_TABS, adminTabPath, type AdminTab } from '@/lib/adminTabs'

/**
 * Names [tab] of the admin page in a text, as „Verwaltung › Funktionen“. For admins it links there; others, such as
 * vote managers, cannot open the page, so they get the name only.
 */
export function AdminTabLink({ tab }: { tab: AdminTab }) {
  const { user } = useAuth()
  const label = `Verwaltung › ${ADMIN_TABS.find(({ value }) => value === tab)?.label}`
  return (
    <>
      „
      {user?.role === 'ADMIN' ? (
        <Link to={adminTabPath(tab)} className="underline underline-offset-4">
          {label}
        </Link>
      ) : (
        label
      )}
      “
    </>
  )
}
