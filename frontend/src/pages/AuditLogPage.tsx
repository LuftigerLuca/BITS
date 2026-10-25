import { AuditLogCard } from '@/features/audit-log/AuditLogCard'

export function AuditLogPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Audit-Log</h1>
      <AuditLogCard />
    </div>
  )
}
