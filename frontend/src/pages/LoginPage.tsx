import { useEffect, useState, type CSSProperties, type FormEvent } from 'react'
import { BookOpenIcon } from 'lucide-react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import backgroundLarge from '@/assets/login-background-1672.webp'
import backgroundSmall from '@/assets/login-background-1280.webp'
import { useAuth } from '@/auth/context'
import { DiscordIcon } from '@/components/DiscordIcon'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useDocumentTitle } from '@/hooks/use-document-title'
import { ApiError, DISCORD_LOGIN_URL, adminLogin, getAuthOptions } from '@/lib/api'

/** Error codes the backend appends after a failed Discord login. */
const DISCORD_ERRORS: Record<string, string> = {
  not_member: 'Kein Mitglied des Discord-Servers.',
  discord_unavailable: 'Discord ist gerade nicht erreichbar. Bitte versuche es später erneut.',
}

/** Darkens the background image so it stays in the background. */
const BACKGROUND_DIM = 'linear-gradient(rgb(0 0 0 / 55%), rgb(0 0 0 / 55%))'

export function LoginPage() {
  const { user, sessionExpired } = useAuth()
  const [searchParams] = useSearchParams()
  const [adminLoginEnabled, setAdminLoginEnabled] = useState(false)
  useDocumentTitle('Anmelden')

  useEffect(() => {
    getAuthOptions()
      .then((options) => setAdminLoginEnabled(options.adminLogin))
      .catch(() => setAdminLoginEnabled(false))
  }, [])

  if (user) return <Navigate to="/" replace />

  const errorCode = searchParams.get('error')
  const discordError = errorCode
    ? (DISCORD_ERRORS[errorCode] ?? 'Die Anmeldung mit Discord ist fehlgeschlagen.')
    : null

  // Phones load only the small image. On wide landscape screens the card moves to the right, and the image
  // shifts left so the eagle in its centre stays clear of the card, down to 1024 × 768; from 1280 px the card
  // keeps a tenth of the width from the edge, as far as the eagle leaves room down to 1366 × 768.
  return (
    <main
      style={
        {
          '--login-background-small': `${BACKGROUND_DIM}, url(${backgroundSmall})`,
          '--login-background-large': `${BACKGROUND_DIM}, url(${backgroundLarge})`,
        } as CSSProperties
      }
      className="flex min-h-svh items-center justify-center bg-black bg-(image:--login-background-small) bg-cover bg-center bg-no-repeat p-4 lg:bg-(image:--login-background-large) lg:landscape:justify-end lg:landscape:bg-[80%_center] lg:landscape:pr-16 xl:landscape:pr-[10vw]"
    >
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle as="h1">BITS Dashboard</CardTitle>
          <CardDescription>
            Verwaltung des BITS-Bots für den deutschen WarEra-Discord.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {sessionExpired && !discordError && (
            <Alert>
              <AlertDescription>Deine Sitzung ist abgelaufen, bitte melde dich erneut an.</AlertDescription>
            </Alert>
          )}
          {discordError && (
            <Alert variant="destructive">
              <AlertDescription>{discordError}</AlertDescription>
            </Alert>
          )}
          <Button
            variant="discord"
            className="w-full"
            onClick={() => window.location.assign(DISCORD_LOGIN_URL)}
          >
            <DiscordIcon data-icon="inline-start" />
            Mit Discord anmelden
          </Button>
          <Link
            to="/docs"
            className="inline-flex items-center gap-1.5 self-center text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
          >
            <BookOpenIcon className="size-4" aria-hidden />
            Was kann BITS? Zum Handbuch
          </Link>
        </CardContent>
        {adminLoginEnabled && (
          <CardFooter className="flex-col items-stretch gap-4 border-t">
            <AdminLoginForm />
          </CardFooter>
        )}
      </Card>
    </main>
  )
}

function AdminLoginForm() {
  const { refresh } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await adminLogin(username, password)
      await refresh()
      navigate('/', { replace: true })
    } catch (e) {
      setError(adminLoginError(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="flex flex-col gap-3" onSubmit={submit}>
      <p className="text-sm font-medium">Admin-Anmeldung</p>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="username">Benutzername</Label>
        <Input
          id="username"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          required
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="password">Passwort</Label>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </div>
      <Button type="submit" variant="outline" disabled={submitting}>
        Anmelden
      </Button>
    </form>
  )
}

function adminLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Benutzername oder Passwort falsch.'
    // The lockout ends a fixed time after the first attempt, or later if the whole login is locked; the page cannot tell.
    if (error.status === 429) return 'Zu viele Anmeldeversuche. Die Admin-Anmeldung ist vorübergehend gesperrt, versuche es später erneut.'
  }
  return 'Die Anmeldung ist fehlgeschlagen. Bitte versuche es später erneut.'
}
