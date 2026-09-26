import { useEffect, useMemo, useState } from 'react'
import { Search, ArrowUpDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { REVIEW_STATUS_META } from '../../data/admin'

export const STATUS_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'PENDING', label: '승인대기' },
  { key: 'APPROVED', label: '승인' },
  { key: 'REJECTED', label: '반려' },
]

export const PAGE_SIZE = 3

export const approveBtn =
  'rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'
export const rejectBtn =
  'rounded-xl bg-red-600 px-4 py-2 text-sm font-bold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'

export function StatusBadge({ status }) {
  const meta = REVIEW_STATUS_META[status]
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + meta.cls}>{meta.label}</span>
}

export function Toolbar({ status, setStatus, query, setQuery, sort, setSort }) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => {
          const on = status === f.key
          return (
            <button
              key={f.key}
              type="button"
              onClick={() => setStatus(f.key)}
              className={
                'rounded-full px-4 py-2 text-sm font-bold transition ' +
                (on ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200')
              }
            >
              {f.label}
            </button>
          )
        })}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="이메일 또는 이름 검색"
            className="w-56 rounded-2xl border border-gray-200 bg-white py-2.5 pl-4 pr-10 text-sm text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <Search className="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
        </div>
        <label className="flex items-center gap-2 rounded-2xl border border-gray-200 px-3 py-2.5 text-sm font-semibold text-gray-600">
          <ArrowUpDown className="h-4 w-4 text-gray-400" />
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value)}
            className="bg-transparent pr-1 outline-none"
            aria-label="정렬"
          >
            <option value="latest">최신순</option>
            <option value="oldest">오래된순</option>
          </select>
        </label>
      </div>
    </div>
  )
}

export function Pagination({ page, pages, setPage }) {
  if (pages <= 1) return null
  //페이지가 많아도 현재 위치 주변만 보여줘 모바일에서 버튼이 화면 밖으로 밀리지 않게 한다.
  const visiblePages = Array.from({ length: Math.min(pages, page + 2) - Math.max(1, page - 2) + 1 }, (_, i) => Math.max(1, page - 2) + i)
  return (
    <div className="mt-6 flex flex-wrap items-center justify-center gap-1">
      <button
        type="button"
        onClick={() => setPage(Math.max(1, page - 1))}
        disabled={page === 1}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        aria-label="이전 페이지"
      >
        <ChevronLeft className="h-4 w-4" />
      </button>
      {visiblePages[0] > 1 && <span className="px-1 text-gray-400">…</span>}
      {visiblePages.map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => setPage(p)}
          aria-current={p === page ? 'page' : undefined}
          className={
            'h-11 w-11 shrink-0 rounded-lg text-sm font-bold transition ' +
            (p === page ? 'bg-blue-600 text-white' : 'border border-gray-200 text-gray-600 hover:bg-gray-50')
          }
        >
          {p}
        </button>
      ))}
      {visiblePages[visiblePages.length - 1] < pages && <span className="px-1 text-gray-400">…</span>}
      <button
        type="button"
        onClick={() => setPage(Math.min(pages, page + 1))}
        disabled={page === pages}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        aria-label="다음 페이지"
      >
        <ChevronRight className="h-4 w-4" />
      </button>
    </div>
  )
}

/** 실제 API에서 목록을 불러와 상태 필터·검색·정렬·페이지네이션을 적용하는 공통 훅. */
export function useReviewList(loader, matches, initialQuery = '') {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [status, setStatusState] = useState('ALL')
  const [query, setQueryState] = useState(initialQuery)
  const [sort, setSort] = useState('latest')
  const [page, setPage] = useState(1)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')
    loader()
      .then((data) => {
        if (!cancelled) setItems(data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return items
      //'승인대기' 필터에는 권한 부여 확인 중(APPROVAL_PENDING)인 신청도 함께 보여준다.
      .filter((it) => status === 'ALL' || it.status === status || (status === 'PENDING' && it.status === 'APPROVAL_PENDING'))
      .filter((it) => q === '' || matches(it, q))
      .sort((a, b) => (sort === 'latest' ? b.appliedAt.localeCompare(a.appliedAt) : a.appliedAt.localeCompare(b.appliedAt)))
  }, [items, status, query, sort, matches])

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pages)
  const paged = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)

  function updateStatus(id, next) {
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, status: next } : it)))
  }

  function updateField(id, key, value) {
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, [key]: value } : it)))
  }

  return {
    loading,
    loadError,
    status,
    setStatus: (s) => {
      setStatusState(s)
      setPage(1)
    },
    query,
    setQuery: (q) => {
      setQueryState(q)
      setPage(1)
    },
    sort,
    setSort,
    filtered,
    paged,
    pages,
    page: current,
    setPage,
    updateStatus,
    updateField,
  }
}

export function confirmApprove() {
  return window.confirm('승인하시겠습니까?')
}
