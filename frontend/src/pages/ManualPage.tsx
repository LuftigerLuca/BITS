import { Link, Navigate, useParams } from 'react-router'
import { useAuth } from '@/auth/context'
import { ManualArticle } from '@/features/manual/ManualArticle'
import { ManualOverview } from '@/features/manual/ManualOverview'
import { MANUAL_PATH, manualPage } from '@/features/manual/manual'
import { useDocumentTitle } from '@/hooks/use-document-title'

/**
 * The user manual, open without a login: members use most of BITS in Discord and may never sign in. Without a
 * slug it shows the overview, with one that page.
 */
export function ManualPage() {
  const { slug } = useParams()
  const { user } = useAuth()
  const page = manualPage(slug)

  useDocumentTitle(...(page ? [page.title, 'Handbuch'] : ['Handbuch']))

  if (slug !== undefined && !page) return <Navigate to={MANUAL_PATH} replace />

  return (
    <div className="min-h-svh">
      {/* Stays in view, so the way back to the dashboard is always at hand; the contents beside the text sit below it. */}
      <header className="sticky top-0 z-10 border-b bg-background">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <Link to={MANUAL_PATH} className="font-heading font-semibold">
            BITS Handbuch
          </Link>
          <Link to={user ? '/' : '/login'} className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
            {user ? 'Zum Dashboard' : 'Anmelden'}
          </Link>
        </div>
      </header>
      <main>{page ? <ManualArticle key={page.slug} page={page} /> : <ManualOverview />}</main>
    </div>
  )
}
