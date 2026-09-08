import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import * as PortOne from '@portone/browser-sdk/v2'
import {
  ArrowRightIcon,
  CircleAlertIcon,
  CircleCheckIcon,
  CreditCardIcon,
  LoaderCircleIcon,
  LockIcon,
  TicketIcon,
} from 'lucide-react'
import { fetchFestivalDetail } from '../api/festivalApi'
import { cancelReservation, createReservation, fetchReservationDetail } from '../api/reservationApi'
import { completePayment, preparePayment } from '../api/paymentApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './ReservationCheckout.module.css'

//지원하는 결제수단. PortOne.requestPayment()에 넘길 payMethod/virtualAccount 값을 여기서
//결정한다(https://developers.portone.io/opi/ko/integration/pg/v2/readme?v=v2 — PG사별
//결제수단 코드 기준).
//
//카카오페이는 별도 버튼 없이 '카드 결제' 위젯 안에서 사용자가 카카오페이 퀵버튼을 직접
//선택하는 방식으로 지원된다(2026-09-07 실제 테스트 결제로 확인된 유일한 성공 경로 —
//응답: method.type이 "PaymentMethodEasyPay", provider가 "KAKAOPAY"로 찍힘. CARD 요청이었는데도
//결과는 간편결제로 나온다). payMethod: 'EASY_PAY' 직접 요청은 실제 브라우저로 검증해본 적이
//없어 이 채널에서 되는지 불확실하다 — 그래서 실증된 CARD 경로를 그대로 쓴다.
//
//무통장입금(가상계좌)도 2026-09-07 실제 테스트 결제로 이 파라미터 그대로 성공 확인됨
//(응답: status VIRTUAL_ACCOUNT_ISSUED, method.bank/accountNumber/expiredAt 정상 수신).
//결제 완료 API도 이미 그 상태를 처리한다(Task 7-4 handleVirtualAccountIssued).
const PAY_METHODS = [
  { key: 'CARD', label: '카드 결제', toRequest: () => ({ payMethod: 'CARD' }) },
  {
    key: 'VIRTUAL_ACCOUNT',
    label: '무통장입금',
    // accountExpiry는 타입 정의상 선택값이지만 실제로는 PG(토스페이먼츠 등) 쪽에서 필수로
    // 요구한다 — 없이 호출하면 SDK가 "data.virtualAccount.accountExpiry 파라미터는 필수
    // 입력입니다" 에러를 던진다(실제 호출로 확인). 입금 기한은 24시간으로 잡는다.
    toRequest: () => ({ payMethod: 'VIRTUAL_ACCOUNT', virtualAccount: { accountExpiry: { validHours: 24 } } }),
  },
]

//버튼을 누른 뒤 결제창이 뜨기까지 예매 신청→결제 준비 두 단계를 순서대로 거친다(결제 준비는
//예매가 실제로 존재해야 검증할 수 있어 병렬화할 수 없다). 그동안 화면이 멈춘 것처럼 보이지
//않도록 단계별 문구를 보여준다.
const STEP_LABELS = {
  reserving: '예매를 확인하고 있어요…',
  preparing: '결제를 준비하고 있어요…',
  opening: '결제창을 여는 중이에요…',
}

//결제창이 이 시간 안에 응답하지 않으면 포기하고 사용자에게 알린다. '카드 결제'를 눌러도
//위젯 안에서 카카오페이 같은 QR/앱 승인형 간편결제를 고를 수 있는데, 이 경우 사용자가
//QR을 스캔하고 카카오톡 앱에서 직접 승인하는 단계가 끼기 때문에 정상적으로도 수십 초가
//걸릴 수 있다(실제로 20초로 뒀을 때 정상 결제 도중 타임아웃이 발생함, 2026-09-08).
//완전히 막힌 경우(팝업 차단 등)를 걸러내는 안전장치이므로, 정상적인 사람의 승인 대기
//시간까지 넉넉히 포함하도록 넉넉히 잡는다.
const PAYMENT_WIDGET_TIMEOUT_MS = 120_000

