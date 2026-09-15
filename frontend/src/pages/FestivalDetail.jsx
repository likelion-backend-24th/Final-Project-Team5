import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  BanknoteIcon,
  CalendarIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ClockIcon,
  ImageIcon,
  MapPinIcon,
  StoreIcon,
  TicketIcon,
} from 'lucide-react'
import {
  FESTIVAL_CATEGORY_LABELS,
  FESTIVAL_REGION_LABELS,
  FESTIVAL_VISIBLE_STATUS_LABELS,
  fetchFestivalDetail,
  formatLocation,
  toAbsoluteImageUrl,
} from '../api/festivalApi'
import { fetchMyReservations } from '../api/reservationApi'
import { useAuth } from '../context/AuthContext.jsx'
import Badge from '../components/Badge'
import { MAX_QUANTITY_PER_TICKET_TYPE } from '../constants'
import BoothListModal from '../components/BoothListModal'
import styles from './FestivalDetail.module.css'

//참가자용 요약 문구 — 프론트에서만 보여주는 안내용 텍스트다(백엔드 데이터 아님). 환불 정책 수치는
//reservation-service의 실제 정책(RefundPolicy: cutoff-hours 24, tiers 10/7/3/1일 전 0/10/20/30%)과
//반드시 맞춰서 고쳐야 한다 — 다르면 참가자가 실제와 다른 환불액을 기대하게 된다.
const NOTICE_SECTIONS = [
  {
    title: '예매 및 취소 안내',
    content:
      '결제가 확정되면 예매 내역에서 QR 티켓을 확인할 수 있어요. 취소·환불은 공연 시작 전까지만 가능하며, 남은 기간에 따라 위약금이 달라져요. 아래 "환불 정책"에서 정확한 기준을 확인해주세요.',
  },
  {
    title: '입장 유의사항',
    content:
      '현장에서는 예매 확정 시 발급되는 QR 티켓으로 입장해요. QR 스캔이 어려우면 함께 발급되는 입장 코드로도 확인할 수 있어요. 이미 입장 처리된 티켓은 재사용할 수 없으니, 일행과 따로 입장할 계획이면 각자 본인 명의로 예매해주세요.',
  },
  {
    title: '환불 정책',
    content:
      '공연 시작 10일 전까지 취소하면 위약금 없이 전액 환불돼요. 이후에는 남은 기간에 따라 위약금이 붙어요 — 7일 전 10%, 3일 전 20%, 1일 전 30%. 공연 시작 24시간 이내에는 취소·환불이 불가해요.',
  },
]

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토']

function formatDateTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

