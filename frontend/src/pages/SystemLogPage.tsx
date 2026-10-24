import { ArrowLeftIcon } from 'lucide-react'
import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { SystemLogCard } from '@/features/system-log/SystemLogCard'

export function SystemLogPage() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <Link to="/admin" aria-label="Zurück zur Verwaltung" className={buttonVariants({ variant: 'ghost', size: 'icon-sm' })}>
          <ArrowLeftIcon />
        </Link>
        <h1 className="font-heading text-xl font-semibold">System-Log</h1>
      </div>
      <SystemLogCard />
    </div>
  )
}
