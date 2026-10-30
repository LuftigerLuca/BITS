import { useState, type FormEvent } from 'react'
import { ErrorState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { useCountries } from '@/lib/countries'
import { ERROR_MESSAGE, countryErrorMessage } from './labels'
import { useOverview, useSetOverviewCountry } from './queries'

/** The WarEra country whose citizens the start page counts. */
export function CountrySettingCard() {
  const overview = useOverview()
  const countries = useCountries(true)
  const setCountry = useSetOverviewCountry()
  // `undefined` until the admin picks one, so the current country shows once the overview arrives.
  const [picked, setPicked] = useState<string | null>()
  const countryId = picked === undefined ? (overview.data?.country?.id ?? null) : picked

  function submit(event: FormEvent) {
    event.preventDefault()
    if (countryId) setCountry.mutate(countryId, { onSuccess: () => setPicked(undefined) })
  }

  const items = (countries.data ?? []).map((c) => ({ value: c.id, label: c.name }))
  const unchanged = countryId === (overview.data?.country?.id ?? null)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Startseite</CardTitle>
        <CardDescription>Die Startseite zeigt Admins, wie viele Bürger dieses Land in WarEra hat.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {overview.isError && <ErrorState message={ERROR_MESSAGE} onRetry={() => overview.refetch()} />}
          {countries.isError && (
            <ErrorState message={countryErrorMessage(countries.error)} onRetry={() => countries.refetch()} />
          )}
          <Field className="sm:max-w-sm">
            <FieldLabel htmlFor="overview-country">Land der Startseite</FieldLabel>
            <SearchableSelect
              id="overview-country"
              className="w-full"
              items={items}
              value={countryId}
              onValueChange={setPicked}
              disabled={countries.isPending || overview.isPending}
              placeholder={countries.isPending ? 'Länder werden geladen …' : 'Land wählen'}
            />
          </Field>
          <Button type="submit" className="self-start" disabled={setCountry.isPending || !countryId || unchanged}>
            Speichern
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
