import { useEffect, useRef, useState } from 'react'
import { CircleAlertIcon } from 'lucide-react'
import { approveFestivalCancellation, fetchCancellationRequests, rejectFestivalCancellation } from '../../api/adminApi'
import { formatDate as formatDateTime, formatMoney } from '../settlementPresentation'

const CANCELLATION_ERROR_MESSAGES = {
  CANCELLATION_NOT_REQUESTED: '취소 요청이 없는 페스티벌이에요.',
  CANCELLATION_ALREADY_APPROVED: '이미 승인돼 환불이 진행 중인 요청은 반려할 수 없어요.',
  FORBIDDEN_ADMIN_ROLE: '운영자만 행사 취소를 승인할 수 있어요.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌이에요.',
}

const CANCELLATION_FILTERS = [
  { key: 'PENDING', label: '대기' },
  { key: 'REFUNDING', label: '환불 진행 중' },
  { key: 'CANCELLED', label: '취소 완료' },
  { key: 'REJECTED', label: '반려' },
]

const EMPTY_MESSAGES = {
  PENDING: '대기 중인 행사 취소 요청이 없어요.',
  REFUNDING: '환불이 진행 중인 요청이 없어요.',
  CANCELLED: '취소가 완료된 요청이 없어요.',
  REJECTED: '반려된 요청이 없어요.',
}

function toErrorMessage(error) {
  const errorCode = error.response?.data?.errorCode
  return CANCELLATION_ERROR_MESSAGES[errorCode] || '요청을 처리하지 못했어요. 최신 행사 상태를 확인해주세요.'
}

/**
 * 주최자가 요청한 행사 취소를 운영자가 승인하는 목록(페스티벌 관리 > 행사 취소 승인 탭).
 * 승인하면 payment-service 환불 배치가 남은 티켓을 위약금 없이 전액 환불하므로 되돌릴 수 없다.
 * 제목·설명은 부모(FestivalManagement)가 그리고, 여기서는 상태 필터와 목록을 함께 렌더링한다.
 */
function CancellationRequests({ onActionSuccess }) {
  const [filter, setFilter] = useState('PENDING')
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const [working, setWorking] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const busyRef = useRef(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fetchCancellationRequests(filter)
      .then((response) => {
        if (cancelled) return
        setRequests(response.data.data)
        setLoadError('')
      })
      .catch(() => {
        if (!cancelled) setLoadError('행사 취소 요청을 불러오지 못했어요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [filter, refreshKey])

  function selectFilter(key) {
    setFilter(key)
    setNotice('')
    setActionError('')
  }

  //승인·반려는 같은 잠금을 쓴다 — 한 요청에 두 버튼이 연달아 눌리는 일을 막는다.
  async function run(action, successNotice) {
    if (busyRef.current) return
    busyRef.current = true
    setWorking(true)
    setActionError('')
    setNotice('')
    try {
      await action()
      setNotice(successNotice)
      setRefreshKey((value) => value + 1)
      onActionSuccess?.()
    } catch (error) {
      setActionError(toErrorMessage(error))
    } finally {
      busyRef.current = false
      setWorking(false)
    }
  }

  function handleApprove(request) {
    const amountText =
      request.expectedRefundAmount != null ? ` 예상 환불 금액은 ${formatMoney(request.expectedRefundAmount)}이에요.` : ''
    if (!window.confirm(`'${request.name}'의 주최자 귀책 전액 환불을 승인할까요?${amountText} 승인 후 환불 작업은 되돌릴 수 없습니다.`)) return
    run(() => approveFestivalCancellation(request.festivalId), '승인했어요. 환불 배치가 결제 건을 순서대로 처리해요.')
  }

  function handleReject(request) {
    if (!window.confirm(`'${request.name}'의 취소 요청을 반려할까요? 행사는 요청 전 상태로 돌아가고 주최자는 다시 요청할 수 있어요.`)) return
    run(() => rejectFestivalCancellation(request.festivalId), '반려했어요. 행사가 요청 전 상태로 돌아갔어요.')
  }

  return (
    <div className="mt-3">
      <div className="flex flex-wrap gap-2">
        {CANCELLATION_FILTERS.map((f) => {
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

      {notice && (
        <p role="status" className="mt-3 rounded-2xl bg-blue-50 px-3.5 py-3 text-[13px] font-semibold text-blue-700">
          {notice}
        </p>
      )}

      {actionError && (
        <p role="alert" className="mt-3 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
          <CircleAlertIcon className="h-4 w-4" />
          {actionError}
        </p>
      )}

      {loading && <p className="mt-3 text-sm text-gray-400">불러오는 중…</p>}

      {!loading && loadError && <p className="mt-3 text-sm font-semibold text-red-600">{loadError}</p>}

      {!loading && !loadError && requests.length === 0 && (
        <p className="mt-3 text-sm text-gray-400">{EMPTY_MESSAGES[filter]}</p>
      )}

      {!loading && !loadError && requests.length > 0 && (
        <ul className="mt-3 space-y-2">
          {requests.map((request) => (
            <li key={request.festivalId} className="flex flex-col gap-3 rounded-2xl border border-gray-100 px-4 py-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-gray-900">
                    {request.name}
                    <span className="ml-1.5 text-xs font-medium text-gray-400">#{request.festivalId}</span>
                  </p>
                  <p className="mt-0.5 break-words text-xs text-gray-500">
                    {filter === 'REJECTED' ? `당시 취소 사유: ${request.reason}` : request.reason}
                  </p>
                </div>

                {filter === 'PENDING' && (
                  <div className="flex shrink-0 items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleReject(request)}
                      disabled={working}
                      className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:border-gray-400 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      반려
                    </button>
                    <button
                      type="button"
                      onClick={() => handleApprove(request)}
                      disabled={working}
                      className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      전액 환불 승인
                    </button>
                  </div>
                )}
              </div>

              {filter === 'PENDING' && (
                <div className="text-xs text-gray-500">
                  <p>
                    판매 티켓 {(request.soldQuantity ?? 0).toLocaleString()}장 · 환불 대상 결제{' '}
                    {(request.refundTargetPaymentCount ?? 0).toLocaleString()}건 ·{' '}
                    {request.expectedRefundAmount != null
                      ? `예상 환불 금액 ${formatMoney(request.expectedRefundAmount)}`
                      : '환불 금액을 확인할 수 없어요'}
                  </p>
                  {request.unresolvedPaymentCount > 0 && (
                    <p className="mt-0.5 text-orange-600">{request.unresolvedPaymentCount}건은 금액 계산이 어려워 제외됐어요.</p>
                  )}
                </div>
              )}

              {(filter === 'REFUNDING' || filter === 'CANCELLED') && (
                <div className="text-xs text-gray-500">
                  <p>승인 시각 {formatDateTime(request.approvedAt)}</p>
                  {filter === 'CANCELLED' && <p className="mt-0.5">완료 시각 {formatDateTime(request.cancelledAt)}</p>}
                </div>
              )}

              {filter === 'REJECTED' && <p className="text-xs text-gray-500">반려 시각 {formatDateTime(request.rejectedAt)}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default CancellationRequests
