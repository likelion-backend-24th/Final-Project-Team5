import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowRightIcon, CircleAlertIcon, CircleCheckIcon, LoaderCircleIcon } from 'lucide-react'
import { completePayment } from '../api/paymentApi'
import {
  clearPendingPaymentRedirect,
  getPendingPaymentRedirect,
} from '../api/paymentRedirectStore'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './ReservationCheckout.module.css'

const PAYMENT_REDIRECT_ERROR_MESSAGES = {
  FORBIDDEN_PAYMENT_OWNER: '본인의 결제만 확인할 수 있어요.',
  PAYMENT_NOT_FOUND: '결제 정보를 찾을 수 없어요.',
  PAYMENT_NOT_YET_PROCESSED: '결제 처리가 아직 끝나지 않았어요. 잠시 후 다시 확인해주세요.',
  PAYMENT_VERIFICATION_FAILED: '결제 정보가 일치하지 않아 확인하지 못했어요.',
  // 같은 예매를 다른 탭에서 먼저 결제했거나 결제 중 예매가 만료된 경우 — 서버가 이 결제를 자동으로 전액 환불한다.
  RESERVATION_ALREADY_FINALIZED: '이 예매는 이미 결제가 끝났거나 만료됐어요. 방금 결제는 자동으로 취소돼 환불돼요.',
}

function toErrorMessage(error) {
  const errorCode = error.response?.data?.errorCode
  return PAYMENT_REDIRECT_ERROR_MESSAGES[errorCode]
    || '결제 결과를 확인하지 못했어요. 잠시 후 다시 시도해주세요.'
}

/** 모바일 PG 리다이렉트 결과를 검증하고 서버의 결제 완료 처리로 연결한다. */
function PaymentRedirect() {
  const [searchParams] = useSearchParams()
  const { user, isLoading: authLoading } = useAuth()
  const [result, setResult] = useState({ status: 'verifying', message: '', retryable: false })
  const [attempt, setAttempt] = useState(0)

  const paymentId = searchParams.get('paymentId')
  const portOneErrorCode = searchParams.get('code')
  const portOneErrorMessage = searchParams.get('message')

  useEffect(() => {
    if (authLoading) return undefined

    let cancelled = false
    async function verifyPayment() {
      await Promise.resolve()
      if (cancelled) return

      const pending = getPendingPaymentRedirect()
      if (!paymentId || pending?.paymentId !== paymentId) {
        setResult({
          status: 'error',
          message: '이 브라우저에서 시작한 결제 정보를 확인할 수 없어요.',
          retryable: false,
        })
        return
      }

      if (portOneErrorCode) {
        clearPendingPaymentRedirect(paymentId)
        setResult({
          status: 'error',
          message: portOneErrorMessage || '결제가 취소되었거나 처리되지 않았어요.',
          retryable: false,
        })
        return
      }

      if (!user) {
        setResult({
          status: 'error',
          message: '로그인 정보를 확인할 수 없어요. 다시 로그인한 뒤 예매 내역을 확인해주세요.',
          retryable: false,
        })
        return
      }

      try {
        const response = await completePayment(paymentId)
        if (cancelled) return
        clearPendingPaymentRedirect(paymentId)
        const status = response.data.data.status === 'VIRTUAL_ACCOUNT_ISSUED'
          ? 'virtualAccountIssued'
          : 'success'
        setResult({ status, message: '', retryable: false })
      } catch (error) {
        if (cancelled) return
        setResult({ status: 'error', message: toErrorMessage(error), retryable: true })
      }
    }
    verifyPayment()

    return () => {
      cancelled = true
    }
  }, [attempt, authLoading, paymentId, portOneErrorCode, portOneErrorMessage, user])

  if (authLoading || result.status === 'verifying') {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState} role="status">
            <LoaderCircleIcon
              size={40}
              aria-hidden="true"
              className={`${styles.infoIconMuted} ${styles.spinner}`}
            />
            <h1 className={styles.infoTitle}>결제 결과를 확인하고 있어요</h1>
            <p className={styles.infoDescription}>화면을 닫지 말고 잠시만 기다려주세요.</p>
          </div>
        </div>
      </main>
    )
  }

  if (result.status === 'error') {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleAlertIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>결제 결과를 확인하지 못했어요</h1>
            <p className={styles.infoDescription}>{result.message}</p>
            {result.retryable && (
              <button
                type="button"
                className={styles.infoButton}
                onClick={() => {
                  setResult({ status: 'verifying', message: '', retryable: false })
                  setAttempt((value) => value + 1)
                }}
              >
                다시 확인하기
              </button>
            )}
            <Link to="/reservations" className={result.retryable ? styles.infoLink : styles.infoButton}>
              내 예약 확인하기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </main>
    )
  }

  const virtualAccountIssued = result.status === 'virtualAccountIssued'
  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <div className={styles.infoState}>
          <CircleCheckIcon size={40} aria-hidden="true" className={styles.infoIconSuccess} />
          <h1 className={styles.infoTitle}>
            {virtualAccountIssued ? '입금 계좌가 발급되었어요' : '결제가 완료되었어요'}
          </h1>
          <p className={styles.infoDescription}>
            {virtualAccountIssued
              ? '안내된 계좌로 입금하면 자동으로 결제가 확인돼요. 내 예약에서 입금 상태를 확인해주세요.'
              : '내 예약에서 티켓과 입장용 QR을 확인할 수 있어요.'}
          </p>
          <Link to="/reservations" className={styles.infoButton}>
            내 예약 보러가기
            <ArrowRightIcon size={16} aria-hidden="true" />
          </Link>
        </div>
      </div>
    </main>
  )
}

export default PaymentRedirect
