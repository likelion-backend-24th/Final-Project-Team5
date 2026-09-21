import { useEffect, useReducer, useRef, useState } from 'react'

//이 시간 안에 다시 요청하면 네트워크를 타지 않고 캐시를 그대로 쓴다. 남은 수량처럼 자주 바뀌는 값이
//있어 짧게 잡는다 — 지나면 캐시를 먼저 보여주되 뒤에서 다시 받아 갱신한다(stale-while-revalidate).
export const FRESH_MS = 10_000
const MAX_ENTRIES = 60

//key -> { data, fetchedAt }. 모듈 전역이라 페이지를 오가도 유지되고, 새로고침하면 사라진다.
const entries = new Map()
//같은 key로 진행 중인 요청 — 홈 카드 pointerdown 프리페치와 상세 진입이 겹쳐도 한 번만 나간다.
const inflight = new Map()

function readEntry(key) {
  return entries.get(key)
}

function store(key, data) {
  entries.delete(key)
  entries.set(key, { data, fetchedAt: Date.now() })
  while (entries.size > MAX_ENTRIES) {
    entries.delete(entries.keys().next().value)
  }
}

function isFresh(entry) {
  return Boolean(entry) && Date.now() - entry.fetchedAt < FRESH_MS
}

//응답은 항상 { success, data } 봉투이므로 data만 꺼내 캐시한다. 실패는 캐시하지 않는다.
function fetchAndStore(key, fetcher) {
  const existing = inflight.get(key)
  if (existing) return existing

  const promise = fetcher()
    .then((response) => {
      const data = response.data.data
      store(key, data)
      return data
    })
    .finally(() => {
      inflight.delete(key)
    })
  inflight.set(key, promise)
  return promise
}

/** 캐시가 신선하지 않을 때만 미리 받아둔다. 실패는 조용히 무시한다(실제 진입 시 다시 시도된다). */
export function prefetchQuery(key, fetcher) {
  if (isFresh(readEntry(key))) return
  fetchAndStore(key, fetcher).catch(() => {})
}

/** key가 prefix로 시작하는 캐시를 지운다. 예매·취소처럼 잔여 수량이 바뀌는 동작 뒤에 부른다. */
export function invalidateQueries(prefix) {
  for (const key of [...entries.keys()]) {
    if (key.startsWith(prefix)) entries.delete(key)
  }
}

export function clearQueryCache() {
  entries.clear()
  inflight.clear()
}

/**
 * GET 응답을 캐시해서 재방문 시 즉시 그리는 훅.
 * - 캐시가 있으면 isLoading 없이 바로 data를 돌려주고, 10초가 지났으면 뒤에서 다시 받아 갱신한다.
 * - 캐시가 없을 때만 isLoading=true (스켈레톤을 보여줄 구간).
 * - 이미 data가 있는데 갱신만 실패하면 에러를 화면에 올리지 않고 캐시된 data를 그대로 둔다.
 * fetcher는 매 렌더 새로 만들어져도 되도록 ref로 들고 있는다 — key가 같으면 같은 요청이다.
 */
export function useCachedQuery(key, fetcher, { enabled = true } = {}) {
  const [, rerender] = useReducer((count) => count + 1, 0)
  const [failure, setFailure] = useState(null)
  const fetcherRef = useRef(fetcher)
  useEffect(() => {
    fetcherRef.current = fetcher
  })

  useEffect(() => {
    if (!enabled || isFresh(readEntry(key))) return undefined
    let cancelled = false
    fetchAndStore(key, () => fetcherRef.current())
      .then(() => {
        if (!cancelled) rerender()
      })
      .catch((error) => {
        if (!cancelled) setFailure({ key, error })
      })
    return () => {
      cancelled = true
    }
  }, [key, enabled])

  const entry = readEntry(key)
  const data = entry?.data
  const error = data === undefined && failure?.key === key ? failure.error : null
  return { data, error, isLoading: enabled && data === undefined && !error }
}