const CREATE_RESERVATION_ERROR_MESSAGES = {
  FESTIVAL_NOT_PUBLISHED: '예매할 수 없는 페스티벌이에요.',
  TICKET_TYPE_NOT_FOUND: '존재하지 않는 티켓 종류예요.',
  STOCK_EXCEEDED: '남은 재고가 부족해요.',
  PURCHASE_LIMIT_EXCEEDED: '1인당 구매 가능 수량을 초과했어요.',
}

//예매 보유(hold) 남은 시간을 "MM:SS"로 표시한다.
function formatRemaining(expiresAt, now) {
  const remainingMs = Math.max(0, new Date(expiresAt).getTime() - now)
  const totalSeconds = Math.floor(remainingMs / 1000)
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

/** 예매 신청 → 결제 준비 → PortOne 결제창 → 결제 확인까지 한 화면에서 진행한다. */
function ReservationCheckout() {
  const { id: festivalId } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const ticketTypeId = Number(searchParams.get('ticketTypeId'))
  const quantity = Number(searchParams.get('quantity')) || 1
  const resumeReservationId = searchParams.get('reservationId')
  const { user, isLoading: authLoading } = useAuth()

  const [festival, setFestival] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [step, setStep] = useState('idle') // idle | reserving | preparing | opening | success | virtualAccountIssued
  const [payError, setPayError] = useState('')
  const [payMethod, setPayMethod] = useState(PAY_METHODS[0].key)
  // 결제창 타임아웃·취소 후 재시도할 때 매번 새 예매를 만들면 이전 PENDING 예매가 그대로
  // 재고를 쥔 채 쌓인다(각 예매가 10분 뒤 개별 만료되기 전까지). 같은 화면에서의 재시도는
  // 기존 예매를 그대로 재사용해 결제만 다시 준비하도록 한다.
  const reservationIdRef = useRef(null)
  // 예매 보유 만료 시각. 새로 예매를 만든 시점(또는 "결제 이어하기"로 재진입한 시점)부터
  // 화면에 남은 시간을 보여주기 위해 별도 상태로 들고 있는다.
  const [expiresAt, setExpiresAt] = useState(null)
  const [now, setNow] = useState(Date.now())
  // "결제 이어하기"로 들어온 경우, 기존 예매 조회가 끝나기 전에 결제 버튼을 누르면
  // reservationIdRef가 아직 비어있어 새 예매를 또 만들어버린다. 조회가 끝날 때까지 버튼을 막는다.
  const [resuming, setResuming] = useState(Boolean(resumeReservationId))
  // 페스티벌 상세의 "예매하기"를 누른 시점(=이 화면에 들어온 시점)부터 10분 보유 타이머가
  // 시작되어야 하므로, "결제하기" 버튼을 누르기 전에 이 화면 진입과 동시에 예매를 만든다.
  const creatingRef = useRef(false)

  const isProcessing = step === 'reserving' || step === 'preparing' || step === 'opening' || resuming

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

  // "결제 이어하기"로 들어온 경우: 새로 예매를 만들지 않고 기존 예매를 그대로 이어받는다.
  useEffect(() => {
    if (!resumeReservationId) return
    let cancelled = false
    fetchReservationDetail(resumeReservationId)
      .then((response) => {
        if (cancelled) return
        const reservation = response.data.data
        if (reservation.reservationStatus !== 'PENDING') {
          setLoadError('이미 처리되었거나 만료된 예매예요.')
          return
        }
        reservationIdRef.current = reservation.id
        setExpiresAt(reservation.expiresAt)
      })
      .catch(() => {
        if (!cancelled) setLoadError('예매 정보를 불러오지 못했어요.')
      })
      .finally(() => {
        if (!cancelled) setResuming(false)
      })
    return () => {
      cancelled = true
    }
  }, [resumeReservationId])

  // 남은 시간 표시용 1초 틱.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const ticketType = festival?.ticketTypes.find((t) => t.id === ticketTypeId)

  // "결제 이어하기"가 아닌 일반 진입: 페스티벌 정보 확인이 끝나는 즉시 예매를 만들어 10분
  // 보유 타이머를 바로 시작한다("결제하기" 버튼을 누를 때까지 미루지 않는다).
  useEffect(() => {
    if (resumeReservationId || !user || !ticketType || creatingRef.current || reservationIdRef.current) return
    creatingRef.current = true
    setStep('reserving')
    createReservation({ festivalId: Number(festivalId), ticketTypeId, quantity })
      .then((reservationRes) => {
        reservationIdRef.current = reservationRes.data.data.id
        setExpiresAt(reservationRes.data.data.expiresAt)
        setStep('idle')
      })
      .catch((error) => {
        const errorCode = error.response?.data?.errorCode
        setPayError(CREATE_RESERVATION_ERROR_MESSAGES[errorCode] || '예매 신청 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.')
        setStep('idle')
      })
  }, [resumeReservationId, user, ticketType, festivalId, ticketTypeId, quantity])

  const totalAmount = ticketType ? ticketType.price * quantity : 0

  async function handlePay() {
    if (!ticketType) return
    setPayError('')
    setStep('reserving')

    try {
      if (!reservationIdRef.current) {
        const reservationRes = await createReservation({
          festivalId: Number(festivalId),
          ticketTypeId,
          quantity,
        })
        reservationIdRef.current = reservationRes.data.data.id
        setExpiresAt(reservationRes.data.data.expiresAt)
      }

      setStep('preparing')
      let prepareRes
      try {
        prepareRes = await preparePayment(reservationIdRef.current)
      } catch (prepareError) {
        const prepareErrorCode = prepareError.response?.data?.errorCode
        // 재사용하려던 예매가 그새 만료·완료 등으로 결제 불가 상태가 된 경우에만 새로 예매해서
        // 한 번 더 시도한다. 그 외 에러는 그대로 바깥 catch로 던져 평소처럼 처리한다.
        if (prepareErrorCode !== 'RESERVATION_NOT_PAYABLE' && prepareErrorCode !== 'RESERVATION_NOT_FOUND') {
          throw prepareError
        }
        const reservationRes = await createReservation({
          festivalId: Number(festivalId),
          ticketTypeId,
          quantity,
        })
        reservationIdRef.current = reservationRes.data.data.id
        setExpiresAt(reservationRes.data.data.expiresAt)
        prepareRes = await preparePayment(reservationIdRef.current)
      }
      const { paymentId, storeId, channelKey, totalAmount: amount } = prepareRes.data.data

      setStep('opening')
      const selectedMethod = PAY_METHODS.find((m) => m.key === payMethod) ?? PAY_METHODS[0]
      // 카드 외 결제수단(간편결제·무통장입금 등)은 PG·채널 설정에 따라 위젯 자체가 뜨지 않고
      // Promise가 영영 안 끝나는 경우가 실제로 있었다(파라미터는 정상인데도 카드만 응답,
      // 나머지는 무한 대기 — 채널에 해당 결제수단이 활성화돼 있지 않을 때의 증상으로 보인다).
      // 버튼이 영구히 멈추지 않도록 타임아웃을 두고, 시간 초과 시 다른 수단을 안내한다.
      const paymentResult = await Promise.race([
        PortOne.requestPayment({
          storeId,
          channelKey,
          paymentId,
          orderName: `${festival.name} - ${ticketType.name} x ${quantity}`,
          totalAmount: amount,
          currency: 'KRW',
          ...selectedMethod.toRequest(),
        }),
        new Promise((_, reject) =>
          setTimeout(
            () => reject(new Error(`PAYMENT_WIDGET_TIMEOUT:${selectedMethod.label}`)),
            PAYMENT_WIDGET_TIMEOUT_MS,
          ),
        ),
      ])

      if (paymentResult?.code != null) {
        // 사용자가 결제창을 닫았거나 PG사에서 거절한 경우. 예매는 PENDING으로 남아있다가
        // 10분 뒤 자동 만료되므로 여기서 별도로 취소 처리하지 않는다.
        setPayError(paymentResult.message || '결제가 취소되었어요.')
        setStep('idle')
        return
      }

      const completeRes = await completePayment(paymentId)
      // 무통장입금은 이 시점에 입금이 끝난 게 아니라 계좌가 발급된 것뿐이라(백엔드가
      // VIRTUAL_ACCOUNT_ISSUED로 기록), 카드·카카오페이처럼 바로 '완료' 화면을 보여주면 안 된다.
      // 실제 입금 확인은 PortOne 웹훅이 비동기로 처리한다(Task 7-5).
      setStep(completeRes.data.data.status === 'VIRTUAL_ACCOUNT_ISSUED' ? 'virtualAccountIssued' : 'success')
    } catch (error) {
      if (error?.message?.startsWith('PAYMENT_WIDGET_TIMEOUT:')) {
        const methodLabel = error.message.split(':')[1]
        setPayError(`${methodLabel} 결제창이 응답하지 않아요. 다른 결제 수단으로 다시 시도해주세요.`)
        setStep('idle')
        return
      }
      const errorCode = error.response?.data?.errorCode
      setPayError(CREATE_RESERVATION_ERROR_MESSAGES[errorCode] || '결제 처리 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.')
      setStep('idle')
    }
  }

  async function handleCancel() {
    if (!reservationIdRef.current) {
      navigate(`/festivals/${festivalId}`)
      return
    }
    if (!window.confirm('예매를 취소할까요? 재고가 다시 풀려요.')) return
    try {
      await cancelReservation(reservationIdRef.current)
    } catch {
      // 이미 만료·취소된 예매에 대한 재취소 시도는 무시하고 그냥 나간다.
    }
    navigate(`/festivals/${festivalId}`)
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

  if (step === 'virtualAccountIssued') {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleCheckIcon size={40} aria-hidden="true" className={styles.infoIconSuccess} />
            <h1 className={styles.infoTitle}>입금 계좌가 발급되었어요</h1>
            <p className={styles.infoDescription}>
              안내된 계좌로 입금하면 자동으로 결제가 확인돼요. 입금 전까지는 예매가 확정되지 않으니
              발급 화면에 표시된 입금 기한을 확인해주세요.
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

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>예매 확인</h1>

        {expiresAt && (
          <p className={styles.holdTimer} role="status">
            예매 보유 시간 {formatRemaining(expiresAt, now)} 남음
          </p>
        )}

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

        <fieldset className={styles.payMethods} disabled={isProcessing}>
          <legend className={styles.payMethodsLegend}>결제 수단</legend>
          {PAY_METHODS.map((method) => (
            <label key={method.key} className={styles.payMethodOption}>
              <input
                type="radio"
                name="payMethod"
                value={method.key}
                checked={payMethod === method.key}
                onChange={() => setPayMethod(method.key)}
              />
              {method.label}
            </label>
          ))}
        </fieldset>

        {payError && (
          <p className={styles.submitError} role="alert">
            <CircleAlertIcon size={16} aria-hidden="true" />
            {payError}
          </p>
        )}

        <button
          type="button"
          className={styles.submit}
          disabled={isProcessing}
          onClick={handlePay}
        >
          {isProcessing ? (
            <>
              <LoaderCircleIcon size={18} aria-hidden="true" className={styles.spinner} />
              {resuming ? '예매 정보를 불러오고 있어요…' : STEP_LABELS[step]}
            </>
          ) : (
            <>
              <CreditCardIcon size={16} aria-hidden="true" />
              {`${totalAmount.toLocaleString()}원 결제하기`}
              <ArrowRightIcon size={16} aria-hidden="true" />
            </>
          )}
        </button>

        <button
          type="button"
          className={styles.cancelLink}
          disabled={isProcessing}
          onClick={handleCancel}
        >
          예매 취소하고 나가기
        </button>
      </div>
    </main>
  )
}

export default ReservationCheckout
