import { useEffect, useRef, useState } from 'react'
import { TriangleAlertIcon } from 'lucide-react'
import { fetchCancellationRequests, approveFestivalCancellation } from '../../api/adminApi'
import { secondaryButton } from '../settlementPresentation'

const CANCELLATION_ERROR_MESSAGES = {
  CANCELLATION_NOT_REQUESTED: '취소 요청이 없는 페스티벌입니다.',
  FORBIDDEN_ADMIN_ROLE: '관리자만 행사 취소를 승인할 수 있습니다.',
}

export default function CancellationRequests() {
  const [requests, setRequests] = useState([])
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const lock = useRef(false)

  useEffect(() => {
    let active = true
    fetchCancellationRequests()
      .then((r) => {
        if (active) setRequests(r.data.data)
      })
      .catch(() => {
        if (active) setMessage('행사 취소 요청을 불러오지 못했습니다.')
      })
    return () => {
      active = false
    }
  }, [refresh])

  async function submit(id) {
    if (
      lock.current ||
      !window.confirm('주최자 귀책 전액 환불을 승인할까요? 승인 후 환불 작업을 되돌릴 수 없습니다.')
    )
      return
    lock.current = true
    setBusy(true)
    try {
      await approveFestivalCancellation(id)
      setMessage('승인했습니다. 환불 배치가 순차적으로 처리합니다.')
      setRefresh((v) => v + 1)
    } catch (error) {
      setMessage(
        CANCELLATION_ERROR_MESSAGES[error.response?.data?.errorCode] ||
          '처리에 실패했습니다. 최신 행사 상태를 확인해 주세요.',
      )
    } finally {
      lock.current = false
      setBusy(false)
    }
  }

  return (
    <section className="my-6 space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">
      <h2 className="flex items-center gap-2 font-bold text-amber-900">
        <TriangleAlertIcon
          size={20}
          aria-hidden="true"
        />
        주최자 귀책 행사 취소
      </h2>
      {message && (
        <p
          role="status"
          className="text-sm text-amber-900"
        >
          {message}
        </p>
      )}
      {requests.length === 0 ? (
        <p>대기 중인 행사 취소 요청이 없습니다.</p>
      ) : (
        requests.map((r) => (
          <div
            key={r.festivalId}
            className="flex flex-wrap items-center justify-between gap-3 border-t border-amber-200 py-3"
          >
            <p className="min-w-0 break-words text-sm text-amber-900">
              {r.name} · #{r.festivalId} · {r.reason}
            </p>
            <button
              className={secondaryButton}
              disabled={busy || r.approved}
              onClick={() => submit(r.festivalId)}
            >
              {r.approved ? '환불 진행 중' : '전액 환불 승인'}
            </button>
          </div>
        ))
      )}
    </section>
  )
}
