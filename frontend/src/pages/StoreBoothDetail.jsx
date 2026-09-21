import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeftIcon, CircleAlertIcon, ImageIcon, LockIcon, MegaphoneIcon, StoreIcon } from 'lucide-react'
import { callNextBoothWaitlist, changeBoothStatus, fetchBoothQueueStatus, fetchMyBoothDetail } from '../api/boothApi'
import { toAbsoluteImageUrl } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './AdminList.module.css'

const STATUS_OPTIONS = [
  { value: 'WAITING', label: '대기' },
  { value: 'OPEN', label: '운영중' },
  { value: 'CLOSED', label: '마감' },
]

/** 백엔드 스펙(GET /api/store/booths/{id}) 기준 STOREHOST 본인 부스 상세 · 상태 변경 화면. */
function StoreBoothDetail() {
  const { id } = useParams()
  const { user, isLoading: authLoading } = useAuth()
  const isStorehost = user?.role === 'STOREHOST'

  const [booth, setBooth] = useState(null)
  const [loading, setLoading] = useState(true)
  const [errorState, setErrorState] = useState(null)
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [queueStatus, setQueueStatus] = useState(null)
  const [callingNext, setCallingNext] = useState(false)
  const [callError, setCallError] = useState('')

  useEffect(() => {
    if (authLoading || !isStorehost) return

    let cancelled = false
    setLoading(true)
    setErrorState(null)

    fetchMyBoothDetail(id)
      .then((response) => {
        if (!cancelled) setBooth(response.data.data)
      })
      .catch((error) => {
        if (cancelled) return
        const status = error.response?.status
        if (status === 404) setErrorState('not_found')
        else if (status === 403) setErrorState('forbidden')
        else setErrorState('unknown')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [id, authLoading, isStorehost])

  useEffect(() => {
    if (authLoading || !isStorehost) return
    let cancelled = false
    fetchBoothQueueStatus(id)
      .then((response) => {
        if (!cancelled) setQueueStatus(response.data.data)
      })
      .catch(() => {
        // 대기열 현황은 참고용 정보라, 조회 실패해도 부스 상세 자체는 그대로 보여준다.
      })
    return () => {
      cancelled = true
    }
  }, [id, authLoading, isStorehost])

  async function handleCallNext() {
    if (callingNext) return
    setCallingNext(true)
    setCallError('')
    try {
      const response = await callNextBoothWaitlist(id)
      setQueueStatus(response.data.data)
    } catch (error) {
      setCallError(
        error.response?.data?.errorCode === 'NO_WAITING_QUEUE'
          ? '더 이상 호출할 대기자가 없어요.'
          : '호출에 실패했어요. 잠시 후 다시 시도해주세요.',
      )
    } finally {
      setCallingNext(false)
    }
  }

  async function handleStatusChange(nextStatus) {
    if (nextStatus === booth.boothStatus || statusUpdating) return
    setStatusUpdating(true)
    setStatusError('')
    try {
      const response = await changeBoothStatus(id, nextStatus)
      setBooth(response.data.data)
    } catch {
      setStatusError('상태 변경에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setStatusUpdating(false)
    }
  }

  //권한이 없으면 조회를 시작하지 않으므로 데이터 로딩보다 권한 안내를 먼저 보여준다.
  if (authLoading || (isStorehost && loading)) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>불러오는 중…</p>
      </main>
    )
  }

  if (!isStorehost) {
    return (
      <main className={styles.main}>
        <div className={styles.forbidden}>
          <LockIcon size={40} aria-hidden="true" />
          <h1 className={styles.forbiddenTitle}>부스 운영자만 이용 가능한 페이지입니다</h1>
        </div>
      </main>
    )
  }

  if (errorState === 'not_found') {
    return (
      <main className={styles.main}>
        <div className={styles.forbidden}>
          <h1 className={styles.forbiddenTitle}>존재하지 않는 부스예요</h1>
          <Link to="/store/booths" className="mt-2 text-sm font-bold text-blue-600 hover:underline">
            내 부스 목록으로 돌아가기
          </Link>
        </div>
      </main>
    )
  }

  if (errorState === 'forbidden') {
    return (
      <main className={styles.main}>
        <div className={styles.forbidden}>
          <LockIcon size={40} aria-hidden="true" />
          <h1 className={styles.forbiddenTitle}>본인이 개설한 부스만 볼 수 있어요</h1>
        </div>
      </main>
    )
  }

  if (errorState === 'unknown' || !booth) {
    return (
      <main className={styles.main}>
        <p className={styles.loadError} role="alert">
          <CircleAlertIcon size={16} aria-hidden="true" />
          부스 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.
        </p>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.headerRow}>
        <Link
          to="/store/booths"
          className="inline-flex items-center gap-1 text-sm font-bold text-gray-500 hover:text-gray-700"
          style={{ textDecoration: 'none' }}
        >
          <ArrowLeftIcon size={16} aria-hidden="true" />
          내 부스 목록
        </Link>
      </div>

      <div className={styles.card} style={{ maxWidth: 560, width: '100%' }}>
        <div className="flex gap-4">
          {booth.imageUrl ? (
            <img
              src={toAbsoluteImageUrl(booth.imageUrl)}
              alt=""
              className="h-20 w-20 shrink-0 rounded-2xl object-cover"
            />
          ) : (
            <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-gray-100 text-gray-400">
              <ImageIcon size={28} aria-hidden="true" />
            </div>
          )}
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className={styles.festivalName} style={{ marginBottom: 0 }}>
                {booth.title}
              </h1>
            </div>
            <p className={styles.location} style={{ marginBottom: 0 }}>
              <StoreIcon size={14} aria-hidden="true" />
              {booth.boothHostName}
            </p>
          </div>
        </div>

        {booth.description && <p className={`${styles.introduction} mt-4`}>{booth.description}</p>}

        <div className="mt-6 border-t border-gray-100 pt-5">
          <p className="mb-2 text-sm font-bold text-gray-900">부스 상태</p>
          <div className="flex gap-2">
            {STATUS_OPTIONS.map((option) => {
              const active = booth.boothStatus === option.value
              return (
                <button
                  key={option.value}
                  type="button"
                  disabled={statusUpdating}
                  onClick={() => handleStatusChange(option.value)}
                  className={`flex-1 rounded-xl py-2.5 text-sm font-bold transition disabled:opacity-60 ${
                    active ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  {option.label}
                </button>
              )
            })}
          </div>
          {booth.boothStatus === 'WAITING' && (
            <p className="mt-2 text-xs text-gray-400">대기 상태에서는 관람자에게 부스가 보이지 않아요.</p>
          )}
          {statusError && <p className="mt-2 text-xs text-red-600">{statusError}</p>}
        </div>

        <div className="mt-6 border-t border-gray-100 pt-5">
          <p className="mb-2 flex items-center gap-1.5 text-sm font-bold text-gray-900">
            <MegaphoneIcon size={16} aria-hidden="true" />
            대기열 관리
          </p>
          {queueStatus && (
            <p className="text-sm text-gray-600">
              현재 호출 번호 <span className="font-bold text-gray-900">{queueStatus.calledNumber}</span>번 · 대기 인원{' '}
              <span className="font-bold text-gray-900">{queueStatus.waitingCount}</span>명
            </p>
          )}
          <button
            type="button"
            disabled={callingNext || queueStatus?.waitingCount === 0}
            onClick={handleCallNext}
            className="mt-3 w-full rounded-xl bg-blue-600 py-2.5 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {callingNext ? '호출 중…' : '다음 순번 호출'}
          </button>
          {callError && <p className="mt-2 text-xs text-red-600">{callError}</p>}
        </div>
      </div>
    </main>
  )
}

export default StoreBoothDetail
