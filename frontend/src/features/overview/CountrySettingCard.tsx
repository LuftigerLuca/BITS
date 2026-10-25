import { useState, type FormEvent } from 'react'
import { ErrorState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
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
          {setCountry.isError && (
            <Alert variant="destructive">
              <AlertDescription>{countryErrorMessage(setCountry.error)}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-col gap-1.5 sm:max-w-sm">
            <Label htmlFor="overview-country">Land der Startseite</Label>
            <Select
              items={items}
              value={countryId}
              onValueChange={setPicked}
              disabled={countries.isPending || overview.isPending}
            >
              <SelectTrigger id="overview-country" className="w-full">
                <SelectValue placeholder={countries.isPending ? 'Länder werden geladen …' : 'Land wählen'} />
              </SelectTrigger>
              <SelectContent>
                {items.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button type="submit" className="self-start" disabled={setCountry.isPending || !countryId || unchanged}>
            Speichern
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
