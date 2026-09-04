import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { CalendarIcon, ImageIcon, LockIcon, MapPinIcon, TicketIcon } from 'lucide-react'
import { fetchMyFestivalDetail } from '../api/hostFestivalApi'
import { FESTIVAL_CATEGORY_LABELS } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import Badge from '../components/Badge'
import styles from './FestivalDetail.module.css'

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

function formatDateTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** 백엔드 스펙(GET /api/host/festivals/{id}) 기준 주최자 본인 페스티벌 상세 화면. */
function HostFestivalDetail() {
  const { id } = useParams()
  const { user, isLoading: authLoading } = useAuth()
  const isHost = user?.role === 'HOST'

  const [festival, setFestival] = useState(null)
  const [loading, setLoading] = useState(true)
  const [errorState, setErrorState] = useState(null)

  useEffect(() => {
    if (authLoading || !isHost) return

    let cancelled = false
    setLoading(true)
    setErrorState(null)

    fetchMyFestivalDetail(id)
      .then((response) => {
        if (!cancelled) setFestival(response.data.data)
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
  }, [id, authLoading, isHost])

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
        <div className={styles.infoState}>
          <LockIcon size={40} aria-hidden="true" />
          <h1 className={styles.infoTitle}>주최자만 이용 가능한 페이지입니다</h1>
        </div>
      </main>
    )
  }

  if (loading) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>불러오는 중…</p>
      </main>
    )
  }

  if (errorState === 'not_found') {
    return (
      <main className={styles.main}>
        <div className={styles.infoState}>
          <h1 className={styles.infoTitle}>존재하지 않는 페스티벌이에요</h1>
          <Link to="/host/festivals" className={styles.infoLink}>
            내 페스티벌 목록으로 돌아가기
          </Link>
        </div>
      </main>
    )
  }

  if (errorState === 'forbidden') {
    return (
      <main className={styles.main}>
        <div className={styles.infoState}>
          <h1 className={styles.infoTitle}>본인 소유 페스티벌만 조회할 수 있어요</h1>
          <Link to="/host/festivals" className={styles.infoLink}>
            내 페스티벌 목록으로 돌아가기
          </Link>
        </div>
      </main>
    )
  }

  if (errorState === 'unknown') {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>페스티벌 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.</p>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.hero}>
        <div className={styles.heroPlaceholder} aria-hidden="true">
          <ImageIcon size={48} />
        </div>
      </div>

      <div className={styles.content}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Badge variant={STATUS_VARIANTS[festival.festivalStatus]}>
            {STATUS_LABELS[festival.festivalStatus] ?? festival.festivalStatus}
          </Badge>
          <Badge variant="secondary">
            {FESTIVAL_CATEGORY_LABELS[festival.festivalCategory] ?? festival.festivalCategory}
          </Badge>
        </div>
        <h1 className={styles.title}>{festival.name}</h1>

        {festival.festivalStatus === 'REJECTED' && (
          <p className={styles.description} style={{ color: 'var(--fgColor-danger)' }}>
            운영자 심사에서 반려되었어요. 내용을 보완해 다시 등록해주세요.
          </p>
        )}

        <div className={styles.metaList}>
          <span className={styles.metaRow}>
            <CalendarIcon size={16} aria-hidden="true" />
            {formatDateTime(festival.startAt)} ~ {formatDateTime(festival.endAt)}
          </span>
          <span className={styles.metaRow}>
            <MapPinIcon size={16} aria-hidden="true" />
            {festival.location}
          </span>
        </div>

        {festival.description && <p className={styles.description}>{festival.description}</p>}

        <section className={styles.ticketSection}>
          <h2 className={styles.sectionTitle}>
            <TicketIcon size={18} aria-hidden="true" />
            티켓 종류
          </h2>

          {festival.ticketTypes.length === 0 ? (
            <p className={styles.emptyTickets}>등록된 티켓이 없어요.</p>
          ) : (
            <ul className={styles.ticketList}>
              {festival.ticketTypes.map((ticketType) => (
                <li key={ticketType.id} className={styles.ticketCard}>
                  <div>
                    <p className={styles.ticketName}>{ticketType.name}</p>
                    <p className={styles.ticketStock}>
                      잔여 {ticketType.remainQuantity} / {ticketType.totalQuantity}
                    </p>
                  </div>
                  <p className={styles.ticketPrice}>
                    {ticketType.price <= 0 ? '무료' : `${ticketType.price.toLocaleString()}원`}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  )
}

export default HostFestivalDetail
