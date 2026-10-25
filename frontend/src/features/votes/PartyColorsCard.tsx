import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { PartyColors } from '@/lib/api'
import { partyColorsErrorMessage } from './labels'
import { usePartyColors, useSetPartyColors } from './queries'

/** What a colour field starts with once a party gets a colour of its own; the palette's depends on each vote. */
const PICKER_DEFAULT = '#3987E5'
const NO_PARTY_DEFAULT = '#9A9EA6'

/** Key of those in no party among the colours; WarEra's IDs never look like this. */
const NO_PARTY = ''

/** Admins only: the colours of the parties of the country set for the start page in the result image. */
export function PartyColorsCard() {
  const partyColors = usePartyColors()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Parteifarben</CardTitle>
        <CardDescription>
          Ohne eigene Farbe bekommt jede Partei eine Farbe aus der Palette, je nach ihrem Platz in der Abstimmung.
          Parteien anderer Länder behalten immer die Palettenfarbe.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {partyColors.isPending ? (
          <LoadingState label="Parteien werden geladen …" />
        ) : partyColors.isError ? (
          <ErrorState message={partyColorsErrorMessage(partyColors.error)} onRetry={() => partyColors.refetch()} />
        ) : partyColors.data.countryName === null ? (
          <p className="text-sm text-muted-foreground">
            Lege unter „Startseite“ ein Land fest, um die Farben seiner Parteien einzustellen.
          </p>
        ) : (
          // Keyed so a reload of the saved colours resets the form.
          <PartyColorsForm key={JSON.stringify(partyColors.data)} saved={partyColors.data} />
        )}
      </CardContent>
    </Card>
  )
}

function PartyColorsForm({ saved }: { saved: PartyColors }) {
  const initial = () => {
    const colors: Record<string, string | null> = { [NO_PARTY]: saved.noPartyColor }
    for (const party of saved.parties) colors[party.id] = party.color
    return colors
  }
  const [colors, setColors] = useState(initial)
  const setPartyColors = useSetPartyColors()
  const changed = JSON.stringify(colors) !== JSON.stringify(initial())

  function set(id: string, color: string | null) {
    setColors({ ...colors, [id]: color })
  }

  function submit() {
    const own: Record<string, string> = {}
    for (const party of saved.parties) {
      const color = colors[party.id]
      if (color) own[party.id] = color.toUpperCase()
    }
    setPartyColors.mutate({ colors: own, noPartyColor: colors[NO_PARTY]?.toUpperCase() ?? null })
  }

  const rows = [
    ...saved.parties.map((party) => ({ id: party.id, name: party.name, fallback: PICKER_DEFAULT })),
    { id: NO_PARTY, name: 'Parteilos', fallback: NO_PARTY_DEFAULT },
  ]
  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      {setPartyColors.isError && (
        <Alert variant="destructive">
          <AlertDescription>{partyColorsErrorMessage(setPartyColors.error)}</AlertDescription>
        </Alert>
      )}
      <p className="text-sm">Parteien in {saved.countryName}</p>
      {saved.parties.length === 0 && (
        <p className="text-sm text-muted-foreground">WarEra kennt in diesem Land keine Parteien.</p>
      )}
      <ul className="flex flex-col divide-y">
        {rows.map((row) => {
          const color = colors[row.id] ?? null
          return (
            <li key={row.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 py-2 text-sm">
              <span className="min-w-0 flex-1 truncate font-medium">{row.name}</span>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  className="size-4 accent-primary"
                  checked={color !== null}
                  onChange={(event) => set(row.id, event.target.checked ? row.fallback : null)}
                />
                Eigene Farbe
              </label>
              <span className="flex w-36 items-center gap-2">
                {color === null ? (
                  <span className="text-muted-foreground">Farbe aus der Palette</span>
                ) : (
                  <>
                    <input
                      type="color"
                      aria-label={`Farbe von ${row.name}`}
                      className="h-8 w-10 shrink-0 cursor-pointer rounded border bg-transparent"
                      value={color.toLowerCase()}
                      onChange={(event) => set(row.id, event.target.value)}
                    />
                    <span className="font-mono text-muted-foreground">{color.toUpperCase()}</span>
                  </>
                )}
              </span>
            </li>
          )
        })}
      </ul>
      <Button type="submit" className="self-start" disabled={!changed || setPartyColors.isPending}>
        Speichern
      </Button>
    </form>
  )
}
