import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CalendarIcon, ImageIcon, MapPinIcon, TicketIcon } from 'lucide-react'
import { FESTIVAL_CATEGORY_LABELS, fetchFestivalDetail, toAbsoluteImageUrl } from '../api/festivalApi'
import { fetchMyReservations } from '../api/reservationApi'
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
  //도우미는 예매를 할 수 없는 계정이라(예매 API가 막혀 있다) 수량·예매 버튼을 보여주지 않는다.
  //이 화면은 도우미에게 담당 행사 정보를 확인하는 용도로만 쓰인다.
  const isHelper = user?.role === 'HELPER'
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

  async function handleReserve(ticketType) {
    if (!user) {
      navigate('/login')
      return
    }
    const quantity = quantities[ticketType.id] ?? 1

    // 이미 결제 대기 중인 예매가 있으면 새로 만들지 않고 그 결제로 이어갈 수 있게 안내한다
    // (안 그러면 재고가 중복으로 묶이고 결제대기 건도 계속 쌓인다).
    try {
      const { data } = await fetchMyReservations()
      // 다른 페스티벌의 결제대기 건까지 여기서 붙잡으면(동시에 여러 페스티벌 예매를 원하는 게
      // 자연스러운 경우도 있어) 오히려 불편하다 — 지금 보고 있는 이 페스티벌과 같을 때만 안내한다.
      const pending = data.data.find(
        (r) =>
          r.reservationStatus === 'PENDING' &&
          new Date(r.expiresAt).getTime() > Date.now() &&
          String(r.festivalId) === String(id),
      )
      if (pending) {
        const goToPending = window.confirm('결제 진행중인 예매 건이 있습니다. 이동할까요?')
        if (goToPending) {
          navigate(
            `/festivals/${pending.festivalId}/reserve?ticketTypeId=${pending.ticketTypeId}&quantity=${pending.quantity}&reservationId=${pending.id}`,
          )
          return
        }
      }
    } catch {
      // 조회 실패는 이 안내 기능만 건너뛰고 평소처럼 새 예매를 진행한다.
    }

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
                      {!soldOut && !isHelper && (
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
