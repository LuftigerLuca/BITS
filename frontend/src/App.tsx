import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

function App() {
  return (
    <main className="flex min-h-svh items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>BITS Dashboard</CardTitle>
          <CardDescription>
            Verwaltung des BITS-Bots für den deutschen WarEra-Discord.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Die Anmeldung mit Discord folgt in einem späteren Schritt.
        </CardContent>
        <CardFooter>
          <Button className="w-full" disabled>
            Mit Discord anmelden
          </Button>
        </CardFooter>
      </Card>
    </main>
  )
}

export default App
