import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CalendarIcon, ImageIcon, MapPinIcon, TicketIcon } from 'lucide-react'
import { FESTIVAL_CATEGORY_LABELS, fetchFestivalDetail, toAbsoluteImageUrl } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import Badge from '../components/Badge'
import styles from './FestivalDetail.module.css'

//사이트 전체 1인당 구매 제한(계정·티켓 종류당 기준, reservation-service의 고정값과 맞춘 화면 표시용 상한)
const MAX_QUANTITY_PER_TICKET_TYPE = 4

function formatDateTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** GET /api/festivals/{id} 기준 페스티벌 상세 페이지. */
function FestivalDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [festival, setFestival] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [quantities, setQuantities] = useState({})

  function handleQuantityChange(ticketType, value) {
    const max = Math.min(ticketType.remainQuantity, MAX_QUANTITY_PER_TICKET_TYPE)
    const next = Math.min(Math.max(1, Number(value) || 1), max)
    setQuantities((prev) => ({ ...prev, [ticketType.id]: next }))
  }

  function handleReserve(ticketType) {
    if (!user) {
      navigate('/login')
      return
    }
    const quantity = quantities[ticketType.id] ?? 1
    navigate(`/festivals/${id}/reserve?ticketTypeId=${ticketType.id}&quantity=${quantity}`)
  }

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setNotFound(false)
    setLoadError('')

    fetchFestivalDetail(id)
      .then((response) => {
        if (!cancelled) setFestival(response.data.data)
      })
      .catch((error) => {
        if (cancelled) return
        if (error.response?.status === 404) {
          setNotFound(true)
        } else {
          setLoadError('페스티벌 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [id])

  if (loading) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>불러오는 중…</p>
      </main>
    )
  }

  if (notFound) {
    return (
      <main className={styles.main}>
        <div className={styles.infoState}>
          <h1 className={styles.infoTitle}>존재하지 않는 페스티벌이에요</h1>
          <p className={styles.infoDescription}>
            주소가 잘못되었거나, 아직 공개되지 않은 페스티벌일 수 있어요.
          </p>
          <Link to="/festivals" className={styles.infoLink}>
            전체 페스티벌 목록으로 돌아가기
          </Link>
        </div>
      </main>
    )
  }

  if (loadError) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>{loadError}</p>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.hero}>
        {festival.imageUrls?.length > 0 ? (
          <img
            src={toAbsoluteImageUrl(festival.imageUrls[0])}
            alt={festival.name}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <div className={styles.heroPlaceholder} aria-hidden="true">
            <ImageIcon size={48} />
          </div>
        )}
      </div>

      <div className={styles.content}>
        <Badge variant="secondary">
          {FESTIVAL_CATEGORY_LABELS[festival.festivalCategory] ?? festival.festivalCategory}
        </Badge>
        <h1 className={styles.title}>{festival.name}</h1>

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
              {festival.ticketTypes.map((ticketType) => {
                const soldOut = ticketType.remainQuantity <= 0
                const quantity = quantities[ticketType.id] ?? 1
                const maxQuantity = Math.min(ticketType.remainQuantity, MAX_QUANTITY_PER_TICKET_TYPE)
                return (
                  <li key={ticketType.id} className={styles.ticketCard}>
                    <div>
                      <p className={styles.ticketName}>{ticketType.name}</p>
                      <p className={styles.ticketStock}>
                        {soldOut ? '매진' : `잔여 ${ticketType.remainQuantity} / ${ticketType.totalQuantity}`}
                      </p>
                    </div>
                    <div className={styles.ticketActions}>
                      <p className={styles.ticketPrice}>
                        {ticketType.price <= 0 ? '무료' : `${ticketType.price.toLocaleString()}원`}
                      </p>
                      {!soldOut && (
                        <>
                          <input
                            type="number"
                            min={1}
                            max={maxQuantity}
                            value={quantity}
                            onChange={(event) => handleQuantityChange(ticketType, event.target.value)}
                            className={styles.qtyInput}
                            aria-label={`${ticketType.name} 수량`}
                          />
                          <button
                            type="button"
                            className={styles.reserveButton}
                            onClick={() => handleReserve(ticketType)}
                          >
                            예매하기
                          </button>
                        </>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      </div>
    </main>
  )
}

export default FestivalDetail
