import { SystemLogCard } from '@/features/system-log/SystemLogCard'
import { PageHeader } from '@/components/PageHeader'

export function SystemLogPage() {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="System-Log" />
      <SystemLogCard />
    </div>
  )
}
