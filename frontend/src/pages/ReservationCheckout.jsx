import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import * as PortOne from '@portone/browser-sdk/v2'
import { ArrowRightIcon, CircleAlertIcon, CircleCheckIcon, LockIcon, TicketIcon } from 'lucide-react'
import { fetchFestivalDetail } from '../api/festivalApi'
import { createReservation } from '../api/reservationApi'
import { completePayment, preparePayment } from '../api/paymentApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './ReservationCheckout.module.css'

//결제 준비 API가 내려주는 값만으로 requestPayment()를 호출한다. 카드 결제만 지원한다
//(가상계좌 등 다른 결제수단은 백엔드가 이미 처리 가능하지만, 화면은 이번 범위에서 카드만 다룬다).
const PAY_METHOD = 'CARD'

const CREATE_RESERVATION_ERROR_MESSAGES = {
  FESTIVAL_NOT_PUBLISHED: '예매할 수 없는 페스티벌이에요.',
  TICKET_TYPE_NOT_FOUND: '존재하지 않는 티켓 종류예요.',
  STOCK_EXCEEDED: '남은 재고가 부족해요.',
  PURCHASE_LIMIT_EXCEEDED: '1인당 구매 가능 수량을 초과했어요.',
}

/** 예매 신청 → 결제 준비 → PortOne 결제창 → 결제 확인까지 한 화면에서 진행한다. */
function ReservationCheckout() {
  const { id: festivalId } = useParams()
  const [searchParams] = useSearchParams()
  const ticketTypeId = Number(searchParams.get('ticketTypeId'))
  const quantity = Number(searchParams.get('quantity')) || 1
  const { user, isLoading: authLoading } = useAuth()

  const [festival, setFestival] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [step, setStep] = useState('idle') // idle | processing | success
  const [payError, setPayError] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchFestivalDetail(festivalId)
      .then((response) => {
        if (!cancelled) setFestival(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('페스티벌 정보를 불러오지 못했어요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [festivalId])

  const ticketType = festival?.ticketTypes.find((t) => t.id === ticketTypeId)
  const totalAmount = ticketType ? ticketType.price * quantity : 0

  async function handlePay() {
    if (!ticketType) return
    setPayError('')
    setStep('processing')

    try {
      const reservationRes = await createReservation({
        festivalId: Number(festivalId),
        ticketTypeId,
        quantity,
      })
      const reservationId = reservationRes.data.data.id

      const prepareRes = await preparePayment(reservationId)
      const { paymentId, storeId, channelKey, totalAmount: amount } = prepareRes.data.data

      const paymentResult = await PortOne.requestPayment({
        storeId,
        channelKey,
        paymentId,
        orderName: `${festival.name} - ${ticketType.name} x ${quantity}`,
        totalAmount: amount,
        currency: 'KRW',
        payMethod: PAY_METHOD,
      })

      if (paymentResult?.code != null) {
        // 사용자가 결제창을 닫았거나 PG사에서 거절한 경우. 예매는 PENDING으로 남아있다가
        // 10분 뒤 자동 만료되므로 여기서 별도로 취소 처리하지 않는다.
        setPayError(paymentResult.message || '결제가 취소되었어요.')
        setStep('idle')
        return
      }

      await completePayment(paymentId)
      setStep('success')
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      setPayError(CREATE_RESERVATION_ERROR_MESSAGES[errorCode] || '결제 처리 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.')
      setStep('idle')
    }
  }

  if (authLoading || loading) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <p className={styles.loading}>불러오는 중…</p>
        </div>
      </main>
    )
  }

  if (!user) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <LockIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>로그인이 필요해요</h1>
            <p className={styles.infoDescription}>예매를 진행하려면 먼저 로그인해주세요.</p>
            <Link to="/login" className={styles.infoButton}>
              로그인하러 가기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (loadError || !ticketType) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleAlertIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>예매 정보를 확인할 수 없어요</h1>
            <p className={styles.infoDescription}>{loadError || '요청하신 티켓 종류를 찾을 수 없어요.'}</p>
            <Link to={`/festivals/${festivalId}`} className={styles.infoLink}>
              페스티벌로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (step === 'success') {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleCheckIcon size={40} aria-hidden="true" className={styles.infoIconSuccess} />
            <h1 className={styles.infoTitle}>결제가 완료되었어요</h1>
            <p className={styles.infoDescription}>내 예약에서 티켓과 입장용 QR을 확인할 수 있어요.</p>
            <Link to="/reservations" className={styles.infoButton}>
              내 예약 보러가기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>예매 확인</h1>

        <div className={styles.summary}>
          <p className={styles.festivalName}>{festival.name}</p>

          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>
              <TicketIcon size={16} aria-hidden="true" />
              티켓 종류
            </span>
            <span className={styles.summaryValue}>{ticketType.name}</span>
          </div>
          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>수량</span>
            <span className={styles.summaryValue}>{quantity}장</span>
          </div>
          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>단가</span>
            <span className={styles.summaryValue}>{ticketType.price.toLocaleString()}원</span>
          </div>
          <div className={`${styles.summaryRow} ${styles.summaryTotal}`}>
            <span className={styles.summaryLabel}>총 결제 금액</span>
            <span className={styles.summaryValue}>{totalAmount.toLocaleString()}원</span>
          </div>
        </div>

        {payError && (
          <p className={styles.submitError} role="alert">
            <CircleAlertIcon size={16} aria-hidden="true" />
            {payError}
          </p>
        )}

        <button
          type="button"
          className={styles.submit}
          disabled={step === 'processing'}
          onClick={handlePay}
        >
          {step === 'processing' ? '결제 진행 중…' : `${totalAmount.toLocaleString()}원 결제하기`}
          <ArrowRightIcon size={16} aria-hidden="true" />
        </button>
      </div>
    </main>
  )
}

export default ReservationCheckout
