import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { Label } from './label'
import { matchesSearch, SearchableSelect, type SearchableSelectItem } from './searchable-select'

const ITEMS: SearchableSelectItem[] = [
  { value: 'all', label: 'Alle Länder', pinned: true },
  { value: 'at', label: 'Österreich' },
  { value: 'de', label: 'Germany' },
  { value: 'ch', label: 'Schweiz' },
]

function Field({ initial = null }: { initial?: string | null }) {
  const [value, setValue] = useState<string | null>(initial)
  return (
    <>
      <Label htmlFor="country">Land</Label>
      <SearchableSelect id="country" items={ITEMS} value={value} onValueChange={setValue} placeholder="Land wählen" />
      <output>{value ?? 'nichts'}</output>
    </>
  )
}

const options = () => screen.queryAllByRole('option').map((option) => option.textContent)

describe('matchesSearch', () => {
  it('finds the text anywhere in the label, regardless of case and accents', () => {
    expect(matchesSearch({ value: 'at', label: 'Österreich' }, 'osterr')).toBe(true)
    expect(matchesSearch({ value: 'at', label: 'Österreich' }, 'REICH')).toBe(true)
    expect(matchesSearch({ value: 'fr', label: 'Côte d’Ivoire' }, 'cote')).toBe(true)
    expect(matchesSearch({ value: 'de', label: 'Germany' }, 'austria')).toBe(false)
  })

  it('keeps pinned items for any text', () => {
    expect(matchesSearch({ value: 'all', label: 'Alle', pinned: true }, 'xyz')).toBe(true)
  })

  it('ignores the ID and surrounding spaces', () => {
    expect(matchesSearch({ value: 'de', label: 'Germany' }, 'de')).toBe(false)
    expect(matchesSearch({ value: 'de', label: 'Germany' }, '  germ ')).toBe(true)
  })
})

describe('SearchableSelect', () => {
  it('opens with a focused search field that filters the list and keeps the pinned item', async () => {
    render(<Field />)
    expect(screen.getByLabelText('Land')).toHaveTextContent('Land wählen')

    await userEvent.click(screen.getByLabelText('Land'))
    const search = await screen.findByRole('combobox', { name: 'Suchen' })
    expect(search).toHaveFocus()
    expect(options()).toEqual(['Alle Länder', 'Österreich', 'Germany', 'Schweiz'])

    await userEvent.type(search, 'osterr')
    expect(options()).toEqual(['Alle Länder', 'Österreich'])
    await userEvent.click(screen.getByRole('option', { name: 'Österreich' }))
    expect(screen.getByText('at')).toBeInTheDocument()
    expect(screen.getByLabelText('Land')).toHaveTextContent('Österreich')
  })

  it('keeps only the pinned item when nothing else matches', async () => {
    render(<Field />)
    await userEvent.click(screen.getByLabelText('Land'))
    await userEvent.type(await screen.findByRole('combobox', { name: 'Suchen' }), 'xyz')

    expect(options()).toEqual(['Alle Länder'])
    expect(screen.queryByText('Keine Treffer')).not.toBeInTheDocument()
  })

  it('works with the keyboard alone and clears the search text on closing', async () => {
    render(<Field initial="de" />)
    screen.getByLabelText('Land').focus()

    await userEvent.keyboard('{Enter}')
    const search = await screen.findByRole('combobox', { name: 'Suchen' })
    await userEvent.type(search, 'sch')
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}')
    expect(screen.getByLabelText('Land')).toHaveTextContent('Schweiz')

    await userEvent.click(screen.getByLabelText('Land'))
    await userEvent.type(await screen.findByRole('combobox', { name: 'Suchen' }), 'ger')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('combobox', { name: 'Suchen' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Land')).toHaveTextContent('Schweiz')

    await userEvent.click(screen.getByLabelText('Land'))
    expect(await screen.findByRole('combobox', { name: 'Suchen' })).toHaveValue('')
    expect(options()).toHaveLength(4)
  })
})

describe('SearchableSelect without matches', () => {
  it('shows „Keine Treffer“', async () => {
    render(
      <SearchableSelect
        id="role"
        items={[{ value: 'r1', label: 'Bürger' }]}
        value={null}
        onValueChange={() => {}}
        placeholder="Rolle wählen"
      />,
    )
    await userEvent.click(screen.getByText('Rolle wählen'))
    await userEvent.type(await screen.findByRole('combobox', { name: 'Suchen' }), 'xyz')

    expect(options()).toEqual([])
    expect(screen.getByText('Keine Treffer')).toBeVisible()
  })
})

describe('SearchableSelect list', () => {
  it('is named after the label of its field', async () => {
    render(<Field />)

    await userEvent.click(screen.getByLabelText('Land'))

    expect(await screen.findByRole('dialog', { name: 'Land' })).toBeInTheDocument()
  })
})
