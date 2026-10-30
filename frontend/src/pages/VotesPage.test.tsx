import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me, Vote, VoteOptions } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const member: Me = { name: 'Luca', avatarUrl: null, role: 'USER', wareraUsername: 'luca', discordUser: true }

const options: VoteOptions = {
  roles: [
    { id: 'r-congress', name: 'Kongress', color: 0x00ff00 },
    { id: 'r-citizen', name: 'Bürger', color: null },
  ],
  channels: [{ id: 'c-votes', name: 'abstimmungen', category: 'Politik' }],
}

const vote: Vote = {
  id: 1,
  question: 'Krieg erklären?',
  roleName: 'Kongress',
  channelName: 'abstimmungen',
  startedAt: Date.UTC(2026, 9, 20, 12),
  startedBy: 'Luca',
  endsAt: Date.UTC(2026, 9, 22, 12),
  threshold: null,
  endedByThreshold: false,
  closedAt: null,
  closedBy: null,
  status: 'OPEN',
  voted: 3,
  eligible: 7,
  resultText: null,
}

const evaluated: Vote = {
  ...vote,
  id: 2,
  question: 'Steuern senken?',
  closedAt: Date.UTC(2026, 9, 21, 12),
  closedBy: 'Mia',
  status: 'EVALUATED',
  voted: null,
  resultText: 'Ergebnis: Steuern senken?\nGesamt: 2 Ja · 0 Nein · 0 Enthaltung · 2 von 2 haben abgestimmt (100 %)',
}

function stubVotes(me: Me, access = { canManage: true }, routes = {}) {
  return stubFetch({
    'GET /web/me': () => json(me),
    'GET /web/votes/access': () => json(access),
    'GET /web/votes': () => json({ open: [vote], closed: [evaluated] }),
    'GET /web/votes/options': () => json(options),
    ...routes,
  })
}

async function choose(label: string, option: string) {
  await userEvent.click(screen.getByLabelText(label))
  await userEvent.click(await screen.findByRole('option', { name: option }))
}

