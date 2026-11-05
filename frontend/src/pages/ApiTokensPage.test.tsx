import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ApiTokens, CreatedApiToken } from '@/features/api-tokens/api'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = {
  name: 'Luca',
  avatarUrl: null,
  role: 'ADMIN',
  wareraUsername: null,
  discordUser: true,
}

const scopes = [
  {
    id: 'accounts:list',
    path: '/external/v1/accounts',
    summary: 'Alle verknüpften Accounts seitenweise abrufen',
  },
  {
    id: 'dsks:list',
    path: '/external/v1/dsks',
    summary: 'DSKs mit ihren Militäreinheiten abrufen',
  },
]

const tokens: ApiTokens = {
  tokens: [
    {
      id: 2,
      name: 'Statistik-Bot',
      hint: 'bits_AbCd',
      scopes: ['dsks:list'],
      createdAt: Date.UTC(2026, 2, 1, 12),
      createdBy: 'Luca',
      expiresAt: Date.UTC(2026, 5, 1, 22),
      lastUsedAt: null,
      status: 'ACTIVE',
    },
    {
      id: 1,
      name: 'Altes Tool',
      hint: 'bits_XyZ0',
      scopes: ['accounts:list'],
      createdAt: Date.UTC(2025, 2, 1, 12),
      createdBy: 'Luca',
      expiresAt: Date.UTC(2026, 1, 1, 23),
      lastUsedAt: Date.UTC(2026, 0, 31, 9),
      status: 'EXPIRED',
    },
  ],
  scopes,
}

function stub(routes: Record<string, (init?: RequestInit) => Response> = {}, data: ApiTokens = tokens) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/api-tokens': () => json(data),
    ...routes,
  })
}

describe('API tokens', () => {
  it('shows the tokens with their status and scopes, but never a secret', async () => {
    stub()
    renderApp('/admin/api-tokens')

    const token = await screen.findByRole('region', {
      name: 'Token Statistik-Bot',
    })
    expect(within(token).getByText('Aktiv')).toBeInTheDocument()
    expect(within(token).getByText(/bits_AbCd…/)).toBeInTheDocument()
    expect(within(token).getByText(/nie benutzt/)).toBeInTheDocument()
    expect(within(token).getByText('dsks:list')).toBeInTheDocument()
    const expired = screen.getByRole('region', { name: 'Token Altes Tool' })
    expect(within(expired).getByText('Abgelaufen')).toBeInTheDocument()
    expect(within(expired).getByRole('button', { name: 'Altes Tool entfernen' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'API-Dokumentation' })).toHaveAttribute('href', '/external/docs')
    expect(screen.getByRole('link', { name: 'API-Tokens' })).toHaveAttribute('aria-current', 'page')
  })

  it('shows an empty state without tokens', async () => {
    stub({}, { tokens: [], scopes })
    renderApp('/admin/api-tokens')

    expect(await screen.findByText('Noch kein Token angelegt')).toBeInTheDocument()
  })

  it('creates a token and shows its secret once', async () => {
    const created: unknown[] = []
    const answer: CreatedApiToken = {
      token: { ...tokens.tokens[0], id: 3, name: 'Neu' },
      secret: 'bits_secret-value',
    }
    stub({
      'POST /web/admin/api-tokens': (init) => {
        created.push(JSON.parse(String(init?.body)))
        return json(answer, 201)
      },
    })
    renderApp('/admin/api-tokens')

    const button = await screen.findByRole('button', { name: 'Token anlegen' })
    await userEvent.type(screen.getByLabelText('Name'), ' Neu ')
    expect(button).toBeDisabled()
    // Clicked in the other order, sent in the order of the documentation.
    await userEvent.click(
      screen.getByRole('checkbox', {
        name: 'DSKs mit ihren Militäreinheiten abrufen',
      }),
    )
    await userEvent.click(
      screen.getByRole('checkbox', {
        name: 'Alle verknüpften Accounts seitenweise abrufen',
      }),
    )
    const expiresOn = screen.getByLabelText('Gültig bis')
    await userEvent.clear(expiresOn)
    await userEvent.type(expiresOn, '2026-12-31')
    await userEvent.click(button)

    await expect
      .poll(() => created)
      .toEqual([
        {
          name: 'Neu',
          expiresOn: '2026-12-31',
          scopes: ['accounts:list', 'dsks:list'],
        },
      ])
    await expectToast('success', 'Token angelegt')
    expect(await screen.findByLabelText('Token')).toHaveValue('bits_secret-value')

    await userEvent.click(screen.getByRole('button', { name: 'Fertig' }))
    expect(screen.queryByDisplayValue('bits_secret-value')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
  })

  it('tells why a token was not created', async () => {
    stub({ 'POST /web/admin/api-tokens': () => status(400) })
    renderApp('/admin/api-tokens')

    await userEvent.type(await screen.findByLabelText('Name'), 'Neu')
    await userEvent.click(
      screen.getByRole('checkbox', {
        name: 'DSKs mit ihren Militäreinheiten abrufen',
      }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Token anlegen' }))

    await expectToast('error', 'Prüfe Name, Ablaufdatum (höchstens ein Jahr) und Scopes.')
  })

  it('revokes a token after asking', async () => {
    const revoked: string[] = []
    stub({
      'DELETE /web/admin/api-tokens/2': () => {
        revoked.push('2')
        return status(204)
      },
    })
    renderApp('/admin/api-tokens')

    await userEvent.click(await screen.findByRole('button', { name: 'Statistik-Bot widerrufen' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/funktioniert ab sofort nicht mehr/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Widerrufen' }))

    await expect.poll(() => revoked).toEqual(['2'])
    await expectToast('success', 'Token widerrufen')
  })

  it('has no accessibility violations axe can find, also with a token shown and a dialog open', async () => {
    stub({
      'POST /web/admin/api-tokens': () =>
        json(
          {
            token: { ...tokens.tokens[0], id: 3 },
            secret: 'bits_secret-value',
          } satisfies CreatedApiToken,
          201,
        ),
    })
    renderApp('/admin/api-tokens')
    await screen.findByRole('region', { name: 'Token Statistik-Bot' })
    await expectNoAxeViolations()

    await userEvent.type(screen.getByLabelText('Name'), 'Neu')
    await userEvent.click(
      screen.getByRole('checkbox', {
        name: 'DSKs mit ihren Militäreinheiten abrufen',
      }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Token anlegen' }))
    await screen.findByLabelText('Token')
    await expectNoAxeViolations()

    await userEvent.click(screen.getByRole('button', { name: 'Altes Tool entfernen' }))
    await screen.findByRole('alertdialog')
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
