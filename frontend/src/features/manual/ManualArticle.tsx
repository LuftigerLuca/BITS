import { Fragment, useMemo } from 'react'
import { ArrowLeftIcon, LightbulbIcon } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '@/lib/utils'
import { manualPage, manualPath, type ManualPage } from './manual'
import { useActiveSection } from './useActiveSection'

/**
 * One page of the manual, each topic a section of its own to link to. Beside them the contents stay in view and
 * show which topic is being read.
 */
export function ManualArticle({ page }: { page: ManualPage }) {
  const Icon = page.icon
  const ids = useMemo(() => page.topics.map((topic) => topic.id), [page])
  const active = useActiveSection(ids)

  return (
    <div className="mx-auto grid w-full max-w-5xl gap-10 px-4 py-12 lg:grid-cols-[14rem_minmax(0,1fr)]">
      {/* One column on a phone, so the contents come before the topics there. */}
      <aside className="lg:sticky lg:top-20 lg:max-h-[calc(100svh-6.5rem)] lg:self-start lg:overflow-y-auto">
        <div className="flex flex-col gap-6">
          <Link
            to={manualPath()}
            className="inline-flex w-fit items-center gap-1 text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
          >
            <ArrowLeftIcon className="size-4" aria-hidden />
            Zurück zur Übersicht
          </Link>
          <nav aria-labelledby="manual-contents" className="flex flex-col gap-2">
            <h2 id="manual-contents" className="text-sm font-semibold">
              Auf dieser Seite
            </h2>
            <ul className="flex flex-col gap-0.5 border-l text-sm">
              {page.topics.map((topic) => (
                <li key={topic.id}>
                  <a
                    href={`#${topic.id}`}
                    aria-current={topic.id === active ? 'location' : undefined}
                    className={cn(
                      '-ml-px block border-l-2 border-transparent py-1 pl-3 text-muted-foreground transition-colors hover:text-foreground',
                      topic.id === active && 'border-primary font-medium text-foreground',
                    )}
                  >
                    {topic.title}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </aside>

      <article className="flex max-w-3xl min-w-0 flex-col gap-8">
        <header className="flex flex-col gap-3">
          <h1 className="flex items-center gap-3 font-heading text-3xl font-bold">
            <Icon className="size-7 shrink-0 text-primary" aria-hidden />
            {page.title}
          </h1>
          <p className="text-lg text-muted-foreground">{page.intro}</p>
          {page.buildsOn.length > 0 && (
            <p className="rounded-md border bg-muted/40 p-3 text-sm">
              Diese Seite setzt voraus, was hier steht:{' '}
              {page.buildsOn.map((slug, index) => {
                const base = manualPage(slug)
                if (!base) return null
                return (
                  <Fragment key={slug}>
                    {index > 0 && ', '}
                    <Link to={manualPath(base)} className="font-medium underline underline-offset-4">
                      {base.title}
                    </Link>
                  </Fragment>
                )
              })}
            </p>
          )}
        </header>

        {page.topics.map((topic) => (
          <section key={topic.id} id={topic.id} className="flex scroll-mt-20 flex-col gap-3 border-t pt-6">
            <h2 className="font-heading text-xl font-semibold">{topic.title}</h2>
            <p>{topic.intro}</p>
            {topic.points && (
              <ul className="flex list-disc flex-col gap-1.5 pl-6">
                {topic.points.map((point) => (
                  <li key={point}>{point}</li>
                ))}
              </ul>
            )}
            {topic.steps && (
              <ol className="flex list-decimal flex-col gap-1.5 pl-6 marker:text-muted-foreground">
                {topic.steps.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
            )}
            {topic.tips?.map((tip) => (
              <p key={tip} className="flex gap-2 rounded-md border border-primary/30 bg-primary/5 p-3 text-sm">
                <LightbulbIcon className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
                <span>{tip}</span>
              </p>
            ))}
          </section>
        ))}
      </article>
    </div>
  )
}
