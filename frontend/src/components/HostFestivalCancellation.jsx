import { useRef, useState } from 'react'
import { CircleAlertIcon, TriangleAlertIcon } from 'lucide-react'
import { requestFestivalCancellation } from '../api/hostFestivalApi'

const CANCELLATION_ERROR_MESSAGES = {
  FESTIVAL_NOT_CANCELLABLE: '공개 또는 종료 상태의 페스티벌만 취소할 수 있어요.',
  CANCEL_REASON_REQUIRED: '취소 사유를 입력해주세요.',
  FORBIDDEN_HOST_ROLE: '주최자만 행사 취소를 요청할 수 있어요.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌이에요.',
}

function toErrorMessage(error) {
  const errorCode = error.response?.data?.errorCode
  return CANCELLATION_ERROR_MESSAGES[errorCode] || '요청을 처리하지 못했어요. 최신 행사 상태를 확인해주세요.'
}

/**
 * 주최자 귀책 행사 취소 요청 영역(주최자 페스티벌 상세 하단).
 * 요청 즉시 신규 예매가 막히고(CANCELLATION_PENDING), 운영자가 승인하면 남은 티켓이 위약금 없이 전액 환불된다.
 * 되돌릴 수 없는 작업이라 도우미 계정의 비밀번호 안내와 같은 amber 경고 카드 톤을 쓴다.
 */
function HostFestivalCancellation({ festivalId, festivalStatus, onRequested }) {
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [working, setWorking] = useState(false)
  const busyRef = useRef(false)

  const pending = festivalStatus === 'CANCELLATION_PENDING'

  async function handleSubmit(event) {
    event.preventDefault()
    if (busyRef.current) return
    if (!window.confirm('행사 취소를 요청하면 신규 예매가 즉시 중단됩니다. 계속할까요?')) return

    busyRef.current = true
    setWorking(true)
    setError('')
    try {
      const response = await requestFestivalCancellation(festivalId, reason.trim())
      setNotice('취소 요청이 접수됐어요. 운영자 승인 후 전액 환불이 진행돼요.')
      setReason('')
      onRequested?.(response.data.data)
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      busyRef.current = false
      setWorking(false)
    }
  }

  return (
    <section className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 p-5">
      <h2 className="flex items-center gap-2 text-base font-extrabold text-amber-900">
        <TriangleAlertIcon className="h-5 w-5" />
        주최자 귀책 행사 취소
      </h2>
      <p className="mt-1 text-sm text-amber-800">
        운영자 승인 후 입장 여부와 무관하게 남은 티켓을 위약금 없이 전액 환불해요. 요청 즉시 신규 예매가 중단되며 되돌릴 수 없어요.
      </p>

      {notice && (
        <p role="status" className="mt-4 rounded-xl bg-white px-3.5 py-3 text-sm font-semibold text-amber-900">
          {notice}
        </p>
      )}

      {error && (
        <p role="alert" className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
          <CircleAlertIcon className="h-4 w-4" />
          {error}
        </p>
      )}

      {pending ? (
        <p className="mt-4 text-sm font-semibold text-amber-900">취소 요청이 접수되어 운영자 승인을 기다리고 있어요.</p>
      ) : (
        <form onSubmit={handleSubmit} className="mt-4 flex flex-wrap items-end gap-2">
          <label className="min-w-0 flex-1 text-sm font-bold text-amber-900">
            취소 사유
            <input
              type="text"
              required
              maxLength={500}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="참가자에게 안내할 취소 사유"
              className="mt-1.5 w-full rounded-xl border border-amber-200 bg-white px-3 py-2.5 text-sm text-gray-900 outline-none focus:border-amber-400 focus:ring-2 focus:ring-amber-100"
            />
          </label>
          <button
            type="submit"
            disabled={working || !reason.trim()}
            className="inline-flex items-center gap-2 rounded-2xl bg-amber-900 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-amber-800 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            취소 승인 요청
          </button>
        </form>
      )}
    </section>
  )
}

export default HostFestivalCancellation
