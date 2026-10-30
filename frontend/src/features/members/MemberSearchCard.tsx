import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { EmptyState, ErrorState, LoadingState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { SEARCH_ERROR } from './labels'
import { useMemberSearch } from './queries'

/** Finds members by their Discord or WarEra name; a hit opens the member below. */
export function MemberSearchCard() {
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const results = useMemberSearch(query)

  function search(event: FormEvent) {
    event.preventDefault()
    setQuery(input.trim())
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Mitglied suchen</CardTitle>
        <CardDescription>
          Nach Discord-Name, WarEra-Name oder Discord-ID. WarEra-Namen sind die beim Verknüpfen, Discord-Namen die der
          letzten Meldung des Bots.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 text-sm">
        <form onSubmit={search}>
          <Field>
            <FieldLabel htmlFor="member-search">Name oder Discord-ID</FieldLabel>
            <div className="flex gap-2">
              <Input id="member-search" value={input} onChange={(event) => setInput(event.target.value)} />
              <Button type="submit" variant="outline">
                Suchen
              </Button>
            </div>
          </Field>
        </form>
        {query === '' ? null : results.isPending ? (
          <LoadingState label="Mitglieder werden gesucht …" />
        ) : results.isError ? (
          <ErrorState message={SEARCH_ERROR} onRetry={() => results.refetch()} />
        ) : results.data.length === 0 ? (
          <EmptyState title="Kein Mitglied gefunden" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Discord</TableHead>
                <TableHead>WarEra</TableHead>
                <TableHead>Discord-ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {results.data.map((member) => (
                <TableRow key={member.discordUserId}>
                  <TableCell>
                    <Link
                      to={`?${new URLSearchParams({ id: member.discordUserId })}`}
                      className="font-medium underline-offset-4 hover:underline"
                    >
                      {member.discordName ?? member.discordUserId}
                    </Link>
                    {member.onServer === false && (
                      <span className="text-muted-foreground"> (nicht auf dem Server)</span>
                    )}
                  </TableCell>
                  <TableCell>
                    {member.wareraUsername ?? <span className="text-muted-foreground">nicht verknüpft</span>}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{member.discordUserId}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
