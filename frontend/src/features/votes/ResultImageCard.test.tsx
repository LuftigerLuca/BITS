import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me, PartyColors, ResultSettings } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }
const noCountry: PartyColors = { countryName: null, parties: [], noPartyColor: null }

function stub(settings: () => ResultSettings, routes: Record<string, (init?: RequestInit) => Response> = {}) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/role-sync/countries': () => json([]),
    'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    'GET /web/admin/bot-presence': () => json({}),
    'GET /web/votes/options': () => json({ roles: [], channels: [] }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: [] }),
    'GET /web/admin/votes/result-settings': () => json(settings()),
    'GET /web/admin/votes/result-settings/party-colors': () => json(noCountry),
    ...routes,
  })
}

async function card() {
  return (await screen.findByText('Bild der Auswertung')).closest('[data-slot=card]') as HTMLElement
}

function png(size: number) {
  return new File([new Uint8Array(size)], 'adler.png', { type: 'image/png' })
}

describe('background of the result image', () => {
  it('uploads a picture, shows it in the preview and resets it to the eagle', async () => {
    let custom = false
    let uploaded: BodyInit | null | undefined
    stub(() => ({ customBackground: custom, updatedAt: custom ? 1 : null }), {
      'PUT /web/admin/votes/result-settings/background': (init) => {
        uploaded = init?.body
        custom = true
        return status(204)
      },
      'DELETE /web/admin/votes/result-settings/background': () => {
        custom = false
        return status(204)
      },
    })
    renderApp('/admin')

    const result = within(await card())
    expect(await result.findByText('Hintergrund: BITS-Adler')).toBeInTheDocument()
    expect(result.getByRole('button', { name: 'Auf Adler zurücksetzen' })).toBeDisabled()
    const preview = result.getByAltText('Vorschau des Ergebnisbilds mit Beispieldaten') as HTMLImageElement
    const before = preview.src

    const file = png(100)
    await userEvent.upload(result.getByLabelText('Hintergrundbild'), file)

    expect(await result.findByText('Hintergrund: eigenes Bild')).toBeInTheDocument()
    await expectToast('success', 'Hintergrund gespeichert')
    expect(uploaded).toBe(file)
    // A new URL, so the browser loads the preview again.
    expect(preview.src).not.toBe(before)

    await userEvent.click(result.getByRole('button', { name: 'Auf Adler zurücksetzen' }))
    expect(await result.findByText('Hintergrund: BITS-Adler')).toBeInTheDocument()
    await expectToast('success', 'Auf Adler zurückgesetzt')
  })

  it('rejects files that are too large before sending them', async () => {
    const fetch = stub(() => ({ customBackground: false, updatedAt: null }))
    renderApp('/admin')

    const result = within(await card())
    await userEvent.upload(await result.findByLabelText('Hintergrundbild'), png(5 * 1024 * 1024 + 1))

    await expectToast('error', 'Das Bild ist größer als 5 MB.')
    expect(fetch.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false)
  })

  it('says when the backend cannot read the file', async () => {
    stub(() => ({ customBackground: false, updatedAt: null }), {
      'PUT /web/admin/votes/result-settings/background': () => status(400),
    })
    renderApp('/admin')

    const result = within(await card())
    await userEvent.upload(await result.findByLabelText('Hintergrundbild'), png(10))

    await expectToast('error', 'Die Datei ist kein PNG- oder JPEG-Bild, das BITS lesen kann.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
