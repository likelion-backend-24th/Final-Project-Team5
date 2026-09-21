import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { FRESH_MS, invalidateQueries, prefetchQuery, useCachedQuery } from './queryCache'

const envelope = (data) => ({ data: { success: true, data } })

function Probe({ cacheKey = 'festival:1', fetcher }) {
  const { data, error, isLoading } = useCachedQuery(cacheKey, fetcher)
  if (isLoading) return <p>loading</p>
  if (error) return <p>error:{error.message}</p>
  return <p>data:{data}</p>
}

describe('useCachedQuery', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows loading first, then the fetched data', async () => {
    const fetcher = vi.fn().mockResolvedValue(envelope('A'))
    render(<Probe fetcher={fetcher} />)
    expect(screen.getByText('loading')).toBeTruthy()
    expect(await screen.findByText('data:A')).toBeTruthy()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('renders cached data immediately and skips the network while fresh', async () => {
    const fetcher = vi.fn().mockResolvedValue(envelope('A'))
    const first = render(<Probe fetcher={fetcher} />)
    await screen.findByText('data:A')
    first.unmount()

    render(<Probe fetcher={fetcher} />)
    //로딩 없이 첫 렌더부터 캐시 데이터가 보인다
    expect(screen.getByText('data:A')).toBeTruthy()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('shows stale data right away and revalidates in the background once it is no longer fresh', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(envelope('old')).mockResolvedValueOnce(envelope('new'))
    const first = render(<Probe fetcher={fetcher} />)
    await screen.findByText('data:old')
    first.unmount()

    act(() => {
      vi.advanceTimersByTime(FRESH_MS + 1)
    })
    render(<Probe fetcher={fetcher} />)
    expect(screen.getByText('data:old')).toBeTruthy()
    await waitFor(() => expect(screen.getByText('data:new')).toBeTruthy())
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('dedupes concurrent requests for the same key', async () => {
    const fetcher = vi.fn().mockResolvedValue(envelope('A'))
    render(
      <>
        <Probe fetcher={fetcher} />
        <Probe fetcher={fetcher} />
      </>,
    )
    await waitFor(() => expect(screen.getAllByText('data:A')).toHaveLength(2))
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('surfaces an error when there is no cached data, and does not cache failures', async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(envelope('A'))
    const first = render(<Probe fetcher={fetcher} />)
    expect(await screen.findByText('error:boom')).toBeTruthy()
    first.unmount()

    render(<Probe fetcher={fetcher} />)
    expect(await screen.findByText('data:A')).toBeTruthy()
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('keeps showing cached data when a background refresh fails', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(envelope('old')).mockRejectedValueOnce(new Error('boom'))
    const first = render(<Probe fetcher={fetcher} />)
    await screen.findByText('data:old')
    first.unmount()

    act(() => {
      vi.advanceTimersByTime(FRESH_MS + 1)
    })
    render(<Probe fetcher={fetcher} />)
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
    expect(screen.getByText('data:old')).toBeTruthy()
    expect(screen.queryByText(/error:/)).toBeNull()
  })

  it('refetches after invalidateQueries clears matching keys only', async () => {
    const detail = vi.fn().mockResolvedValue(envelope('D'))
    const other = vi.fn().mockResolvedValue(envelope('O'))
    const a = render(<Probe cacheKey="festival:1" fetcher={detail} />)
    const b = render(<Probe cacheKey="reservations:me" fetcher={other} />)
    await screen.findByText('data:D')
    await screen.findByText('data:O')
    a.unmount()
    b.unmount()

    invalidateQueries('festival')
    render(<Probe cacheKey="festival:1" fetcher={detail} />)
    render(<Probe cacheKey="reservations:me" fetcher={other} />)
    expect(screen.getByText('loading')).toBeTruthy()
    await waitFor(() => expect(detail).toHaveBeenCalledTimes(2))
    expect(other).toHaveBeenCalledTimes(1)
  })
})

describe('prefetchQuery', () => {
  it('warms the cache so the first render already has data, and skips when already fresh', async () => {
    const fetcher = vi.fn().mockResolvedValue(envelope('P'))
    prefetchQuery('festival:9', fetcher)
    prefetchQuery('festival:9', fetcher)
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))

    render(<Probe cacheKey="festival:9" fetcher={fetcher} />)
    await waitFor(() => expect(screen.getByText('data:P')).toBeTruthy())
    expect(fetcher).toHaveBeenCalledTimes(1)
    prefetchQuery('festival:9', fetcher)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('swallows prefetch failures', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('nope'))
    expect(() => prefetchQuery('festival:10', fetcher)).not.toThrow()
    await waitFor(() => expect(fetcher).toHaveBeenCalled())
  })
})
