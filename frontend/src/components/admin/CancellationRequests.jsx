import { useEffect, useRef, useState } from 'react'
import { CircleAlertIcon } from 'lucide-react'
import { approveFestivalCancellation, fetchCancellationRequests, rejectFestivalCancellation } from '../../api/adminApi'

const CANCELLATION_ERROR_MESSAGES = {
  CANCELLATION_NOT_REQUESTED: '취소 요청이 없는 페스티벌이에요.',
  CANCELLATION_ALREADY_APPROVED: '이미 승인돼 환불이 진행 중인 요청은 반려할 수 없어요.',
  FORBIDDEN_ADMIN_ROLE: '운영자만 행사 취소를 승인할 수 있어요.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌이에요.',
}

function toErrorMessage(error) {
  const errorCode = error.response?.data?.errorCode
  return CANCELLATION_ERROR_MESSAGES[errorCode] || '요청을 처리하지 못했어요. 최신 행사 상태를 확인해주세요.'
}

/**
 * 주최자가 요청한 행사 취소를 운영자가 승인하는 목록(주최자 관리 > 행사 취소 승인 탭).
 * 승인하면 payment-service 환불 배치가 남은 티켓을 위약금 없이 전액 환불하므로 되돌릴 수 없다.
 * 제목·설명은 부모(OrganizerManagement)가 그리고 여기서는 목록만 렌더링한다.
 */
function CancellationRequests() {
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
    fetchCancellationRequests()
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
  }, [refreshKey])

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
    } catch (error) {
      setActionError(toErrorMessage(error))
    } finally {
      busyRef.current = false
      setWorking(false)
    }
  }

  function handleApprove(request) {
    if (!window.confirm(`'${request.name}'의 주최자 귀책 전액 환불을 승인할까요? 승인 후 환불 작업은 되돌릴 수 없습니다.`)) return
    run(() => approveFestivalCancellation(request.festivalId), '승인했어요. 환불 배치가 결제 건을 순서대로 처리해요.')
  }

  function handleReject(request) {
    if (!window.confirm(`'${request.name}'의 취소 요청을 반려할까요? 행사는 요청 전 상태로 돌아가고 주최자는 다시 요청할 수 있어요.`)) return
    run(() => rejectFestivalCancellation(request.festivalId), '반려했어요. 행사가 요청 전 상태로 돌아갔어요.')
  }

  return (
    <div className="mt-3">
      {notice && (
        <p role="status" className="mb-3 rounded-2xl bg-blue-50 px-3.5 py-3 text-[13px] font-semibold text-blue-700">
          {notice}
        </p>
      )}

      {actionError && (
        <p role="alert" className="mb-3 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
          <CircleAlertIcon className="h-4 w-4" />
          {actionError}
        </p>
      )}

      {loading && <p className="text-sm text-gray-400">불러오는 중…</p>}

      {!loading && loadError && <p className="text-sm font-semibold text-red-600">{loadError}</p>}

      {!loading && !loadError && requests.length === 0 && (
        <p className="text-sm text-gray-400">대기 중인 행사 취소 요청이 없어요.</p>
      )}

      {!loading && !loadError && requests.length > 0 && (
        <ul className="space-y-2">
          {requests.map((request) => (
            <li
              key={request.festivalId}
              className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-100 px-4 py-3"
            >
              <div className="min-w-0">
                <p className="truncate text-sm font-bold text-gray-900">
                  {request.name}
                  <span className="ml-1.5 text-xs font-medium text-gray-400">#{request.festivalId}</span>
                </p>
                <p className="mt-0.5 break-words text-xs text-gray-500">{request.reason}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {/* 승인된 요청은 환불이 돌고 있어 반려할 수 없으므로 버튼을 아예 감춘다 */}
                {!request.approved && (
                  <button
                    type="button"
                    onClick={() => handleReject(request)}
                    disabled={working}
                    className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:border-gray-400 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    반려
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => handleApprove(request)}
                  disabled={working || request.approved}
                  className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {request.approved ? '환불 진행 중' : '전액 환불 승인'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default CancellationRequests
