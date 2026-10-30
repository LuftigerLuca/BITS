import { PageHeader } from '@/components/PageHeader'
import { SystemStatusView } from '@/features/system-status/SystemStatusView'

export function SystemStatusPage() {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="System-Status" />
      <SystemStatusView />
    </div>
  )
}
