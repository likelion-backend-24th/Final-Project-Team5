import { useEffect, useState } from 'react'
import { CircleAlertIcon, XIcon } from 'lucide-react'
import { fetchRefundQuote } from '../api/reservationApi'
import { requestRefund } from '../api/paymentApi'

//환불이 거절되는 이유는 현장 대응이 달라지므로 하나로 뭉뚱그리지 않고 구분해 안내한다.
const REFUND_ERROR_MESSAGES = {
  REFUND_WINDOW_CLOSED: '공연 시작 24시간 이내에는 환불할 수 없어요.',
  ALREADY_CHECKED_IN_NOT_REFUNDABLE: '이미 입장한 예매는 환불할 수 없어요.',
  REFUND_QUANTITY_EXCEEDED: '환불 가능한 수량을 초과했어요.',
  RESERVATION_NOT_REFUNDABLE: '환불할 수 없는 예매 상태예요.',
  REFUND_NOT_ALLOWED: '환불할 수 없는 예매예요.',
  PAYMENT_NOT_CANCELLABLE: '이미 취소된 결제예요.',
  REFUND_FAILED: '결제사 취소 요청에 실패했어요. 잠시 후 다시 시도해주세요.',
}

function toErrorMessage(error) {
  const errorCode = error.response?.data?.errorCode
  return REFUND_ERROR_MESSAGES[errorCode] || '환불 요청 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.'
}

/**
 * 환불 요청 모달. 위약금 정책이 날짜 구간별로 달라서, 확정 전에 서버 견적으로
 * "얼마가 빠지고 얼마를 돌려받는지"를 반드시 먼저 보여준다.
 */
function RefundModal({ reservation, onClose, onRefunded }) {
  const refundableQuantity = reservation.quantity - (reservation.refundedQuantity ?? 0)

  const [quantity, setQuantity] = useState(refundableQuantity)
  const [quote, setQuote] = useState(null)
  const [loadingQuote, setLoadingQuote] = useState(true)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  //수량을 바꿀 때마다 위약금이 달라지므로 견적을 다시 받는다.
  useEffect(() => {
    let cancelled = false
    setLoadingQuote(true)
    fetchRefundQuote(reservation.id, quantity)
      .then((response) => {
        if (!cancelled) {
          setQuote(response.data.data)
          setError('')
        }
      })
      .catch((requestError) => {
        if (!cancelled) setError(toErrorMessage(requestError))
      })
      .finally(() => {
        if (!cancelled) setLoadingQuote(false)
      })
    return () => {
      cancelled = true
    }
  }, [reservation.id, quantity])

  async function handleSubmit() {
    if (submitting) return
    setSubmitting(true)
    setError('')
    try {
      await requestRefund(reservation.paymentId, { quantity, reason: '참가자 환불 요청' })
      onRefunded(reservation.id, quantity)
    } catch (requestError) {
      setError(toErrorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  const blocked = Boolean(quote && !quote.refundable)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-6">
      <div className="w-full max-w-sm rounded-3xl bg-white p-6 shadow-lg">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-extrabold text-gray-900">환불 요청</h3>
          <button type="button" onClick={onClose} aria-label="닫기" className="text-gray-400 hover:text-gray-600">
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        {refundableQuantity > 1 && (
          <div className="mt-5">
            <label htmlFor="refund-quantity" className="block text-sm font-bold text-gray-900">
              환불할 수량
            </label>
            <p className="mt-1 text-xs text-gray-400">일부만 환불할 수 있어요. 남은 티켓은 그대로 입장할 수 있어요.</p>
            <select
              id="refund-quantity"
              value={quantity}
              onChange={(event) => setQuantity(Number(event.target.value))}
              disabled={submitting}
              className="mt-2 w-full rounded-2xl border border-gray-300 bg-white px-4 py-3 text-[15px] text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            >
              {Array.from({ length: refundableQuantity }, (_, index) => index + 1).map((value) => (
                <option key={value} value={value}>
                  {value}장
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="mt-5 rounded-2xl bg-gray-50 p-4">
          {loadingQuote && <p className="text-sm text-gray-500">환불 금액을 계산하는 중…</p>}

          {!loadingQuote && quote && quote.refundable && (
            <dl className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">환불 대상 금액</dt>
                <dd className="font-semibold text-gray-900">{quote.grossAmount.toLocaleString()}원</dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">취소 수수료 ({quote.feePercent}%)</dt>
                <dd className="font-semibold text-red-600">-{quote.feeAmount.toLocaleString()}원</dd>
              </div>
              <div className="flex items-center justify-between border-t border-gray-200 pt-2">
                <dt className="font-bold text-gray-900">환불 예정 금액</dt>
                <dd className="text-lg font-extrabold text-blue-600">{quote.refundAmount.toLocaleString()}원</dd>
              </div>
            </dl>
          )}

          {!loadingQuote && blocked && (
            <p className="text-sm font-semibold text-red-600">
              {REFUND_ERROR_MESSAGES[quote.rejectReason] || '환불할 수 없는 예매예요.'}
            </p>
          )}
        </div>

        {error && (
          <p role="alert" className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
            <CircleAlertIcon className="h-4 w-4" />
            {error}
          </p>
        )}

        <p className="mt-4 text-xs text-gray-400">
          환불된 티켓은 다시 판매돼요. 취소 수수료는 공연 시작일까지 남은 기간에 따라 달라집니다.
        </p>

        <button
          type="button"
          onClick={handleSubmit}
          disabled={submitting || loadingQuote || blocked}
          className="mt-4 w-full rounded-2xl bg-blue-600 py-3.5 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
        >
          {submitting ? '환불 요청 중…' : '환불 요청하기'}
        </button>
      </div>
    </div>
  )
}

export default RefundModal
