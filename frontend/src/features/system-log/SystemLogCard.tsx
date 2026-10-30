import { Fragment, useState, type FormEvent } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { LogCategory, SystemLogEntry, SystemLogFilter } from '@/lib/api'
import { CATEGORIES, CATEGORY_BADGES, ERROR_MESSAGE, OUTCOMES, STATUS, describe, formatTime, name } from './labels'
import { useSystemLog, useSystemLogItems, useSystemLogRoles } from './queries'

/** The Select's value for "no filter"; real values are categories and role IDs, which never look like this. */
const ALL = 'all'

/** What BITS did on its own, newest first: sync runs with their role changes, account links and errors. */
export function SystemLogCard() {
  // The page of a member links here with `?member=<Discord ID>`.
  const [searchParams] = useSearchParams()
  const [category, setCategory] = useState(ALL)
  const [roleId, setRoleId] = useState(ALL)
  const [memberInput, setMemberInput] = useState(() => searchParams.get('member') ?? '')
  const [member, setMember] = useState(() => searchParams.get('member') ?? '')
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const filter: SystemLogFilter = {
    category: category === ALL ? null : (category as LogCategory),
    member: member.trim() || null,
    roleId: roleId === ALL ? null : roleId,
  }
  const log = useSystemLog(filter)
  const roles = useSystemLogRoles()

  const categoryItems = [
    { value: ALL, label: 'Alle Typen' },
    ...Object.entries(CATEGORIES).map(([value, label]) => ({ value, label })),
  ]
  const roleItems = [
    { value: ALL, label: 'Alle Rollen', pinned: true },
    ...(roles.data ?? []).map((r) => ({ value: r.id, label: name(r) })),
  ]
  const entries = log.data?.pages.flatMap((page) => page.entries) ?? []
  const filtered = filter.category !== null || filter.member !== null || filter.roleId !== null

  function searchMember(event: FormEvent) {
    event.preventDefault()
    setMember(memberInput)
  }

  function toggle(id: number) {
    setExpanded((current) => {
      const next = new Set(current)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Automatische Aktionen</CardTitle>
        <CardDescription>
          Sync-Läufe mit den Rollenänderungen des Bots, Verknüpfungen und Fehler, neueste zuerst. Einträge werden nach
          30 Tagen gelöscht.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field>
            <FieldLabel htmlFor="system-log-category">Typ</FieldLabel>
            <Select items={categoryItems} value={category} onValueChange={(value) => setCategory(value ?? ALL)}>
              <SelectTrigger id="system-log-category" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {categoryItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel htmlFor="system-log-role">Rolle</FieldLabel>
            <SearchableSelect
              id="system-log-role"
              className="w-full"
              items={roleItems}
              value={roleId}
              onValueChange={(value) => setRoleId(value ?? ALL)}
            />
          </Field>
          <form onSubmit={searchMember}>
            <Field>
              <FieldLabel htmlFor="system-log-member">Mitglied</FieldLabel>
              <div className="flex gap-2">
                <Input
                  id="system-log-member"
                  placeholder="Name oder Discord-ID"
                  value={memberInput}
                  onChange={(event) => setMemberInput(event.target.value)}
                />
                <Button type="submit" variant="outline">
                  Suchen
                </Button>
              </div>
            </Field>
          </form>
        </div>
        {log.isPending ? (
          <LoadingState label="System-Log wird geladen …" />
        ) : log.isError && entries.length === 0 ? (
          <ErrorState message={ERROR_MESSAGE} onRetry={() => log.refetch()} />
        ) : entries.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {filtered ? 'Keine Einträge für diesen Filter.' : 'Noch keine Einträge.'}
          </p>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-0">
                    <span className="sr-only">Details</span>
                  </TableHead>
                  <TableHead>Zeit</TableHead>
                  <TableHead>Typ</TableHead>
                  <TableHead>Ereignis</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.map((entry) => (
                  <Fragment key={entry.id}>
                    <EntryRow entry={entry} expanded={expanded.has(entry.id)} onToggle={() => toggle(entry.id)} />
                    {expanded.has(entry.id) && (
                      <TableRow id={`system-log-items-${entry.id}`}>
                        <TableCell colSpan={4} className="bg-muted/30">
                          <Items entryId={entry.id} filter={filter} />
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                ))}
              </TableBody>
            </Table>
            {log.isFetchNextPageError && <ErrorState message={ERROR_MESSAGE} onRetry={() => log.fetchNextPage()} />}
            {log.hasNextPage && !log.isFetchNextPageError && (
              <Button
                variant="outline"
                className="self-center"
                disabled={log.isFetchingNextPage}
                onClick={() => log.fetchNextPage()}
              >
                {log.isFetchingNextPage ? 'Wird geladen …' : 'Mehr laden'}
              </Button>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function EntryRow({ entry, expanded, onToggle }: { entry: SystemLogEntry; expanded: boolean; onToggle: () => void }) {
  const badge = CATEGORY_BADGES[entry.category]
  const status = entry.sync && STATUS[entry.sync.status]
  return (
    <TableRow>
      <TableCell>
        {entry.hasItems && (
          <Button
            variant="ghost"
            size="icon-sm"
            aria-expanded={expanded}
            aria-controls={`system-log-items-${entry.id}`}
            aria-label={expanded ? 'Details ausblenden' : 'Details anzeigen'}
            onClick={onToggle}
          >
            {expanded ? <ChevronDownIcon /> : <ChevronRightIcon />}
          </Button>
        )}
      </TableCell>
      <TableCell className="text-muted-foreground">{formatTime(entry.occurredAt)}</TableCell>
      <TableCell>
        <Badge variant={badge.variant}>{badge.label}</Badge>
      </TableCell>
      <TableCell className="whitespace-normal">
        <span className="flex flex-wrap items-center gap-2">
          {describe(entry)}
          {status && <Badge variant={status.variant}>{status.label}</Badge>}
        </span>
      </TableCell>
    </TableRow>
  )
}

function Items({ entryId, filter }: { entryId: number; filter: SystemLogFilter }) {
  const items = useSystemLogItems(entryId, { member: filter.member, roleId: filter.roleId })
  if (items.isPending) return <LoadingState label="Details werden geladen …" />
  if (items.isError) return <ErrorState message={ERROR_MESSAGE} onRetry={() => items.refetch()} />
  if (items.data.length === 0) return <p className="text-sm text-muted-foreground">Keine passenden Änderungen.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Mitglied</TableHead>
          <TableHead>Rolle</TableHead>
          <TableHead>Ergebnis</TableHead>
          <TableHead>Grund</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.data.map((item) => (
          <TableRow key={item.id}>
            <TableCell>{name(item.member)}</TableCell>
            <TableCell>{item.role ? name(item.role) : '–'}</TableCell>
            <TableCell>{OUTCOMES[item.outcome]}</TableCell>
            <TableCell className="whitespace-normal text-muted-foreground">{item.reason ?? ''}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
