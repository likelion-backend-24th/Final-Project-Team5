import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CircleAlertIcon, LockIcon, StoreIcon } from 'lucide-react'
import { fetchMyBooths } from '../api/boothApi'
import { useAuth } from '../context/AuthContext.jsx'
import Badge from '../components/Badge'
import styles from './AdminList.module.css'

const STATUS_LABELS = { WAITING: '대기', OPEN: '운영중', CLOSED: '마감' }
const STATUS_VARIANTS = { WAITING: 'secondary', OPEN: 'accent', CLOSED: 'secondary' }

/** 백엔드 스펙(GET /api/store/booths) 기준 STOREHOST 본인이 개설한 부스 목록 화면. */
function StoreBooths() {
  const { user, isLoading: authLoading } = useAuth()
  const isStorehost = user?.role === 'STOREHOST'

  const [booths, setBooths] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    if (authLoading || !isStorehost) return

    let cancelled = false
    setLoading(true)
    setLoadError('')

    fetchMyBooths()
      .then((response) => {
        if (!cancelled) setBooths(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('부스 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [authLoading, isStorehost])

  if (authLoading) {
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

  return (
    <main className={styles.main}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>내 부스</h1>
      </div>

      {loading && <p className={styles.loading}>불러오는 중…</p>}

      {!loading && loadError && (
        <p className={styles.loadError} role="alert">
          <CircleAlertIcon size={16} aria-hidden="true" />
          {loadError}
        </p>
      )}

      {!loading && !loadError && booths.length === 0 && (
        <div className={styles.emptyState}>
          <p>아직 개설한 부스가 없어요.</p>
          <p>부스를 개설하려면 참여할 페스티벌 상세 페이지로 이동해주세요.</p>
        </div>
      )}

      {!loading && !loadError && booths.length > 0 && (
        <ul className={styles.list}>
          {booths.map((booth) => (
            <li key={booth.id} className={styles.card}>
              <Link to={`/store/booths/${booth.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                <div className={styles.cardHeader}>
                  <Badge variant={STATUS_VARIANTS[booth.boothStatus]}>
                    {STATUS_LABELS[booth.boothStatus] ?? booth.boothStatus}
                  </Badge>
                </div>
                <h2 className={styles.festivalName}>{booth.title}</h2>
                <p className={styles.location}>
                  <StoreIcon size={14} aria-hidden="true" />
                  {booth.boothHostName}
                </p>
                {booth.description && <p className={styles.introduction}>{booth.description}</p>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

export default StoreBooths
