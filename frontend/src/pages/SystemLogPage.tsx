import { SystemLogCard } from '@/features/system-log/SystemLogCard'

export function SystemLogPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">System-Log</h1>
      <SystemLogCard />
    </div>
  )
}
