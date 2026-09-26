import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { CATEGORY_BADGE, DEFAULT_CATEGORY_BADGE_CLS, OPERATION_STATUS_META, fetchFestivalOperationsPage } from '../../data/admin'
import SharedPagination from '../Pagination'

const OPERATIONS_PAGE_SIZE = 10
//판매 부진 기준 — 프론트에서만 쓰는 강조 임계값(백엔드는 saleRate 원값만 내려준다)
const LOW_SALE_RATE_THRESHOLD = 30
//기존 팔레트 중 같은 행의 카테고리·운영 상태 배지와 겹치지 않는 색(정산 대시보드 CONFIRMED와 동일 클래스)
const SOLD_OUT_BADGE_CLS = 'bg-indigo-50 text-indigo-700'
const LOW_SALE_BADGE_CLS = 'bg-orange-50 text-orange-800'

export const OPERATION_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'SCHEDULED', label: '예정' },
  { key: 'ONGOING', label: '진행 중' },
  { key: 'CLOSED', label: '종료' },
  { key: 'CANCELLED', label: '취소' },
]

const EMPTY_MESSAGES = {
  ALL: '조건에 맞는 페스티벌이 없습니다.',
  SCHEDULED: '예정된 페스티벌이 없습니다.',
  ONGOING: '진행 중인 페스티벌이 없습니다.',
  CLOSED: '종료된 페스티벌이 없습니다.',
  CANCELLED: '취소된 페스티벌이 없습니다.',
}

//같은 CANCELLED 버킷 안에서도 취소 요청 중인지 취소가 끝났는지는 운영자가 구분해서 볼 필요가 있다.
function operationBadge(item) {
  const meta = OPERATION_STATUS_META[item.operationStatus] ?? { label: item.operationStatus, cls: DEFAULT_CATEGORY_BADGE_CLS }
  if (item.operationStatus === 'CANCELLED') {
    return { label: item.festivalStatus === 'CANCELLATION_PENDING' ? '취소 요청 중' : '취소 완료', cls: meta.cls }
  }
  return meta
}

//매진·판매 부진 판단 — 어드민 대시보드도 같은 기준을 써야 해서 export한다.
export function isSoldOut(item) {
  return item.totalQuantity > 0 && item.soldQuantity >= item.totalQuantity
}
export function isLowSale(item) {
  return (item.operationStatus === 'SCHEDULED' || item.operationStatus === 'ONGOING') && item.saleRate < LOW_SALE_RATE_THRESHOLD
}

export function HighlightBadges({ item }) {
  const soldOut = isSoldOut(item)
  const lowSale = isLowSale(item)
  if (!soldOut && !lowSale) return null
  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {soldOut && <span className={'rounded-full px-2 py-0.5 text-[11px] font-bold ' + SOLD_OUT_BADGE_CLS}>매진</span>}
      {lowSale && <span className={'rounded-full px-2 py-0.5 text-[11px] font-bold ' + LOW_SALE_BADGE_CLS}>판매 부진</span>}
    </div>
  )
}

//판매 진행 막대 — 어드민 대시보드에서도 재사용한다. 대시보드는 매진/판매 부진 뱃지를 카드 상단에 따로
//두므로 showBadges=false로 여기 내장된 뱃지를 끈다(기본값은 true라 기존 화면은 그대로다).
export function SalesProgress({ item, showBadges = true }) {
  return (
    <div className="min-w-[140px]">
      <p className="text-sm font-semibold text-gray-700">
        {item.soldQuantity.toLocaleString()} / {item.totalQuantity.toLocaleString()}장
        <span className="ml-1.5 text-xs font-bold text-gray-400">{item.saleRate}%</span>
      </p>
      <div className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-gray-100">
        <div className="h-full rounded-full bg-blue-600 transition-all" style={{ width: `${item.saleRate}%` }} />
      </div>
      {showBadges && <HighlightBadges item={item} />}
    </div>
  )
}

function CategoryBadge({ category }) {
  return (
    <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + (CATEGORY_BADGE[category] ?? DEFAULT_CATEGORY_BADGE_CLS)}>
      {category}
    </span>
  )
}

