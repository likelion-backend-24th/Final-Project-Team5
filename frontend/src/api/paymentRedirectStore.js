export const PAYMENT_REDIRECT_PATH = '/payments/redirect'

const STORAGE_KEY = 'pendingPaymentRedirect'
const PENDING_PAYMENT_TTL_MS = 15 * 60 * 1000

function removePendingPayment() {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 저장소 접근이 차단된 환경에서는 남은 값이 없다고 취급한다.
  }
}

function readPendingPayment(now = Date.now()) {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }

    const pending = JSON.parse(raw)
    const isValid =
      typeof pending.paymentId === 'string' &&
      Number.isFinite(pending.createdAt) &&
      now - pending.createdAt >= 0 &&
      now - pending.createdAt <= PENDING_PAYMENT_TTL_MS

    if (!isValid) {
      removePendingPayment()
      return null
    }
    return pending
  } catch {
    removePendingPayment()
    return null
  }
}

// 모바일 PG 리다이렉트에서 같은 탭의 결제만 복구할 수 있도록 짧게 보관한다.
export function savePendingPaymentRedirect({ paymentId, reservationId }) {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ paymentId, reservationId, createdAt: Date.now() }),
  )
}

// 현재 탭에서 시작한 유효한 결제 복귀 정보를 읽는다.
export function getPendingPaymentRedirect() {
  return readPendingPayment()
}

// 현재 결제와 일치할 때만 모바일 복귀 정보를 제거한다.
export function clearPendingPaymentRedirect(paymentId) {
  const pending = readPendingPayment()
  if (!paymentId || pending?.paymentId === paymentId) {
    removePendingPayment()
  }
}

// PortOne이 결제 후 돌아올 현재 서비스의 절대 URL을 만든다.
export function getPaymentRedirectUrl() {
  return new URL(PAYMENT_REDIRECT_PATH, window.location.origin).toString()
}

// 외부 결제창 체류는 탭 종료가 아니므로, 일치하는 결제 반환에만 세션 연속성을 인정한다.
export function isExpectedPaymentRedirectReturn() {
  if (typeof window === 'undefined' || window.location.pathname !== PAYMENT_REDIRECT_PATH) {
    return false
  }

  const paymentId = new URLSearchParams(window.location.search).get('paymentId')
  return Boolean(paymentId && readPendingPayment()?.paymentId === paymentId)
}
