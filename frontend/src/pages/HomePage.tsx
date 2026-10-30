import { useAuth } from '@/auth/context'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { OverviewTiles } from '@/features/overview/OverviewTiles'
import { useDocumentTitle } from '@/hooks/use-document-title'

export function HomePage() {
  const { user } = useAuth()
  useDocumentTitle('Start')
  if (!user) return null

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader className="flex flex-row items-center gap-4">
          <Avatar size="lg">
            {user.avatarUrl && <AvatarImage src={user.avatarUrl} alt="" />}
            <AvatarFallback>{user.name.slice(0, 1).toUpperCase()}</AvatarFallback>
          </Avatar>
          <div className="flex flex-col gap-1">
            <CardTitle as="h1" tabIndex={-1} className="outline-none">
              Willkommen, {user.name}
            </CardTitle>
            <CardDescription>
              <Badge variant={user.role === 'ADMIN' ? 'default' : 'secondary'}>
                {user.role === 'ADMIN' ? 'Administrator' : 'Mitglied'}
              </Badge>
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent className="text-sm">
          <span className="text-muted-foreground">WarEra-Account: </span>
          {user.wareraUsername ?? (
            <span className="text-muted-foreground">
              nicht verknüpft. Mit <code>/verify</code> im Discord kannst du ihn verknüpfen.
            </span>
          )}
        </CardContent>
      </Card>
      {user.role === 'ADMIN' && <OverviewTiles />}
    </div>
  )
}