//"2026.07.18 (토)"처럼 짧게 보여준다 — 상세 페이지 상단 정보 카드용.
function formatDateShort(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}.${m}.${d} (${WEEKDAY_LABELS[date.getDay()]})`
}

function formatDateRangeShort(startAt, endAt) {
  const start = formatDateShort(startAt)
  const end = formatDateShort(endAt)
  if (!start || !end) return ''
  return start === end ? start : `${start} ~ ${end}`
}

//LocalDate("YYYY-MM-DD") 문자열을 "8월 15일"처럼 보여준다 — 이틀 이상 지속되는 페스티벌의 날짜별 티켓 표시용.
function formatDayLabel(value) {
  if (!value) return ''
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}

//LocalTime("HH:mm:ss") 문자열을 "18:00"처럼 앞 5자만 보여준다.
function formatTime(value) {
  return value ? value.slice(0, 5) : ''
}

//가장 저렴한 티켓 기준 가격 범위. 정확한 결제 금액은 티켓 종류를 선택해야 알 수 있어 참고용이다.
function formatPriceRange(ticketTypes) {
  if (!ticketTypes || ticketTypes.length === 0) return '가격 정보 없음'
  const minPrice = Math.min(...ticketTypes.map((ticketType) => ticketType.price))
  return minPrice <= 0 ? '무료입장' : `${minPrice.toLocaleString()}원~`
}

//공연 시작까지 남은 일수 배지. 이미 시작한 뒤에는 굳이 표시하지 않는다.
function ddayLabel(startAt) {
  const start = new Date(startAt)
  if (Number.isNaN(start.getTime())) return null
  const startDay = new Date(start.getFullYear(), start.getMonth(), start.getDate()).getTime()
  const today = new Date()
  const todayDay = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const diffDays = Math.round((startDay - todayDay) / (24 * 60 * 60 * 1000))
  if (diffDays < 0) return null
  return diffDays === 0 ? 'D-DAY' : `D-${diffDays}`
}

//운영 시간(구매자 확인용) 문구. entryStartTime·operatingStartTime 둘 다 없으면 카드 자체를 안 보여준다.
function operatingTimeText(festival) {
  if (!festival.operatingStartTime) return null
  const end = festival.operatingEndTime ? ` ~ ${formatTime(festival.operatingEndTime)}` : ''
  return `매일 ${formatTime(festival.operatingStartTime)}${end}`
}

//현재 시각이 티켓 판매 기간 밖인지 — 신청 전에 미리 안내해 클릭 후 에러를 받는 일을 줄인다.
function saleStatus(ticketType) {
  const now = Date.now()
  if (ticketType.saleStartAt && now < new Date(ticketType.saleStartAt).getTime()) return 'notStarted'
  if (ticketType.saleEndAt && now > new Date(ticketType.saleEndAt).getTime()) return 'ended'
  return 'open'
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
  //유의사항 아코디언 — 한 번에 하나만 펼쳐지고, 처음엔 첫 항목이 펼쳐져 있다.
  const [openNoticeIndex, setOpenNoticeIndex] = useState(0)
  const [showBoothModal, setShowBoothModal] = useState(false)

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

    if (ticketType.ticketMode === 'SEATED') {
      navigate(`/festivals/${id}/seats?ticketTypeId=${ticketType.id}`)
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
        {festival.thumbnailImageUrl ? (
          <img
            src={toAbsoluteImageUrl(festival.thumbnailImageUrl)}
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
        <div className={styles.info}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <Badge variant="secondary">
            {FESTIVAL_CATEGORY_LABELS[festival.festivalCategory] ?? festival.festivalCategory}
          </Badge>
          {festival.festivalStatus === 'PUBLISHED' && (
            <Badge variant="secondary">{FESTIVAL_VISIBLE_STATUS_LABELS.PUBLISHED}</Badge>
          )}
          {festival.festivalStatus === 'CLOSED' && (
            <Badge variant="secondary">{FESTIVAL_VISIBLE_STATUS_LABELS.CLOSED}</Badge>
          )}
          {ddayLabel(festival.startAt) && <Badge variant="secondary">{ddayLabel(festival.startAt)}</Badge>}
        </div>
        <h1 className={styles.title}>{festival.name}</h1>

        <div className={styles.infoCards}>
          <div className={styles.infoCard}>
            <span className={styles.infoCardLabel}>
              <CalendarIcon size={14} aria-hidden="true" />
              일정
            </span>
            <span className={styles.infoCardValue}>{formatDateRangeShort(festival.startAt, festival.endAt)}</span>
          </div>

          {(festival.entryStartTime || festival.operatingStartTime) && (
            <div className={styles.infoCard}>
              <span className={styles.infoCardLabel}>
                <ClockIcon size={14} aria-hidden="true" />
                시간
              </span>
              {operatingTimeText(festival) && (
                <span className={styles.infoCardValue}>{operatingTimeText(festival)}</span>
              )}
              {festival.entryStartTime && (
                <span className={styles.infoCardSub}>입장 {formatTime(festival.entryStartTime)}부터</span>
              )}
            </div>
          )}

          <div className={styles.infoCard}>
            <span className={styles.infoCardLabel}>
              <MapPinIcon size={14} aria-hidden="true" />
              장소
            </span>
            <span className={styles.infoCardValue}>{festival.locationDetail || formatLocation(festival)}</span>
            <span className={styles.infoCardSub}>{FESTIVAL_REGION_LABELS[festival.region] ?? festival.region}</span>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoCardLabel}>
              <BanknoteIcon size={14} aria-hidden="true" />
              가격
            </span>
            <span className={styles.infoCardValue}>{formatPriceRange(festival.ticketTypes)}</span>
          </div>
        </div>

        <h2 className={styles.sectionHeading}>부스</h2>
        <button
          type="button"
          onClick={() => setShowBoothModal(true)}
          className="flex w-full items-center justify-center gap-2 rounded-2xl border border-gray-200 bg-white py-3 text-sm font-bold text-gray-700 transition hover:bg-gray-50"
        >
          <StoreIcon size={16} aria-hidden="true" />
          운영중인 부스 보기
        </button>
        {showBoothModal && <BoothListModal festivalId={id} onClose={() => setShowBoothModal(false)} />}

        <h2 className={styles.sectionHeading}>행사 소개</h2>
        {festival.description ? (
          <p className={styles.description}>{festival.description}</p>
        ) : (
          <p className={styles.description} style={{ color: 'var(--fgColor-muted)' }}>
            등록된 소개가 없어요.
          </p>
        )}

        {festival.detailImageUrls?.length > 0 && (
          <div className={styles.gallery}>
            {festival.detailImageUrls.map((url) => (
              <img key={url} src={toAbsoluteImageUrl(url)} alt="" className={styles.galleryImage} />
            ))}
          </div>
        )}

        {festival.festivalStatus === 'CLOSED' && (
          <p className={styles.description} style={{ color: 'var(--fgColor-danger)' }}>
            종료된 페스티벌이라 예매를 신청할 수 없어요.
          </p>
        )}

        <h2 className={styles.sectionHeading}>유의사항 &amp; 환불 정책</h2>
        <div className={styles.accordion}>
          {NOTICE_SECTIONS.map((section, index) => {
            const open = openNoticeIndex === index
            return (
              <div className={styles.accordionItem} key={section.title}>
                <button
                  type="button"
                  className={styles.accordionButton}
                  aria-expanded={open}
                  onClick={() => setOpenNoticeIndex(open ? -1 : index)}
                >
                  {section.title}
                  {open ? (
                    <ChevronUpIcon size={16} aria-hidden="true" />
                  ) : (
                    <ChevronDownIcon size={16} aria-hidden="true" />
                  )}
                </button>
                {open && <p className={styles.accordionContent}>{section.content}</p>}
              </div>
            )
          })}
        </div>

        </div>

        {/* 정보는 왼쪽, 티켓은 오른쪽 sticky 패널 — 화면이 좁으면 아래로 내려간다. */}
        <aside className={styles.ticketPanel}>
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
                const closed = festival.festivalStatus !== 'PUBLISHED'
                const soldOut = ticketType.remainQuantity <= 0
                const sale = saleStatus(ticketType)
                const canReserve = !soldOut && !closed && !isHelper && sale === 'open'
                const quantity = quantities[ticketType.id] ?? 1
                const maxQuantity = Math.min(ticketType.remainQuantity, MAX_QUANTITY_PER_TICKET_TYPE)
                return (
                  <li key={ticketType.id} className={styles.ticketCard}>
                    <div>
                      <p className={styles.ticketName}>
                        {ticketType.name}
                        {ticketType.ticketDate && ` · ${formatDayLabel(ticketType.ticketDate)}`}
                      </p>
                      {ticketType.description && <p className={styles.ticketStock}>{ticketType.description}</p>}
                      <p className={styles.ticketStock}>
                        {soldOut
                          ? '매진'
                          : sale === 'notStarted'
                            ? `${formatDateTime(ticketType.saleStartAt)}부터 판매`
                            : sale === 'ended'
                              ? '판매 종료'
                              : `잔여 ${ticketType.remainQuantity} / ${ticketType.totalQuantity}`}
                      </p>
                    </div>
                    <div className={styles.ticketActions}>
                      <p className={styles.ticketPrice}>
                        {ticketType.price <= 0 ? '무료' : `${ticketType.price.toLocaleString()}원`}
                      </p>
                      {canReserve && (
                        <>
                          {ticketType.ticketMode !== 'SEATED' && (
                            <input
                              type="number"
                              min={1}
                              max={maxQuantity}
                              value={quantity}
                              onChange={(event) => handleQuantityChange(ticketType, event.target.value)}
                              className={styles.qtyInput}
                              aria-label={`${ticketType.name} 수량`}
                            />
                          )}
                          <button
                            type="button"
                            className={styles.reserveButton}
                            onClick={() => handleReserve(ticketType)}
                          >
                            {ticketType.ticketMode === 'SEATED' ? '좌석 선택하기' : '예매하기'}
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
        </aside>
      </div>
    </main>
  )
}

export default FestivalDetail
