import { AuditLogCard } from '@/features/audit-log/AuditLogCard'
import { PageHeader } from '@/components/PageHeader'

export function AuditLogPage() {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Audit-Log" />
      <AuditLogCard />
    </div>
  )
}
