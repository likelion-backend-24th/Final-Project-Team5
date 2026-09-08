import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  CircleAlertIcon,
  CircleCheckIcon,
  KeyboardIcon,
  LockIcon,
  RefreshCwIcon,
  ScanLineIcon,
} from 'lucide-react'
import { fetchFestivalDetail } from '../api/festivalApi'
import { fetchCheckInStats, verifyCheckInCode, verifyQrToken } from '../api/organizerApi'
import { useAuth } from '../context/AuthContext.jsx'
import QrScanner from '../components/QrScanner'

const CHECK_IN_ROLES = ['HOST', 'HELPER']

//현장에서 바로 다음 행동을 판단할 수 있게, 백엔드 사유를 그대로 옮기지 않고 대응 방법까지 담는다.
const CHECK_IN_ERROR_MESSAGES = {
  INVALID_QR_TOKEN: '유효하지 않은 QR이에요. 티켓 화면을 다시 띄워달라고 안내해주세요.',
  INVALID_CHECK_IN_CODE: '유효하지 않은 입장 코드예요. 코드를 다시 확인해주세요.',
  ALREADY_CHECKED_IN: '이미 입장 처리된 티켓이에요.',
  OTHER_FESTIVAL_TICKET: '다른 공연의 티켓이에요.',
  FESTIVAL_NOT_STARTED: '아직 공연 시작 전이라 입장할 수 없어요.',
  RESERVATION_NOT_CONFIRMED: '결제가 완료되지 않은 예매예요.',
  FORBIDDEN_NOT_ORGANIZER: '본인이 주최한 페스티벌의 티켓만 검증할 수 있어요.',
  HELPER_FESTIVAL_NOT_ASSIGNED: '담당 페스티벌이 지정되지 않은 도우미 계정이에요. 주최자에게 계정 재발급을 요청해주세요.',
}

function formatTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm'

function InfoScreen({ title, description, children }) {
  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 py-14">
      <div className={`h-fit w-full max-w-md text-center ${cardClass}`}>
        <LockIcon className="mx-auto h-10 w-10 text-gray-400" />
        <h1 className="mt-4 text-xl font-extrabold text-gray-900">{title}</h1>
        {description && <p className="mt-2 text-sm text-gray-500">{description}</p>}
        {children}
      </div>
    </main>
  )
}

/**
 * 현장 입장 검증 화면. 주최자(HOST)는 /host/festivals/:id/check-in으로, 도우미(HELPER)는
 * 로그인 직후 /check-in으로 들어온다 — 검증 동작이 완전히 같아서 한 화면을 공유한다.
 * 대상 페스티벌은 HOST는 주소에서, HELPER는 계정에 배정된 festivalId에서 가져온다.
 */
