import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RefreshButton } from './RefreshButton'

describe('RefreshButton', () => {
  it('loads the data anew on a click', async () => {
    const refetch = vi.fn()
    render(<RefreshButton query={{ isFetching: false, refetch }} />)

    await userEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }))

    expect(refetch).toHaveBeenCalledOnce()
  })

  it('cannot be clicked while the data is loading', () => {
    render(<RefreshButton query={{ isFetching: true, refetch: vi.fn() }} />)

    expect(screen.getByRole('button', { name: 'Aktualisieren' })).toBeDisabled()
  })
})
