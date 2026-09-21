import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { prefetchQuery } from './queryCache'
import { cancelReservation, createReservation } from './reservationApi'

vi.mock('./client', () => ({ default: { post: vi.fn(), patch: vi.fn(), get: vi.fn() } }))

const envelope = (data) => ({ data: { success: true, data } })

async function warm(key) {
  const fetcher = vi.fn().mockResolvedValue(envelope('cached'))
  prefetchQuery(key, fetcher)
  await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
  //캐시가 채워졌는지(신선한지) 확인 — 다시 prefetch해도 fetcher가 안 불린다
  prefetchQuery(key, fetcher)
  expect(fetcher).toHaveBeenCalledTimes(1)
  return fetcher
}

describe('reservationApi cache invalidation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('clears cached festival data after a reservation is created (remaining stock changed)', async () => {
    apiClient.post.mockResolvedValue({ data: { success: true } })
    const fetcher = await warm('festival:1')

    const response = await createReservation({ festivalId: 1, ticketTypeId: 2, quantity: 1 })
    expect(response.data.success).toBe(true)

    prefetchQuery('festival:1', fetcher)
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('clears cached festival data after a reservation is cancelled (stock restored)', async () => {
    apiClient.patch.mockResolvedValue({ data: { success: true } })
    const fetcher = await warm('festivals:{"page":0}')

    await cancelReservation(9)

    prefetchQuery('festivals:{"page":0}', fetcher)
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('keeps the cache when the reservation request fails', async () => {
    apiClient.post.mockRejectedValue(new Error('409'))
    const fetcher = await warm('festival:1')

    await expect(createReservation({ festivalId: 1, ticketTypeId: 2, quantity: 1 })).rejects.toThrow('409')

    prefetchQuery('festival:1', fetcher)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
