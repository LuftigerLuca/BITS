import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { useAuth } from '@/auth/context'
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
import { ApiError, DISCORD_LOGIN_URL, adminLogin, getAuthOptions } from '@/lib/api'

/** Error codes the backend appends after a failed Discord login. */
const DISCORD_ERRORS: Record<string, string> = {
  not_member: 'Kein Mitglied des Discord-Servers.',
  discord_unavailable: 'Discord ist gerade nicht erreichbar. Bitte versuche es später erneut.',
}

export function LoginPage() {
  const { user, sessionExpired } = useAuth()
  const [searchParams] = useSearchParams()
  const [adminLoginEnabled, setAdminLoginEnabled] = useState(false)

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

  return (
    <main className="flex min-h-svh items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>BITS Dashboard</CardTitle>
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
          <Button className="w-full" onClick={() => window.location.assign(DISCORD_LOGIN_URL)}>
            Mit Discord anmelden
          </Button>
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
