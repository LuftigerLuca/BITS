import { Link } from 'react-router'
import { Card, CardContent } from '@/components/ui/card'
import { MANUAL_PAGES, manualPath } from './manual'

/** Start page of the manual: one way in for each group of readers. */
export function ManualOverview() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-4 py-12">
      <header className="flex flex-col gap-3">
        <h1 className="font-heading text-3xl font-bold">Handbuch</h1>
        <p className="text-lg text-muted-foreground">
          Hier erfährst du, was BITS kann und wie du es in Discord und im Dashboard benutzt.
        </p>
      </header>

      <section className="flex flex-col gap-4">
        <h2 className="font-heading text-xl font-semibold">Wo möchtest du anfangen?</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {MANUAL_PAGES.map((page) => {
            const Icon = page.icon
            return (
              <Link
                key={page.slug}
                to={manualPath(page)}
                className="rounded-xl focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
              >
                <Card className="h-full transition-colors hover:bg-muted/50">
                  <CardContent className="flex flex-col gap-2">
                    <Icon className="size-5 text-primary" aria-hidden />
                    <h3 className="font-semibold">{page.title}</h3>
                    <p className="text-sm text-muted-foreground">{page.summary}</p>
                  </CardContent>
                </Card>
              </Link>
            )
          })}
        </div>
      </section>

      <p className="text-sm text-muted-foreground">
        Neu bei BITS? Dann fang mit „Mitglieder“ an.
      </p>
    </div>
  )
}
