import { useState } from 'react'
import { CircleAlertIcon, XIcon } from 'lucide-react'
import { suspendUser } from '../../api/adminApi'

const REASON_MAX_LENGTH = 255

/** 회원 정지 사유 입력 모달. RefundModal의 중앙 오버레이 구조를 따른다. */
function SuspendUserModal({ member, onClose, onSuspended }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit() {
    if (submitting) return
    const trimmed = reason.trim()
    if (!trimmed) {
      setError('정지 사유를 입력해주세요.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await suspendUser(member.id, trimmed)
      onSuspended()
    } catch (requestError) {
      setError(requestError.response?.data?.message || '정지 처리에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-6">
      <div className="w-full max-w-sm rounded-3xl bg-white p-6 shadow-lg">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-extrabold text-gray-900">회원 정지</h3>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            aria-label="닫기"
            className="text-gray-400 hover:text-gray-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        <p className="mt-4 text-sm text-gray-600">
          <span className="font-bold text-gray-900">{member.nickname}</span> 님을 정지할까요?
        </p>

        <div className="mt-4">
          <label htmlFor="suspend-reason" className="block text-sm font-bold text-gray-900">
            정지 사유
          </label>
          <textarea
            id="suspend-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value.slice(0, REASON_MAX_LENGTH))}
            disabled={submitting}
            placeholder="정지 사유를 입력하세요"
            rows={4}
            className="mt-2 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <p className="mt-1 text-right text-xs text-gray-400">
            {reason.length}/{REASON_MAX_LENGTH}
          </p>
        </div>

        {error && (
          <p role="alert" className="mt-2 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
            <CircleAlertIcon className="h-4 w-4" />
            {error}
          </p>
        )}

        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-xl border border-gray-200 px-4 py-2 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="rounded-xl bg-red-600 px-4 py-2 text-sm font-bold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            {submitting ? '처리 중…' : '정지'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default SuspendUserModal
