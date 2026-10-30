import { useState } from 'react'
import type { RoleRule } from '@/features/role-sync/api'
import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import { NewRuleCard } from '@/features/role-sync/NewRuleCard'
import { RemovalLimitCard } from '@/features/role-sync/RemovalLimitCard'
import { RoleRulesCard } from '@/features/role-sync/RoleRulesCard'
import { StatusRolesCard } from '@/features/role-sync/StatusRolesCard'
import { PageHeader } from '@/components/PageHeader'

export function RolesPage() {
  /** The rule whose role the form below the list edits. */
  const [editing, setEditing] = useState<RoleRule | null>(null)
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Rollen" />
      <FeatureOffNotice feature="ROLE_SYNC">
        Der Rollen-Sync pausiert: Du kannst die Regeln weiter bearbeiten, aber BITS vergibt und entzieht keine Rollen,
        bis er unter „Verwaltung“ wieder eingeschaltet ist. Dann gleicht BITS sofort alle Mitglieder ab.
      </FeatureOffNotice>
      <RoleRulesCard onEdit={setEditing} />
      <NewRuleCard editing={editing} onEdit={setEditing} onDone={() => setEditing(null)} />
      <StatusRolesCard />
      <RemovalLimitCard />
    </div>
  )
}
