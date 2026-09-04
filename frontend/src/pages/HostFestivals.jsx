import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRightIcon, CalendarIcon, CircleAlertIcon, LockIcon, MapPinIcon, PlusIcon } from 'lucide-react'
import { fetchMyFestivals } from '../api/hostFestivalApi'
import { FESTIVAL_CATEGORY_LABELS } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import Badge from '../components/Badge'
import styles from './AdminList.module.css'

const STATUS_LABELS = {
  PENDING: '심사 대기',
  PUBLISHED: '공개됨',
  REJECTED: '반려됨',
}

const STATUS_VARIANTS = {
  PENDING: 'secondary',
  PUBLISHED: 'accent',
  REJECTED: 'danger',
}

function formatDateRange(startAt, endAt) {
  const format = (value) => {
    const date = new Date(value)
    return Number.isNaN(date.getTime())
      ? ''
      : date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
  }
  return `${format(startAt)} ~ ${format(endAt)}`
}

/** 백엔드 스펙(GET /api/host/festivals) 기준 주최자 본인 페스티벌 목록 화면. */
function HostFestivals() {
  const { user, isLoading: authLoading } = useAuth()
  const isHost = user?.role === 'HOST'

  const [festivals, setFestivals] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    if (authLoading || !isHost) return

    let cancelled = false
    setLoading(true)
    setLoadError('')

    fetchMyFestivals()
      .then((response) => {
        if (!cancelled) setFestivals(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('페스티벌 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [authLoading, isHost])

  if (authLoading) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>불러오는 중…</p>
      </main>
    )
  }

  if (!isHost) {
    return (
      <main className={styles.main}>
        <div className={styles.forbidden}>
          <LockIcon size={40} aria-hidden="true" />
          <h1 className={styles.forbiddenTitle}>주최자만 이용 가능한 페이지입니다</h1>
          <p className={styles.forbiddenDescription}>
            페스티벌을 등록하려면 먼저 주최자 신청 후 승인을 받아주세요.
          </p>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>내 페스티벌</h1>
        <Link to="/host/festivals/new" className={styles.count}>
          <PlusIcon size={14} aria-hidden="true" style={{ verticalAlign: 'middle', marginRight: 4 }} />
          새 페스티벌 등록
        </Link>
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
          <p>아직 등록한 페스티벌이 없어요.</p>
          <Link to="/host/festivals/new" className={styles.approveButton} style={{ textDecoration: 'none' }}>
            첫 페스티벌 등록하기
            <ArrowRightIcon size={16} aria-hidden="true" style={{ marginLeft: 6 }} />
          </Link>
        </div>
      )}

      {!loading && !loadError && festivals.length > 0 && (
        <ul className={styles.list}>
          {festivals.map((festival) => (
            <li key={festival.id} className={styles.card}>
              <Link to={`/host/festivals/${festival.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                <div className={styles.cardHeader}>
                  <Badge variant={STATUS_VARIANTS[festival.festivalStatus]}>
                    {STATUS_LABELS[festival.festivalStatus] ?? festival.festivalStatus}
                  </Badge>
                  <span className={styles.date}>
                    <CalendarIcon size={12} aria-hidden="true" />
                    {formatDateRange(festival.startAt, festival.endAt)}
                  </span>
                </div>

                <h2 className={styles.festivalName}>{festival.name}</h2>
                <p className={styles.location}>
                  <MapPinIcon size={14} aria-hidden="true" />
                  {festival.location}
                  {' · '}
                  {FESTIVAL_CATEGORY_LABELS[festival.festivalCategory] ?? festival.festivalCategory}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

export default HostFestivals