function OperationStatusBadge({ item }) {
  const badge = operationBadge(item)
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + badge.cls}>{badge.label}</span>
}

/** 페스티벌 관리 > 운영 현황 서브탭 — 공개된 적 있는 페스티벌의 운영 상태·판매 현황을 조회한다.
 * initialFilter가 없으면 기존과 동일하게 'ALL'로 시작한다(어드민 대시보드에서 필터를 지정해 진입할 때만 쓴다). */
function FestivalOperations({ initialFilter } = {}) {
  const [filter, setFilter] = useState(initialFilter ?? 'ALL')
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)

  const [items, setItems] = useState([])
  const [pagination, setPagination] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  //입력이 멈추고 300ms 뒤에만 검색어를 반영한다(서버 사이드 검색).
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
    const params = { operationStatus: filter, page, size: OPERATIONS_PAGE_SIZE }
    if (query) params.keyword = query
    fetchFestivalOperationsPage(params, controller.signal)
      .then(({ items: nextItems, pagination: nextPagination }) => {
        if (controller.signal.aborted) return
        setItems(nextItems)
        setPagination(nextPagination)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setLoadError(error.response?.data?.message || '운영 현황을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [filter, query, page])

  function selectFilter(key) {
    setFilter(key)
    setPage(0)
  }

  return (
    <div>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-2">
          {OPERATION_FILTERS.map((f) => {
            const on = filter === f.key
            return (
              <button
                key={f.key}
                type="button"
                onClick={() => selectFilter(f.key)}
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
            value={queryInput}
            onChange={(e) => setQueryInput(e.target.value)}
            placeholder="페스티벌명, 주최자 닉네임·이메일 검색"
            className="w-64 rounded-2xl border border-gray-200 bg-white py-2.5 pl-4 pr-10 text-sm text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <Search className="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
        </div>
      </div>

      <p className="mt-3 text-xs text-gray-500">환불된 티켓의 재고는 매일 19시에 반영돼요.</p>

      {loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}
      {!loading && loadError && (
        <p className="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{loadError}</p>
      )}

      {!loading && !loadError && (
        <>
          {/* 테이블 (데스크톱) */}
          <div className="mt-5 hidden overflow-x-auto lg:block">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-left text-xs font-bold uppercase tracking-wide text-gray-400">
                  <th className="px-3 py-3">페스티벌</th>
                  <th className="px-3 py-3">카테고리</th>
                  <th className="px-3 py-3">기간</th>
                  <th className="px-3 py-3">운영 상태</th>
                  <th className="px-3 py-3">판매 현황</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-3 py-4">
                      <p className="font-bold text-gray-900">{item.name}</p>
                      <p className="text-xs text-gray-500">{item.host}</p>
                    </td>
                    <td className="px-3 py-4">
                      <CategoryBadge category={item.category} />
                    </td>
                    <td className="px-3 py-4 text-gray-600">{item.dateRange}</td>
                    <td className="px-3 py-4">
                      <OperationStatusBadge item={item} />
                    </td>
                    <td className="px-3 py-4">
                      <SalesProgress item={item} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 카드 (모바일/태블릿) */}
          <ul className="mt-5 space-y-3 lg:hidden">
            {items.map((item) => (
              <li key={item.id} className="rounded-2xl border border-gray-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-gray-900">{item.name}</p>
                    <p className="text-xs text-gray-500">{item.host}</p>
                  </div>
                  <OperationStatusBadge item={item} />
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <CategoryBadge category={item.category} />
                  <span className="text-xs text-gray-500">{item.dateRange}</span>
                </div>
                <div className="mt-3">
                  <SalesProgress item={item} />
                </div>
              </li>
            ))}
          </ul>

          {items.length === 0 && (
            <p className="py-12 text-center text-sm font-semibold text-gray-400">{EMPTY_MESSAGES[filter]}</p>
          )}

          <SharedPagination page={page + 1} totalPages={pagination?.totalPages ?? 0} onChange={(p) => setPage(p - 1)} />
        </>
      )}
    </div>
  )
}

export default FestivalOperations
