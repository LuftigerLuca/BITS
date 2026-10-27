import { ErrorState, LoadingState } from '@/components/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Item, ItemActions, ItemContent, ItemDescription, ItemGroup, ItemTitle } from '@/components/ui/item'
import { Switch } from '@/components/ui/switch'
import { FEATURES } from './labels'
import { useFeatures, useSetFeature } from './queries'

/** Admins only: switches each feature on or off; the pages of those that are off disappear from the navigation. */
export function FeaturesCard() {
  const features = useFeatures()
  const setFeature = useSetFeature()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Funktionen</CardTitle>
        <CardDescription>
          Ausgeschaltete Funktionen pausieren: Ihre Befehle antworten in Discord mit einem Hinweis, ihre Seiten
          verschwinden aus dem Dashboard, und nichts wird gelöscht. Der Bot übernimmt eine Änderung innerhalb von 30
          Sekunden.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {features.isPending ? (
          <LoadingState label="Funktionen werden geladen …" />
        ) : features.isError ? (
          <ErrorState message="Die Funktionen konnten nicht geladen werden." onRetry={() => features.refetch()} />
        ) : (
          <ItemGroup>
            {features.data.map(({ feature, enabled }) => {
              const { label, description } = FEATURES[feature]
              const id = `feature-${feature}`
              return (
                <Item key={feature} role="listitem" variant="outline">
                  <ItemContent>
                    <ItemTitle id={`${id}-label`}>{label}</ItemTitle>
                    {/* Unclamped: what switching off keeps must be readable in full, also on a phone. */}
                    <ItemDescription id={`${id}-description`} className="line-clamp-none">
                      {description}
                    </ItemDescription>
                  </ItemContent>
                  <ItemActions>
                    <Switch
                      aria-labelledby={`${id}-label`}
                      aria-describedby={`${id}-description`}
                      checked={enabled}
                      disabled={setFeature.isPending}
                      onCheckedChange={(checked) => setFeature.mutate({ feature, enabled: checked })}
                    />
                  </ItemActions>
                </Item>
              )
            })}
          </ItemGroup>
        )}
      </CardContent>
    </Card>
  )
}