function CheckIn() {
  const { id: routeFestivalId } = useParams()
  const { user, isLoading: authLoading } = useAuth()
  const festivalId = routeFestivalId ?? user?.festivalId

  const [festival, setFestival] = useState(null)
  const [stats, setStats] = useState(null)
  const [result, setResult] = useState(null) // { ok: true, reservation } | { ok: false, message }
  const [manualCode, setManualCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  //스캔 루프는 0.2초마다 돌기 때문에, 검증 결과를 확인하는 동안 같은 QR이 계속 재전송되지 않도록
  //state가 반영되기 전에도 곧바로 막을 수 있는 ref로 잠근다. 잠금은 사용자가 결과를 닫을 때 풀린다.
  const busyRef = useRef(false)

  const canCheckIn = CHECK_IN_ROLES.includes(user?.role)

  const refreshStats = useCallback(() => {
    if (!festivalId) return
    fetchCheckInStats(festivalId)
      .then((response) => setStats(response.data.data))
      .catch(() => setStats(null))
  }, [festivalId])

  useEffect(() => {
    if (!festivalId || !canCheckIn) return
    refreshStats()
  }, [festivalId, canCheckIn, refreshStats])

  useEffect(() => {
    if (!festivalId || !canCheckIn) return
    let cancelled = false
    //티켓 종류 이름을 결과 화면에 보여주려고 공개 상세 API를 쓴다(도우미도 접근 가능한 공개 경로).
    fetchFestivalDetail(festivalId)
      .then((response) => {
        if (!cancelled) setFestival(response.data.data)
      })
      .catch(() => {
        //이름을 못 불러와도 입장 검증 자체에는 지장이 없으므로 조용히 넘어간다.
      })
    return () => {
      cancelled = true
    }
  }, [festivalId, canCheckIn])

  async function runVerify(request) {
    setSubmitting(true)
    try {
      const response = await request()
      setResult({ ok: true, reservation: response.data.data })
      refreshStats()
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      setResult({
        ok: false,
        message: CHECK_IN_ERROR_MESSAGES[errorCode] || '입장 처리 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.',
      })
    } finally {
      setSubmitting(false)
    }
  }

  //QrScanner는 이 콜백을 ref에 담아두고 최신 값만 호출하므로, 매 렌더 새로 만들어도 카메라가 다시 열리지 않는다.
  function handleScan(qrToken) {
    if (busyRef.current) return
    busyRef.current = true
    runVerify(() => verifyQrToken(qrToken))
  }

  function handleManualSubmit(event) {
    event.preventDefault()
    const code = manualCode.trim()
    if (!code || busyRef.current) return
    busyRef.current = true
    setManualCode('')
    runVerify(() => verifyCheckInCode(code))
  }

  function dismissResult() {
    setResult(null)
    busyRef.current = false
  }

  if (authLoading) {
    return (
      <main className="flex flex-1 justify-center bg-gray-50 px-6 py-14">
        <p className="text-sm text-gray-500">불러오는 중…</p>
      </main>
    )
  }

  if (!user) {
    return (
      <InfoScreen title="로그인이 필요해요" description="입장 검증은 주최자·도우미 계정만 사용할 수 있어요.">
        <Link
          to="/login"
          className="mt-6 inline-flex items-center justify-center rounded-2xl bg-blue-600 px-5 py-3 text-sm font-bold text-white transition hover:bg-blue-700"
        >
          로그인하러 가기
        </Link>
      </InfoScreen>
    )
  }

  if (!canCheckIn) {
    return <InfoScreen title="주최자·도우미만 이용할 수 있는 화면이에요" />
  }

  if (!festivalId) {
    return (
      <InfoScreen
        title="담당 페스티벌이 지정되지 않았어요"
        description="주최자에게 도우미 계정 재발급을 요청해주세요."
      />
    )
  }

  const ticketTypeName = result?.ok
    ? festival?.ticketTypes?.find((t) => t.id === result.reservation.ticketTypeId)?.name
    : null
  const remainingTickets = stats ? stats.totalTickets - stats.checkedInTickets : 0
  const progress = stats && stats.totalTickets > 0
    ? Math.round((stats.checkedInTickets / stats.totalTickets) * 100)
    : 0

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 py-8">
      <div className="w-full max-w-md space-y-5">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-extrabold tracking-tight text-gray-900">
            <ScanLineIcon className="h-6 w-6 text-blue-600" />
            현장 입장 검증
          </h1>
          <p className="mt-1 text-sm text-gray-500">{festival?.name ?? `페스티벌 #${festivalId}`}</p>
        </div>

        {/* 입장 현황 */}
        <section className={cardClass}>
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-gray-500">입장 현황</h2>
            <button
              type="button"
              onClick={refreshStats}
              className="flex items-center gap-1 rounded-full px-2 py-1 text-xs font-bold text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
            >
              <RefreshCwIcon className="h-3.5 w-3.5" />
              새로고침
            </button>
          </div>

          {stats ? (
            <>
              <p className="mt-2 text-3xl font-extrabold text-gray-900">
                {stats.checkedInTickets}
                <span className="text-lg font-bold text-gray-400"> / {stats.totalTickets}명</span>
              </p>
              <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-gray-100">
                <div className="h-full rounded-full bg-blue-600 transition-all" style={{ width: `${progress}%` }} />
              </div>
              <p className="mt-2 text-xs font-semibold text-gray-500">남은 인원 {remainingTickets}명</p>
            </>
          ) : (
            <p className="mt-2 text-sm text-gray-400">입장 현황을 불러오지 못했어요.</p>
          )}
        </section>

        {/* 검증 결과 — 스캔 결과를 확인하는 동안 스캔은 멈춘다 */}
        {result && (
          <section
            className={`rounded-3xl p-6 text-center ${result.ok ? 'bg-green-50' : 'bg-red-50'}`}
            role="alert"
          >
            {result.ok ? (
              <>
                <CircleCheckIcon className="mx-auto h-12 w-12 text-green-600" />
                <p className="mt-3 text-xl font-extrabold text-green-700">입장 처리 완료</p>
                <p className="mt-1 text-sm font-semibold text-green-800">
                  {ticketTypeName ? `${ticketTypeName} · ` : ''}
                  {result.reservation.quantity}명 입장
                </p>
                <p className="mt-1 text-xs text-green-700/70">{formatTime(result.reservation.checkedInAt)}</p>
              </>
            ) : (
              <>
                <CircleAlertIcon className="mx-auto h-12 w-12 text-red-600" />
                <p className="mt-3 text-lg font-extrabold text-red-700">입장 처리 실패</p>
                <p className="mt-1 text-sm font-semibold text-red-800">{result.message}</p>
              </>
            )}

            <button
              type="button"
              onClick={dismissResult}
              className="mt-5 w-full rounded-2xl bg-gray-900 py-3 text-sm font-bold text-white transition hover:bg-gray-700"
            >
              다음 티켓 확인하기
            </button>
          </section>
        )}

        {/* QR 스캐너 */}
        <section className={cardClass}>
          <h2 className="mb-3 text-sm font-bold text-gray-500">QR 스캔</h2>
          <QrScanner onScan={handleScan} paused={Boolean(result) || submitting} />
          <p className="mt-3 text-center text-xs text-gray-400">
            {submitting ? '입장 처리 중…' : '참가자의 QR을 사각형 안에 맞춰주세요.'}
          </p>
        </section>

        {/* 입장 코드 수동 입력 — QR이 안 찍힐 때 쓰는 대체 수단 */}
        <section className={cardClass}>
          <h2 className="flex items-center gap-1.5 text-sm font-bold text-gray-500">
            <KeyboardIcon className="h-4 w-4" />
            입장 코드로 확인
          </h2>
          <p className="mt-1 text-xs text-gray-400">QR이 잘 안 찍힐 때, 티켓에 표시된 코드를 입력해주세요.</p>

          <form className="mt-3 flex gap-2" onSubmit={handleManualSubmit}>
            <input
              type="text"
              value={manualCode}
              onChange={(event) => setManualCode(event.target.value)}
              placeholder="예: SE-2Q4Y-5XF2"
              aria-label="입장 코드"
              autoCapitalize="characters"
              autoComplete="off"
              className="w-full rounded-2xl border border-gray-300 bg-white px-4 py-3 text-[15px] uppercase tracking-wider text-gray-900 outline-none transition placeholder:normal-case placeholder:tracking-normal placeholder:text-gray-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            />
            <button
              type="submit"
              disabled={submitting || !manualCode.trim()}
              className="shrink-0 rounded-2xl bg-blue-600 px-5 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
            >
              확인
            </button>
          </form>
        </section>
      </div>
    </main>
  )
}

export default CheckIn
