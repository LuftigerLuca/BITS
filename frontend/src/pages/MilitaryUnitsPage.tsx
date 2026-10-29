import { ErrorState, LoadingState } from '@/components/QueryState'
import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import { DsksCard } from '@/features/military-units/DsksCard'
import { ERROR_MESSAGE } from '@/features/military-units/labels'
import { useMilitaryUnits } from '@/features/military-units/queries'

export function MilitaryUnitsPage() {
  const militaryUnits = useMilitaryUnits()
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Militäreinheiten</h1>
      <FeatureOffNotice feature="ROLE_SYNC">
        Der Rollen-Sync pausiert: Du kannst die DSKs weiter bearbeiten, aber BITS vergibt und entzieht ihre Rollen erst
        wieder, wenn er unter „Verwaltung“ eingeschaltet ist.
      </FeatureOffNotice>
      {militaryUnits.isPending ? (
        <LoadingState label="Militäreinheiten werden geladen …" />
      ) : militaryUnits.isError ? (
        <ErrorState message={ERROR_MESSAGE} onRetry={() => militaryUnits.refetch()} />
      ) : (
        <DsksCard data={militaryUnits.data} />
      )}
    </div>
  )
}
