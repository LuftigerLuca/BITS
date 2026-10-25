import { RoleRulesCard } from '@/features/role-sync/RoleRulesCard'

export function RolesPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Rollen</h1>
      <RoleRulesCard />
    </div>
  )
}
