import { SystemStatusView } from '@/features/system-status/SystemStatusView'

export function SystemStatusPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">System-Status</h1>
      <SystemStatusView />
    </div>
  )
}
