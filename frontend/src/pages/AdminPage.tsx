import { RoleRulesCard } from '@/features/role-sync/RoleRulesCard'

export function AdminPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Verwaltung</h1>
      <RoleRulesCard />
    </div>
  )
}
