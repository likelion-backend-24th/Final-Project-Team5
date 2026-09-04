import { useEffect, useState } from 'react'
import { CalendarIcon, CircleAlertIcon, CircleCheckIcon, MapPinIcon } from 'lucide-react'
import { fetchPendingFestivals, reviewFestival } from '../api/adminApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './AdminList.module.css'

function formatDateRange(startAt, endAt) {
  const start = new Date(startAt)
  const end = new Date(endAt)
  const format = (date) =>
    Number.isNaN(date.getTime())
      ? ''
      : date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
  return `${format(start)} ~ ${format(end)}`
}

const ERROR_MESSAGES = {
  FORBIDDEN_ROLE: '운영자 권한이 없습니다.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌입니다. 목록을 새로고침해주세요.',
  ALREADY_REVIEWED: '이미 심사 처리된 페스티벌입니다. 목록을 새로고침해주세요.',
  INVALID_DECISION: '공개 또는 반려만 결정할 수 있어요.',
}

/** 백엔드 스펙(GET/PATCH /api/admin/festivals) 기준 운영자 페스티벌 등록 심사 화면. */
function AdminFestivals() {
  const { user, isLoading: authLoading } = useAuth()
  const isAdmin = user?.role === 'ADMIN'

  const [festivals, setFestivals] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionErrors, setActionErrors] = useState({})
  const [pendingActionId, setPendingActionId] = useState(null)

  function loadFestivals() {
    setLoading(true)
    setLoadError('')
    fetchPendingFestivals()
      .then((response) => setFestivals(response.data.data))
      .catch(() => setLoadError('심사 대기 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (authLoading || !isAdmin) return
    loadFestivals()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, isAdmin])

  function setActionError(id, message) {
    setActionErrors((prev) => ({ ...prev, [id]: message }))
  }

  async function handleDecision(id, decision) {
    setPendingActionId(id)
    setActionError(id, '')
    try {
      await reviewFestival(id, { decision })
      setFestivals((prev) => prev.filter((festival) => festival.id !== id))
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      const fallback = decision === 'PUBLISHED' ? '공개 처리에 실패했어요.' : '반려 처리에 실패했어요.'
      setActionError(id, ERROR_MESSAGES[errorCode] ?? `${fallback} 잠시 후 다시 시도해주세요.`)
    } finally {
      setPendingActionId(null)
    }
  }

  if (authLoading) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>불러오는 중…</p>
      </main>
    )
  }

  if (!isAdmin) {
    return (
      <main className={styles.main}>
        <div className={styles.forbidden}>
          <CircleAlertIcon size={40} aria-hidden="true" />
          <h1 className={styles.forbiddenTitle}>운영자 권한이 필요합니다</h1>
          <p className={styles.forbiddenDescription}>이 페이지는 운영자(ADMIN)만 볼 수 있어요.</p>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>페스티벌 등록 심사</h1>
        <p className={styles.count}>심사 대기 {festivals.length}건</p>
      </div>

      {loading && <p className={styles.loading}>불러오는 중…</p>}

      {!loading && loadError && (
        <p className={styles.loadError} role="alert">
          <CircleAlertIcon size={16} aria-hidden="true" />
          {loadError}
        </p>
      )}

      {!loading && !loadError && festivals.length === 0 && (
        <div className={styles.emptyState}>
          <CircleCheckIcon size={32} aria-hidden="true" />
          <p>심사 대기 중인 페스티벌이 없어요.</p>
        </div>
      )}

      {!loading && !loadError && festivals.length > 0 && (
        <ul className={styles.list}>
          {festivals.map((festival) => (
            <li key={festival.id} className={styles.card}>
              <div className={styles.cardHeader}>
                <span className={styles.badge}>{festival.festivalCategory}</span>
                <span className={styles.date}>
                  <CalendarIcon size={12} aria-hidden="true" />
                  {formatDateRange(festival.startAt, festival.endAt)}
                </span>
              </div>

              <h2 className={styles.festivalName}>{festival.name}</h2>
              <p className={styles.location}>
                <MapPinIcon size={14} aria-hidden="true" />
                {festival.location}
              </p>
              <p className={styles.introduction}>{festival.description}</p>

              {festival.ticketTypes?.length > 0 && (
                <ul className={styles.ticketList}>
                  {festival.ticketTypes.map((ticketType) => (
                    <li key={ticketType.id} className={styles.ticketItem}>
                      {ticketType.name} · {ticketType.price.toLocaleString()}원 · {ticketType.remainQuantity}/
                      {ticketType.totalQuantity}
                    </li>
                  ))}
                </ul>
              )}

              {actionErrors[festival.id] && (
                <p className={styles.actionError} role="alert">
                  <CircleAlertIcon size={14} aria-hidden="true" />
                  {actionErrors[festival.id]}
                </p>
              )}

              <div className={styles.actions}>
                <button
                  type="button"
                  className={styles.rejectButton}
                  onClick={() => handleDecision(festival.id, 'REJECTED')}
                  disabled={pendingActionId === festival.id}
                >
                  반려
                </button>
                <button
                  type="button"
                  className={styles.approveButton}
                  onClick={() => handleDecision(festival.id, 'PUBLISHED')}
                  disabled={pendingActionId === festival.id}
                >
                  {pendingActionId === festival.id ? '처리 중…' : '공개'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

export default AdminFestivals
