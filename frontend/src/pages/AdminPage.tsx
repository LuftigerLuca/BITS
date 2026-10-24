import { HistoryIcon } from 'lucide-react'
import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { RoleRulesCard } from '@/features/role-sync/RoleRulesCard'

export function AdminPage() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="font-heading text-xl font-semibold">Verwaltung</h1>
        <Link to="/admin/audit-log" className={buttonVariants({ variant: 'outline' })}>
          <HistoryIcon data-icon="inline-start" />
          Audit-Log
        </Link>
      </div>
      <RoleRulesCard />
    </div>
  )
}
