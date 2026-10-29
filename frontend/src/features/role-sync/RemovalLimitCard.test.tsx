import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { createQueryClient } from '@/lib/queryClient'
import { json, status, stubFetch } from '@/test/fetch'
import { expectToast } from '@/test/toast'
import { RemovalLimitCard } from './RemovalLimitCard'

function renderCard(enabled: boolean, onSave: (body: unknown) => Response = () => status(204)) {
  let saved = enabled
  const fetch = stubFetch({
    'GET /web/admin/role-sync/removal-limit': () => json({ enabled: saved }),
    'PUT /web/admin/role-sync/removal-limit': (init) => {
      const body = JSON.parse(String(init?.body)) as { enabled: boolean }
      const response = onSave(body)
      if (response.ok) saved = body.enabled
      return response
    },
  })
  const queryClient = createQueryClient()
  queryClient.setDefaultOptions({ queries: { retry: false } })
  render(
    <QueryClientProvider client={queryClient}>
      <RemovalLimitCard />
      <Toaster />
    </QueryClientProvider>,
  )
  return fetch
}

describe('removal limit', () => {
  it('switches the limit off and warns while it is off', async () => {
    let body: unknown
    renderCard(true, (request) => {
      body = request
      return status(204)
    })

    const toggle = await screen.findByRole('switch', { name: 'Schutz aktiv' })
    expect(toggle).toBeChecked()
    expect(screen.queryByText(/Der Schutz ist aus/)).not.toBeInTheDocument()

    await userEvent.click(toggle)

    expect(body).toEqual({ enabled: false })
    await expectToast('success', 'Schutz vor Massenentzug ausgeschaltet')
    expect(await screen.findByText(/Der Schutz ist aus/)).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Schutz aktiv' })).not.toBeChecked()
  })

  it('switches the limit on again', async () => {
    let body: unknown
    renderCard(false, (request) => {
      body = request
      return status(204)
    })

    await userEvent.click(await screen.findByRole('switch', { name: 'Schutz aktiv' }))

    expect(body).toEqual({ enabled: true })
    await expectToast('success', 'Schutz vor Massenentzug eingeschaltet')
  })

  it('reports a failed change', async () => {
    renderCard(true, () => status(500))

    await userEvent.click(await screen.findByRole('switch', { name: 'Schutz aktiv' }))

    await waitFor(() => expect(document.querySelector('[data-sonner-toast][data-type="error"]')).not.toBeNull())
    expect(screen.getByRole('switch', { name: 'Schutz aktiv' })).toBeChecked()
    expect(screen.queryByText(/Der Schutz ist aus/)).not.toBeInTheDocument()
  })
})
