import { useEffect, useMemo, useState } from 'react'
import { Search, ArrowUpDown, X, Mail, Phone, CalendarDays, ChevronLeft, ChevronRight, AlertTriangle } from 'lucide-react'
import {
  REVIEW_STATUS_META,
  ACCOUNT_STATUS_META,
  CATEGORY_BADGE,
  DEFAULT_CATEGORY_BADGE_CLS,
  fetchOrganizerApplications,
  reviewOrganizerApplication,
  fetchFestivalSubmissions,
  reviewFestivalSubmission,
  fetchOrganizers,
  revokeOrganizer,
} from '../../data/admin'

const STATUS_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'PENDING', label: '승인대기' },
  { key: 'APPROVED', label: '승인' },
  { key: 'REJECTED', label: '반려' },
]

const PAGE_SIZE = 3

const approveBtn =
  'rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'
const rejectBtn =
  'rounded-xl bg-red-600 px-4 py-2 text-sm font-bold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'

function StatusBadge({ status }) {
  const meta = REVIEW_STATUS_META[status]
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + meta.cls}>{meta.label}</span>
}

function Toolbar({ status, setStatus, query, setQuery, sort, setSort }) {
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

function Pagination({ page, pages, setPage }) {
  if (pages <= 1) return null
  return (
    <div className="mt-6 flex items-center justify-center gap-1">
      <button
        type="button"
        onClick={() => setPage(Math.max(1, page - 1))}
        disabled={page === 1}
        className="flex h-9 w-9 items-center justify-center rounded-lg border border-gray-200 text-gray-500 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        aria-label="이전 페이지"
      >
        <ChevronLeft className="h-4 w-4" />
      </button>
      {Array.from({ length: pages }, (_, i) => i + 1).map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => setPage(p)}
          className={
            'h-9 w-9 rounded-lg text-sm font-bold transition ' +
            (p === page ? 'bg-blue-600 text-white' : 'border border-gray-200 text-gray-600 hover:bg-gray-50')
          }
        >
          {p}
        </button>
      ))}
      <button
        type="button"
        onClick={() => setPage(Math.min(pages, page + 1))}
        disabled={page === pages}
        className="flex h-9 w-9 items-center justify-center rounded-lg border border-gray-200 text-gray-500 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        aria-label="다음 페이지"
      >
        <ChevronRight className="h-4 w-4" />
      </button>
    </div>
  )
}

/** 실제 API에서 목록을 불러와 상태 필터·검색·정렬·페이지네이션을 적용하는 공통 훅. */
function useReviewList(loader, matches, initialQuery = '') {
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
      .filter((it) => status === 'ALL' || it.status === status)
      .filter((it) => q === '' || matches(it, q))
      .sort((a, b) => (sort === 'latest' ? b.appliedAt.localeCompare(a.appliedAt) : a.appliedAt.localeCompare(b.appliedAt)))
  }, [items, status, query, sort, matches])

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pages)
  const paged = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)

  function updateStatus(id, next) {
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, status: next } : it)))
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
  }
}

function confirmApprove() {
  return window.confirm('승인하시겠습니까?')
}
function confirmReject() {
  return window.confirm('반려하시겠습니까?')
}

/* ---------- 서브탭 A: 주최자 신청 승인 (실제 API 연동) ---------- */