describe('votes page', () => {
  it('is linked from the sidebar for members who may manage votes, with the open votes and the archive', async () => {
    stubVotes(member)
    const router = renderApp('/')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(await within(navigation).findByRole('link', { name: 'Abstimmungen' }))

    expect(await screen.findByRole('heading', { name: 'Abstimmungen' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/votes')
    expect(await screen.findByText('Krieg erklären?')).toBeInTheDocument()
    expect(screen.getByText('3 von 7 haben abgestimmt')).toBeInTheDocument()

    const archived = screen.getByRole('article', { name: 'Steuern senken?' })
    expect(within(archived).getByText(/beendet von Mia/)).toBeInTheDocument()
    await userEvent.click(within(archived).getByText('Ergebnis anzeigen'))
    expect(within(archived).getByText(/2 von 2 haben abgestimmt \(100 %\)/)).toBeInTheDocument()
    expect(within(archived).getByRole('img')).toHaveAttribute('src', '/web/votes/2/result.png')
  })

  it('starts a vote with the chosen role, channel and end', async () => {
    const created: unknown[] = []
    stubVotes(member, undefined, {
      'POST /web/votes': (init?: RequestInit) => {
        created.push(JSON.parse(String(init?.body)))
        return json(vote, 201)
      },
    })
    renderApp('/votes')

    await userEvent.type(await screen.findByLabelText('Frage'), 'Krieg erklären?')
    await choose('Stimmberechtigte Rolle', 'Kongress')
    await choose('Kanal', '#abstimmungen (Politik)')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Ohne Enddatum, bis jemand sie beendet' }))
    await userEvent.click(screen.getByRole('button', { name: 'Abstimmung starten' }))

    const toast = await expectToast('success', 'Abstimmung gestartet')
    expect(toast).toHaveTextContent('Sie erscheint gleich im Kanal.')
    expect(created).toEqual([
      { question: 'Krieg erklären?', roleId: 'r-congress', channelId: 'c-votes', endsAt: null, threshold: null },
    ])
  })

  it('says in a toast that votes are off when the backend refuses to start one', async () => {
    stubVotes(member, undefined, { 'POST /web/votes': () => status(423) })
    renderApp('/votes')

    await userEvent.type(await screen.findByLabelText('Frage'), 'Krieg erklären?')
    await choose('Stimmberechtigte Rolle', 'Kongress')
    await choose('Kanal', '#abstimmungen (Politik)')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Ohne Enddatum, bis jemand sie beendet' }))
    await userEvent.click(screen.getByRole('button', { name: 'Abstimmung starten' }))

    await expectToast('error', 'Abstimmungen sind gerade ausgeschaltet.')
  })

  it('starts a vote that ends at a threshold of 1 to 99 percent for Yes or No', async () => {
    const created: unknown[] = []
    stubVotes(member, undefined, {
      'POST /web/votes': (init?: RequestInit) => {
        created.push(JSON.parse(String(init?.body)))
        return json(vote, 201)
      },
    })
    renderApp('/votes')

    await userEvent.type(await screen.findByLabelText('Frage'), 'Krieg erklären?')
    await choose('Stimmberechtigte Rolle', 'Kongress')
    await choose('Kanal', '#abstimmungen (Politik)')
    const percent = screen.getByLabelText('Automatisch beenden bei mehr als (%)')
    await userEvent.type(percent, '100')
    expect(screen.getByRole('button', { name: 'Abstimmung starten' })).toBeDisabled()

    await userEvent.clear(percent)
    await userEvent.type(percent, '50')
    await choose('mit der Stimme', 'Nein')
    await userEvent.click(screen.getByRole('button', { name: 'Abstimmung starten' }))

    await expect.poll(() => created).toHaveLength(1)
    expect(created[0]).toMatchObject({ threshold: { percent: 50, choice: 'NO' } })
  })

  it('shows the threshold a vote ends at, and that it ended by it', async () => {
    stubVotes(member, undefined, {
      'GET /web/votes': () =>
        json({
          open: [{ ...vote, threshold: { percent: 50, choice: 'YES' } }],
          closed: [{ ...evaluated, closedBy: null, threshold: { percent: 66, choice: 'NO' }, endedByThreshold: true }],
        }),
    })
    renderApp('/votes')

    expect(await screen.findByText('Endet automatisch bei mehr als 50 % mit Ja')).toBeInTheDocument()
    const archived = screen.getByRole('article', { name: 'Steuern senken?' })
    expect(within(archived).getByText(/automatisch beendet bei mehr als 66 % mit Nein/)).toBeInTheDocument()
  })

  it('ends a vote only after asking', async () => {
    const fetch = stubVotes(member, undefined, { 'POST /web/votes/1/end': () => status(204) })
    renderApp('/votes')

    await userEvent.click(await screen.findByRole('button', { name: 'Beenden' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('Krieg erklären?')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abstimmung beenden' }))

    await expect.poll(() => fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(true)
    expect(fetch).toHaveBeenCalledWith('/web/votes/1/end', expect.objectContaining({ method: 'POST' }))
    await expectToast('success', 'Abstimmung beendet')
  })

  it('says in a toast when the bot cannot post in the channel', async () => {
    stubVotes(member, undefined, { 'POST /web/votes': () => status(409) })
    renderApp('/votes')

    await userEvent.type(await screen.findByLabelText('Frage'), 'Krieg erklären?')
    await choose('Stimmberechtigte Rolle', 'Kongress')
    await choose('Kanal', '#abstimmungen (Politik)')
    await userEvent.click(screen.getByRole('button', { name: 'Abstimmung starten' }))

    await expectToast(
      'error',
      'Der Bot kann in diesem Kanal nicht posten. Prüfe seine Rechte dort und versuche es in ein paar Sekunden noch einmal.',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    // Kept, so it can be sent again.
    expect(screen.getByLabelText('Frage')).toHaveValue('Krieg erklären?')
  })

  it('leaves the choice of who may manage votes to the admin page, also for admins', async () => {
    stubVotes({ ...member, role: 'ADMIN' })
    renderApp('/votes')

    expect(await screen.findByText('Krieg erklären?')).toBeInTheDocument()
    expect(screen.queryByText('Wer Abstimmungen verwalten darf')).not.toBeInTheDocument()
  })

  it('keeps members out who may not manage votes', async () => {
    stubVotes(member, { canManage: false })
    const router = renderApp('/votes')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(screen.queryByRole('link', { name: 'Abstimmungen' })).not.toBeInTheDocument()
  })
})
