import { useRef, useState } from 'react'
import { TriangleAlertIcon } from 'lucide-react'
import { requestFestivalCancellation } from '../api/hostFestivalApi'
import { inputClass, primaryButton } from './settlementPresentation'

const CANCELLATION_ERROR_MESSAGES = {
  FESTIVAL_NOT_CANCELLABLE: '공개 또는 종료 상태의 페스티벌만 취소할 수 있습니다.',
  CANCEL_REASON_REQUIRED: '취소 사유는 필수입니다.',
  FORBIDDEN_HOST_ROLE: '주최자만 행사 취소를 요청할 수 있습니다.',
}

export default function HostFestivalCancellation({ festivalId }) {
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)

  async function submit() {
    if (lock.current || !window.confirm('행사 취소를 요청하면 신규 예매가 중단됩니다. 계속할까요?')) return
    lock.current = true
    setBusy(true)
    try {
      await requestFestivalCancellation(festivalId, reason)
      setMessage('취소 요청이 접수되었습니다. 운영자 승인 후 전액 환불이 진행됩니다.')
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
    <section className="my-6 space-y-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
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
      <p className="text-sm leading-relaxed text-amber-900">
        운영자 승인 후 입장 여부와 무관하게 남은 티켓을 위약금 없이 전액 환불합니다.
      </p>
      <label className="block space-y-2 text-sm font-semibold text-amber-900">
        취소 사유
        <input
          className={inputClass}
          maxLength={500}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>
      <button
        className={primaryButton}
        disabled={busy || !reason.trim()}
        onClick={submit}
      >
        취소 승인 요청
      </button>
    </section>
  )
}