function OrganizerApprovals() {
  const list = useReviewList(fetchOrganizerApplications, (it, q) => it.email.toLowerCase().includes(q) || it.name.toLowerCase().includes(q))
  const [detail, setDetail] = useState(null)
  const [rejectDraftId, setRejectDraftId] = useState(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState({})
  const [pendingId, setPendingId] = useState(null)

  function setError(id, message) {
    setActionError((prev) => ({ ...prev, [id]: message }))
  }

  async function handleApprove(id) {
    if (!confirmApprove()) return
    setPendingId(id)
    setError(id, '')
    try {
      await reviewOrganizerApplication(id, { status: 'APPROVED' })
      list.updateStatus(id, 'APPROVED')
    } catch (error) {
      setError(id, error.message)
    } finally {
      setPendingId(null)
    }
  }

  function openRejectDraft(id) {
    setRejectDraftId(id)
    setRejectReason('')
    setError(id, '')
  }
  function cancelRejectDraft() {
    setRejectDraftId(null)
    setRejectReason('')
  }

  // 실제 백엔드는 반려 시 사유(rejectReason)를 필수로 받기 때문에, 참고 디자인의 즉시-반려 버튼 대신
  // 사유를 입력받는 작은 폼을 펼친다.
  async function handleReject(id) {
    if (!rejectReason.trim()) {
      setError(id, '반려 사유를 입력해주세요.')
      return
    }
    setPendingId(id)
    setError(id, '')
    try {
      await reviewOrganizerApplication(id, { status: 'REJECTED', rejectReason: rejectReason.trim() })
      list.updateStatus(id, 'REJECTED')
      setRejectDraftId(null)
      setRejectReason('')
    } catch (error) {
      setError(id, error.message)
    } finally {
      setPendingId(null)
    }
  }

  return (
    <div>
      <Toolbar status={list.status} setStatus={list.setStatus} query={list.query} setQuery={list.setQuery} sort={list.sort} setSort={list.setSort} />

      {list.loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}

      {!list.loading && list.loadError && (
        <p className="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{list.loadError}</p>
      )}

      {!list.loading && !list.loadError && (
        <ul className="mt-5 space-y-3">
          {list.paged.map((a) => (
            <li key={a.id} className="flex flex-col gap-4 rounded-2xl border border-gray-200 p-5 md:flex-row md:items-center md:justify-between">
              <div className="flex items-start gap-4">
                <StatusBadge status={a.status} />
                <div>
                  <p className="font-bold text-gray-900">{a.email}</p>
                  <p className="mt-0.5 text-sm text-gray-500">
                    {a.name} · 신청일 {a.appliedAt}
                  </p>
                  <button type="button" onClick={() => setDetail(a)} className="mt-2 text-sm font-bold text-blue-600 hover:underline">
                    소개글 보기
                  </button>

                  {actionError[a.id] && <p className="mt-2 text-xs font-semibold text-red-600">{actionError[a.id]}</p>}

                  {rejectDraftId === a.id && (
                    <div className="mt-3 max-w-sm space-y-2">
                      <textarea
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="반려 사유를 입력하세요"
                        rows={3}
                        className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={cancelRejectDraft}
                          disabled={pendingId === a.id}
                          className="rounded-xl border border-gray-200 px-4 py-2 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          취소
                        </button>
                        <button type="button" onClick={() => handleReject(a.id)} disabled={pendingId === a.id} className={rejectBtn}>
                          {pendingId === a.id ? '처리 중…' : '반려 확정'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {rejectDraftId !== a.id && (
                <div className="flex shrink-0 gap-2">
                  <button type="button" disabled={a.status === 'APPROVED' || pendingId === a.id} onClick={() => handleApprove(a.id)} className={approveBtn}>
                    승인
                  </button>
                  <button
                    type="button"
                    disabled={a.status === 'REJECTED' || pendingId === a.id}
                    onClick={() => openRejectDraft(a.id)}
                    className={rejectBtn}
                  >
                    반려
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {!list.loading && !list.loadError && list.filtered.length === 0 && (
        <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 신청이 없습니다.</p>
      )}

      <Pagination page={list.page} pages={list.pages} setPage={list.setPage} />

      <SlideOver open={!!detail} onClose={() => setDetail(null)} title="주최자 신청 상세">
        {detail && (
          <div className="space-y-5">
            <StatusBadge status={detail.status} />
            <dl className="space-y-3 text-sm">
              <div className="flex items-center gap-2 text-gray-600">
                <Mail className="h-4 w-4 text-gray-400" />
                {detail.email}
              </div>
              <div className="flex items-center gap-2 text-gray-600">
                <Phone className="h-4 w-4 text-gray-400" />
                {detail.contact}
              </div>
              <div className="flex items-center gap-2 text-gray-600">
                <CalendarDays className="h-4 w-4 text-gray-400" />
                신청일 {detail.appliedAt}
              </div>
            </dl>
            <div>
              <p className="text-sm font-bold text-gray-900">소개글</p>
              <p className="mt-2 text-sm leading-relaxed text-gray-600">{detail.intro}</p>
            </div>
          </div>
        )}
      </SlideOver>
    </div>
  )
}

/* ---------- 서브탭 B: 페스티벌 등록 승인 (실제 API 연동) ---------- */

function FestivalApprovals({ initialQuery = '' }) {
  const list = useReviewList(
    fetchFestivalSubmissions,
    (it, q) => it.name.toLowerCase().includes(q) || it.host.toLowerCase().includes(q),
    initialQuery,
  )
  const [expanded, setExpanded] = useState({})
  const [actionError, setActionError] = useState({})
  const [pendingId, setPendingId] = useState(null)

  async function handleDecision(id, uiStatus) {
    const ok = uiStatus === 'APPROVED' ? confirmApprove() : confirmReject()
    if (!ok) return
    setPendingId(id)
    setActionError((prev) => ({ ...prev, [id]: '' }))
    try {
      await reviewFestivalSubmission(id, uiStatus === 'APPROVED' ? 'PUBLISHED' : 'REJECTED')
      list.updateStatus(id, uiStatus)
    } catch (error) {
      setActionError((prev) => ({ ...prev, [id]: error.message }))
    } finally {
      setPendingId(null)
    }
  }

  return (
    <div>
      <Toolbar status={list.status} setStatus={list.setStatus} query={list.query} setQuery={list.setQuery} sort={list.sort} setSort={list.setSort} />

      {list.loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}

      {!list.loading && list.loadError && (
        <p className="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{list.loadError}</p>
      )}

      {!list.loading && !list.loadError && (
        <ul className="mt-5 space-y-4">
          {list.paged.map((f) => {
            const open = expanded[f.id]
            return (
              <li key={f.id} className="flex flex-col gap-4 rounded-2xl border border-gray-200 p-5 sm:flex-row">
                <img
                  src={f.image || '/placeholder.svg'}
                  alt={f.name}
                  className="h-40 w-full shrink-0 rounded-xl object-cover sm:h-28 sm:w-40"
                  crossOrigin="anonymous"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={f.status} />
                    <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + (CATEGORY_BADGE[f.category] ?? DEFAULT_CATEGORY_BADGE_CLS)}>
                      {f.category}
                    </span>
                  </div>
                  <p className="mt-2 text-lg font-extrabold text-gray-900">{f.name}</p>
                  <p className="text-sm text-gray-500">
                    {f.host} · {f.date} · {f.location}
                  </p>
                  <p className={'mt-2 text-sm leading-relaxed text-gray-600 ' + (open ? '' : 'line-clamp-2')}>{f.description}</p>
                  <button
                    type="button"
                    onClick={() => setExpanded((prev) => ({ ...prev, [f.id]: !prev[f.id] }))}
                    className="mt-1 text-sm font-bold text-blue-600 hover:underline"
                  >
                    {open ? '접기' : '더보기'}
                  </button>

                  <div className="mt-3 flex flex-wrap gap-2">
                    {f.tickets.map((t) => (
                      <span key={t.name} className="rounded-lg bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600">
                        {t.name} · {t.price}
                      </span>
                    ))}
                  </div>

                  {actionError[f.id] && <p className="mt-2 text-xs font-semibold text-red-600">{actionError[f.id]}</p>}

                  <div className="mt-4 flex justify-end gap-2">
                    <button
                      type="button"
                      disabled={f.status === 'APPROVED' || pendingId === f.id}
                      onClick={() => handleDecision(f.id, 'APPROVED')}
                      className={approveBtn}
                    >
                      승인
                    </button>
                    <button
                      type="button"
                      disabled={f.status === 'REJECTED' || pendingId === f.id}
                      onClick={() => handleDecision(f.id, 'REJECTED')}
                      className={rejectBtn}
                    >
                      반려
                    </button>
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {!list.loading && !list.loadError && list.filtered.length === 0 && (
        <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 페스티벌이 없습니다.</p>
      )}

      <Pagination page={list.page} pages={list.pages} setPage={list.setPage} />
    </div>
  )
}

/* ---------- 서브탭 C: 주최자 목록 (목업) ---------- */

const ACCOUNT_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'ACTIVE', label: '활동중' },
  { key: 'SUSPENDED', label: '정지됨' },
]

function OrganizerList({ onViewFestivals }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [account, setAccount] = useState('ALL')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [revoking, setRevoking] = useState(null)
  const [revokePending, setRevokePending] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchOrganizers().then((data) => {
      if (!cancelled) {
        setItems(data)
        setLoading(false)
      }
    })
    return () => {
      cancelled = true
    }
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return items
      .filter((o) => account === 'ALL' || o.accountStatus === account)
      .filter((o) => q === '' || o.nickname.toLowerCase().includes(q) || o.email.toLowerCase().includes(q))
  }, [items, account, query])

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pages)
  const paged = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)

  async function confirmRevoke() {
    if (!revoking) return
    setRevokePending(true)
    try {
      await revokeOrganizer(revoking.id)
      setItems((prev) => prev.filter((o) => o.id !== revoking.id))
      setRevoking(null)
    } finally {
      setRevokePending(false)
    }
  }

  return (
    <div>
      {/* 툴바 */}
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-2">
          {ACCOUNT_FILTERS.map((f) => {
            const on = account === f.key
            return (
              <button
                key={f.key}
                type="button"
                onClick={() => {
                  setAccount(f.key)
                  setPage(1)
                }}
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
        <div className="relative">
          <input
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setPage(1)
            }}
            placeholder="닉네임 또는 이메일 검색"
            className="w-64 rounded-2xl border border-gray-200 bg-white py-2.5 pl-4 pr-10 text-sm text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <Search className="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
        </div>
      </div>

      {loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}

      {!loading && (
        <>
          {/* 테이블 (데스크톱) */}
          <div className="mt-5 hidden overflow-x-auto lg:block">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-left text-xs font-bold uppercase tracking-wide text-gray-400">
                  <th className="px-3 py-3">주최자</th>
                  <th className="px-3 py-3">상태</th>
                  <th className="px-3 py-3">승인일</th>
                  <th className="px-3 py-3 text-center">등록 페스티벌</th>
                  <th className="px-3 py-3 text-right">누적 판매</th>
                  <th className="px-3 py-3 text-right">누적 매출</th>
                  <th className="px-3 py-3 text-right">관리</th>
                </tr>
              </thead>
              <tbody>
                {paged.map((o) => (
                  <tr key={o.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-3 py-4">
                      <p className="font-bold text-gray-900">{o.nickname}</p>
                      <p className="text-xs text-gray-500">{o.email}</p>
                    </td>
                    <td className="px-3 py-4">
                      <AccountBadge status={o.accountStatus} />
                    </td>
                    <td className="px-3 py-4 text-gray-600">{o.approvedAt}</td>
                    <td className="px-3 py-4 text-center">
                      <button
                        type="button"
                        onClick={() => onViewFestivals(o.nickname)}
                        className="rounded-lg px-2 py-1 font-bold text-blue-600 transition hover:bg-blue-50 hover:underline"
                      >
                        {o.festivalCount}개
                      </button>
                    </td>
                    <td className="px-3 py-4 text-right text-gray-600">{o.ticketsSold.toLocaleString()}장</td>
                    <td className="px-3 py-4 text-right font-semibold text-gray-900">{o.revenue}</td>
                    <td className="px-3 py-4 text-right">
                      <button
                        type="button"
                        onClick={() => setRevoking(o)}
                        className="rounded-xl border border-red-200 px-3 py-1.5 text-xs font-bold text-red-600 transition hover:bg-red-50"
                      >
                        주최자 권한 회수
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 카드 (모바일/태블릿) */}
          <ul className="mt-5 space-y-3 lg:hidden">
            {paged.map((o) => (
              <li key={o.id} className="rounded-2xl border border-gray-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-gray-900">{o.nickname}</p>
                    <p className="text-xs text-gray-500">{o.email}</p>
                  </div>
                  <AccountBadge status={o.accountStatus} />
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                  <div>
                    <dt className="text-xs text-gray-400">승인일</dt>
                    <dd className="text-gray-700">{o.approvedAt}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-400">등록 페스티벌</dt>
                    <dd>
                      <button type="button" onClick={() => onViewFestivals(o.nickname)} className="font-bold text-blue-600 hover:underline">
                        {o.festivalCount}개
                      </button>
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-400">누적 판매</dt>
                    <dd className="text-gray-700">{o.ticketsSold.toLocaleString()}장</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-400">누적 매출</dt>
                    <dd className="font-semibold text-gray-900">{o.revenue}</dd>
                  </div>
                </dl>
                <button
                  type="button"
                  onClick={() => setRevoking(o)}
                  className="mt-4 w-full rounded-xl border border-red-200 px-3 py-2 text-xs font-bold text-red-600 transition hover:bg-red-50"
                >
                  주최자 권한 회수
                </button>
              </li>
            ))}
          </ul>

          {filtered.length === 0 && <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 주최자가 없습니다.</p>}

          <Pagination page={current} pages={pages} setPage={setPage} />
        </>
      )}

      {/* 권한 회수 확인 모달 */}
      {revoking && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/40" onClick={() => !revokePending && setRevoking(null)} />
          <div className="relative w-full max-w-md rounded-3xl bg-white p-7 shadow-xl">
            <div className="flex items-start gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-600">
                <AlertTriangle className="h-5 w-5" />
              </span>
              <div>
                <h3 className="text-lg font-extrabold text-gray-900">주최자 권한 회수</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-600">
                  <span className="font-bold text-gray-900">{revoking.nickname}</span> 님의 권한을 회수하시겠습니까? 해당 유저는 다시 일반 회원(USER)으로
                  전환됩니다.
                </p>
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setRevoking(null)}
                disabled={revokePending}
                className="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                취소
              </button>
              <button
                type="button"
                onClick={confirmRevoke}
                disabled={revokePending}
                className="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {revokePending ? '처리 중…' : '권한 회수'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function AccountBadge({ status }) {
  const meta = ACCOUNT_STATUS_META[status]
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + meta.cls}>{meta.label}</span>
}

/* ---------- 슬라이드 오버 패널 ---------- */

function SlideOver({ open, onClose, title, children }) {
  return (
    <div className={'fixed inset-0 z-50 ' + (open ? '' : 'pointer-events-none')} aria-hidden={!open}>
      <div onClick={onClose} className={'absolute inset-0 bg-black/30 transition-opacity ' + (open ? 'opacity-100' : 'opacity-0')} />
      <aside
        className={
          'absolute right-0 top-0 flex h-full w-full max-w-md flex-col bg-white shadow-xl transition-transform duration-300 ' +
          (open ? 'translate-x-0' : 'translate-x-full')
        }
        role="dialog"
        aria-label={title}
      >
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h3 className="text-lg font-extrabold text-gray-900">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 transition hover:bg-gray-100"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-6">{children}</div>
      </aside>
    </div>
  )
}

/* ---------- 주최자 관리 (탭 1) ---------- */

const SUB_TABS = [
  { key: 'organizer', label: '주최자 신청 승인' },
  { key: 'festival', label: '페스티벌 등록 승인' },
  { key: 'list', label: '주최자 목록' },
]

function OrganizerManagement() {
  const [sub, setSub] = useState('organizer')
  const [festivalQuery, setFestivalQuery] = useState('')

  function viewOrganizerFestivals(nickname) {
    setFestivalQuery(nickname)
    setSub('festival')
  }

  function renderSub() {
    if (sub === 'organizer') return <OrganizerApprovals />
    if (sub === 'festival') return <FestivalApprovals key={festivalQuery} initialQuery={festivalQuery} />
    return <OrganizerList onViewFestivals={viewOrganizerFestivals} />
  }

  return (
    <div>
      {/* 서브탭 */}
      <div className="inline-flex gap-1 rounded-2xl bg-gray-100 p-1">
        {SUB_TABS.map((t) => {
          const on = sub === t.key
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => {
                if (t.key !== 'festival') setFestivalQuery('')
                setSub(t.key)
              }}
              className={'rounded-xl px-5 py-2.5 text-sm font-bold transition ' + (on ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500 hover:text-gray-700')}
            >
              {t.label}
            </button>
          )
        })}
      </div>

      <div className="mt-6 rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">{renderSub()}</div>
    </div>
  )
}

export default OrganizerManagement
