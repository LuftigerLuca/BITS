import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import { NewRuleCard } from '@/features/role-sync/NewRuleCard'
import { RoleRulesCard } from '@/features/role-sync/RoleRulesCard'

export function RolesPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Rollen</h1>
      <FeatureOffNotice feature="ROLE_SYNC">
        Der Rollen-Sync pausiert: Du kannst die Regeln weiter bearbeiten, aber BITS vergibt und entzieht keine Rollen,
        bis er unter „Verwaltung“ wieder eingeschaltet ist. Dann gleicht BITS sofort alle Mitglieder ab.
      </FeatureOffNotice>
      <RoleRulesCard />
      <NewRuleCard />
    </div>
  )
}
