import { useEffect, useState } from 'react'
import { CATEGORY_BADGE, DEFAULT_CATEGORY_BADGE_CLS, fetchFestivalSubmissionsPage, reviewFestivalSubmission } from '../../data/admin'
import { fetchCancellationRequests, fetchPendingFestivals } from '../../api/adminApi'
import { Pagination, StatusBadge, Toolbar, approveBtn, confirmApprove, rejectBtn } from './ReviewListShared'
import CancellationRequests from './CancellationRequests'
import FestivalOperations from './FestivalOperations'
import DetailModal from './DetailModal'

const FESTIVAL_PAGE_SIZE = 5

/* ---------- 서브탭 A: 페스티벌 등록 승인 (서버 페이징) ---------- */

function FestivalApprovals({ initialQuery = '', onActionSuccess }) {
  const [status, setStatus] = useState(initialQuery ? 'ALL' : 'PENDING')
  const [queryInput, setQueryInput] = useState(initialQuery)
  const [query, setQuery] = useState(initialQuery)
  const [sort, setSort] = useState('latest')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  const [items, setItems] = useState([])
  const [pagination, setPagination] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [descriptionDetail, setDescriptionDetail] = useState(null)
  const [actionError, setActionError] = useState({})
  const [pendingId, setPendingId] = useState(null)
  const [rejectDraftId, setRejectDraftId] = useState(null)
  const [rejectReason, setRejectReason] = useState('')

  //입력이 멈추고 300ms 뒤에만 검색어를 반영한다. 서버 사이드 검색이라 매 타이핑마다 요청하면 낭비다.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(queryInput.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [queryInput])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setLoadError('')
    const params = { status, page, size: FESTIVAL_PAGE_SIZE, sort: sort === 'latest' ? 'createdAt,desc' : 'createdAt,asc' }
    if (query) params.keyword = query
    fetchFestivalSubmissionsPage(params, controller.signal)
      .then(({ items: nextItems, pagination: nextPagination }) => {
        if (controller.signal.aborted) return
        //승인·반려 직후 재조회했는데 현재 페이지가 비면(마지막 항목을 처리한 경우) 이전 페이지로 되돌아간다.
        if (nextItems.length === 0 && page > 0) {
          setPage((p) => p - 1)
          return
        }
        setItems(nextItems)
        setPagination(nextPagination)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setLoadError(error.response?.data?.message || '목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, query, sort, page, reloadKey])

  function selectStatus(next) {
    setStatus(next)
    setPage(0)
  }
  function selectSort(next) {
    setSort(next)
    setPage(0)
  }

  function reload() {
    setReloadKey((k) => k + 1)
    onActionSuccess?.()
  }

  async function handleApprove(id) {
    if (!confirmApprove()) return
    setPendingId(id)
    setActionError((prev) => ({ ...prev, [id]: '' }))
    try {
      await reviewFestivalSubmission(id, 'PUBLISHED')
      reload()
    } catch (error) {
      setActionError((prev) => ({ ...prev, [id]: error.message }))
    } finally {
      setPendingId(null)
    }
  }

  //주최자 신청 반려와 같은 방식 — 사유를 필수로 받아 주최자에게 그대로 전달한다.
  async function handleReject(id) {
    if (!rejectReason.trim()) {
      setActionError((prev) => ({ ...prev, [id]: '반려 사유를 입력해주세요.' }))
      return
    }
    setPendingId(id)
    setActionError((prev) => ({ ...prev, [id]: '' }))
    try {
      await reviewFestivalSubmission(id, 'REJECTED', rejectReason.trim())
      setRejectDraftId(null)
      setRejectReason('')
      reload()
    } catch (error) {
      setActionError((prev) => ({ ...prev, [id]: error.message }))
    } finally {
      setPendingId(null)
    }
  }

  const pages = pagination?.totalPages ?? 1

  return (
    <div>
      <Toolbar
        status={status}
        setStatus={selectStatus}
        query={queryInput}
        setQuery={setQueryInput}
        sort={sort}
        setSort={selectSort}
        placeholder="페스티벌명, 주최자 닉네임·이메일 검색"
      />

      {loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}

      {!loading && loadError && (
        <p className="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{loadError}</p>
      )}

      {!loading && !loadError && (
        <ul className="mt-5 space-y-4">
          {items.map((f) => {
            return (
              <li key={f.id} className="flex flex-col gap-4 rounded-2xl border border-gray-200 p-5 sm:flex-row">
                <img
                  src={f.image || '/placeholder.jpg'}
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
                  <p className="mt-2 text-sm leading-relaxed text-gray-600 line-clamp-2">{f.description}</p>
                  <button
                    type="button"
                    onClick={() => setDescriptionDetail(f)}
                    className="mt-1 text-sm font-bold text-blue-600 hover:underline"
                  >
                    더보기
                  </button>

                  <div className="mt-3 flex flex-wrap gap-2">
                    {f.tickets.map((t) => (
                      <span key={t.name} className="rounded-lg bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600">
                        {t.name} · {t.price}
                      </span>
                    ))}
                  </div>

                  {f.status === 'REJECTED' && f.rejectReason && (
                    <p className="mt-2 text-sm text-red-600">반려 사유: {f.rejectReason}</p>
                  )}
                  {f.rawStatus === 'CLOSED' && <p className="mt-2 text-xs text-gray-500">기간이 끝나 종료된 페스티벌이에요.</p>}

                  {actionError[f.id] && <p className="mt-2 text-xs font-semibold text-red-600">{actionError[f.id]}</p>}

                  {rejectDraftId === f.id && (
                    <div className="mt-3 max-w-sm space-y-2">
                      <textarea
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="반려 사유를 입력하세요 (주최자에게 그대로 전달됩니다)"
                        rows={3}
                        className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => { setRejectDraftId(null); setRejectReason('') }}
                          disabled={pendingId === f.id}
                          className="rounded-xl border border-gray-200 px-4 py-2 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          취소
                        </button>
                        <button type="button" onClick={() => handleReject(f.id)} disabled={pendingId === f.id} className={rejectBtn}>
                          {pendingId === f.id ? '처리 중…' : '반려 확정'}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* 이미 처리된 페스티벌에는 버튼을 두지 않는다(승인 직후 반려 버튼이 잠깐 남던 문제). */}
                  {rejectDraftId !== f.id && f.status === 'PENDING' && (
                    <div className="mt-4 flex justify-end gap-2">
                      <button type="button" disabled={pendingId === f.id} onClick={() => handleApprove(f.id)} className={approveBtn}>
                        {pendingId === f.id ? '처리 중…' : '공개 승인'}
                      </button>
                      <button
                        type="button"
                        disabled={pendingId === f.id}
                        onClick={() => { setRejectDraftId(f.id); setRejectReason(''); setActionError((prev) => ({ ...prev, [f.id]: '' })) }}
                        className={rejectBtn}
                      >
                        반려
                      </button>
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {!loading && !loadError && items.length === 0 && (
        <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 페스티벌이 없습니다.</p>
      )}

      <Pagination page={page + 1} pages={pages} setPage={(p) => setPage(p - 1)} />

      <DetailModal open={!!descriptionDetail} onClose={() => setDescriptionDetail(null)} title={descriptionDetail?.name}>
        <p className="text-sm leading-relaxed text-gray-600">{descriptionDetail?.description}</p>
      </DetailModal>
    </div>
  )
}

/* ---------- 서브탭 C: 행사 취소 승인 ---------- */

//정산 대시보드 하단에 있을 때는 운영자가 찾지 못해 승인이 멈췄다. 주최자 심사와 같은 자리에 둔다.
function CancellationApprovals({ onActionSuccess, initialFilter }) {
  return (
    <div>
      <h2 className="text-lg font-extrabold text-gray-900">행사 취소 승인</h2>
      <p className="mt-1 text-sm text-gray-500">
        주최자가 요청한 행사 취소를 승인하면 남은 티켓이 위약금 없이 전액 환불되고, 정산은 환불이 끝날 때까지 보류돼요.
      </p>
      <CancellationRequests onActionSuccess={onActionSuccess} initialFilter={initialFilter} />
    </div>
  )
}

/* ---------- 페스티벌 관리 (탭) ---------- */

const SUB_TABS = [
  { key: 'festival', label: '페스티벌 등록 승인' },
  { key: 'operations', label: '운영 현황' },
  { key: 'cancellation', label: '행사 취소 승인' },
]

//initialSub/initialOperationsFilter/initialCancellationFilter가 없으면 기존과 동일하게 시작한다
//(어드민 대시보드에서 서브탭·필터를 지정해 진입할 때만 쓴다).
function FestivalManagement({ initialQuery = '', initialSub, initialOperationsFilter, initialCancellationFilter }) {
  const [sub, setSub] = useState(initialSub ?? 'festival')
  const [registrationPendingCount, setRegistrationPendingCount] = useState(0)
  const [cancellationPendingCount, setCancellationPendingCount] = useState(0)

  function refreshRegistrationPendingCount() {
    fetchPendingFestivals({ status: 'PENDING', page: 0, size: 1 })
      .then((response) => setRegistrationPendingCount(response.data.meta.pagination.totalItems))
      .catch(() => {})
  }

  function refreshCancellationPendingCount() {
    fetchCancellationRequests('PENDING')
      .then((response) => setCancellationPendingCount(response.data.data.length))
      .catch(() => {})
  }

  //서브탭을 오가도 대기 뱃지가 유지되도록 최초 진입 시 한 번만 불러온다.
  useEffect(() => {
    refreshRegistrationPendingCount()
    refreshCancellationPendingCount()
  }, [])

  function badgeCountFor(key) {
    if (key === 'festival') return registrationPendingCount
    if (key === 'cancellation') return cancellationPendingCount
    return 0
  }

  function renderSub() {
    if (sub === 'festival') {
      return <FestivalApprovals key={initialQuery} initialQuery={initialQuery} onActionSuccess={refreshRegistrationPendingCount} />
    }
    if (sub === 'operations') return <FestivalOperations initialFilter={initialOperationsFilter} />
    return <CancellationApprovals onActionSuccess={refreshCancellationPendingCount} initialFilter={initialCancellationFilter} />
  }

  return (
    <div>
      {/* 서브탭 */}
      <div className="inline-flex gap-1 rounded-2xl bg-gray-100 p-1">
        {SUB_TABS.map((t) => {
          const on = sub === t.key
          const badgeCount = badgeCountFor(t.key)
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setSub(t.key)}
              className={'rounded-xl px-5 py-2.5 text-sm font-bold transition ' + (on ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500 hover:text-gray-700')}
            >
              {t.label}
              {badgeCount > 0 && (
                <span className="ml-2 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-700">{badgeCount}</span>
              )}
            </button>
          )
        })}
      </div>

      <div className="mt-6 rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">{renderSub()}</div>
    </div>
  )
}

export default FestivalManagement
