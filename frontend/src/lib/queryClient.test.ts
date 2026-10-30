import { focusManager, QueryObserver } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { createQueryClient } from './queryClient'

async function fetchesAfterFocus(refetchOnWindowFocus?: boolean) {
  const queryFn = vi.fn().mockResolvedValue('data')
  const queryClient = createQueryClient()
  queryClient.mount()
  const observer = new QueryObserver(queryClient, {
    queryKey: ['data'],
    queryFn,
    staleTime: 0,
    ...(refetchOnWindowFocus === undefined ? {} : { refetchOnWindowFocus }),
  })
  const unsubscribe = observer.subscribe(() => {})
  await vi.waitFor(() => expect(queryFn).toHaveBeenCalledTimes(1))
  await vi.waitFor(() => expect(observer.getCurrentResult().isFetching).toBe(false))

  focusManager.setFocused(false)
  focusManager.setFocused(true)
  await new Promise((resolve) => setTimeout(resolve))
  focusManager.setFocused(undefined)
  unsubscribe()
  queryClient.unmount()
  return queryFn.mock.calls.length - 1
}

describe('createQueryClient', () => {
  it('loads nothing anew when the window gets focus again', async () => {
    expect(await fetchesAfterFocus()).toBe(0)
  })

  it('lets a query that reads the database only switch it back on', async () => {
    expect(await fetchesAfterFocus(true)).toBe(1)
  })
})
