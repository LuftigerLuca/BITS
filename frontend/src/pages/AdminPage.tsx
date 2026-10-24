import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function AdminPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Verwaltung</CardTitle>
        <CardDescription>Nur für Administratoren sichtbar.</CardDescription>
      </CardHeader>
      <CardContent className="text-sm text-muted-foreground">
        Hier entstehen die Verwaltungsfunktionen des Bots.
      </CardContent>
    </Card>
  )
}
