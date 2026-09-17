import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowRightIcon, LockIcon } from 'lucide-react'
import { fetchFestivalDetail } from '../api/festivalApi'
import { fetchMyReservations } from '../api/reservationApi'
import { useAuth } from '../context/AuthContext.jsx'
import { MAX_QUANTITY_PER_TICKET_TYPE } from '../constants'
import styles from './ZoneSelect.module.css'

function formatDateTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

//현재 시각이 티켓 판매 기간 밖인지 — 신청 전에 미리 안내해 클릭 후 에러를 받는 일을 줄인다.
function saleStatus(ticketType) {
  const now = Date.now()
  if (ticketType.saleStartAt && now < new Date(ticketType.saleStartAt).getTime()) return 'notStarted'
  if (ticketType.saleEndAt && now > new Date(ticketType.saleEndAt).getTime()) return 'ended'
  return 'open'
}

//매진/판매전/판매종료 판정 + 표시 문구 — ZoneCard(카드형)와 도넛 조각(중앙형)이 같은 기준을 공유한다.
function getZoneStatus(ticketType) {
  const soldOut = ticketType.remainQuantity <= 0
  const sale = saleStatus(ticketType)
  const unavailable = soldOut || sale !== 'open'
  const stockText = soldOut
    ? '매진'
    : sale === 'notStarted'
      ? `${formatDateTime(ticketType.saleStartAt)}부터 판매`
      : sale === 'ended'
        ? '판매 종료'
        : `잔여 ${ticketType.remainQuantity}석`
  return { unavailable, stockText }
}

function formatZonePrice(ticketType) {
  return ticketType.price <= 0 ? '무료' : `${ticketType.price.toLocaleString()}원`
}

//극좌표(중심 cx,cy / 반지름 r / 정북 기준 시계방향 각도 angleDeg)를 x,y 평면 좌표로 변환.
function polarToXY(cx, cy, r, angleDeg) {
  const radian = (angleDeg - 90) * (Math.PI / 180)
  return { x: cx + r * Math.cos(radian), y: cy + r * Math.sin(radian) }
}

function pointStr({ x, y }) {
  return `${x} ${y}`
}

//innerR~outerR, startAngle~endAngle(도) 사이의 도넛 "조각" 하나를 그리는 path — 서로 다른 구역을
//나누는 반지름 방향 경계선이 실제로 필요한 일반적인 경우(구역 2개 이상)에 쓴다.
function donutSlicePath(cx, cy, innerR, outerR, startAngle, endAngle) {
  const outerStart = polarToXY(cx, cy, outerR, startAngle)
  const outerEnd = polarToXY(cx, cy, outerR, endAngle)
  const innerEnd = polarToXY(cx, cy, innerR, endAngle)
  const innerStart = polarToXY(cx, cy, innerR, startAngle)
  return [
    `M ${pointStr(outerStart)}`,
    `A ${outerR} ${outerR} 0 0 1 ${pointStr(outerEnd)}`,
    `L ${pointStr(innerEnd)}`,
    `A ${innerR} ${innerR} 0 0 0 ${pointStr(innerStart)}`,
    'Z',
  ].join(' ')
}

//구역이 딱 1개(360도 전체)일 때는 "조각"이 아니라 이음매 없는 완전한 도넛(반지) 모양이어야 한다.
//donutSlicePath처럼 직선(L)으로 이어붙이면 그 연결선이 반지름 방향 seam으로 보이므로, 대신
//바깥 원 + 안쪽 원 — 완전한 원 두 개를 겹쳐 그리고 fill-rule="evenodd"로 안쪽 원 영역을 구멍으로
//파낸다. 이 방식은 경계선 자체가 path 안에 존재하지 않아 진짜로 이음매가 없다.
function fullRingPath(cx, cy, innerR, outerR) {
  const circle = (r) => `M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy} A ${r} ${r} 0 0 1 ${cx - r} ${cy} Z`
  return `${circle(outerR)} ${circle(innerR)}`
}

const DONUT_VIEWBOX_SIZE = 600
const DONUT_CENTER = 300
const DONUT_OUTER_RADIUS = 280
const DONUT_INNER_RADIUS = 150

//items를 size개씩 잘라 행(row) 배열로 나눈다 — [a,b,c,d] → [[a,b,c],[d]].
function chunkArray(items, size) {
  const chunks = []
  for (let i = 0; i < items.length; i += size) {
    chunks.push(items.slice(i, i + size))
  }
  return chunks
}

//한 줄에 최대 size개까지 채우고, 각 줄은 그 줄 안의 개수(1~size)에 맞춰 폭을 균등하게 나눠 채운다 —
//줄마다 별도의 flex 컨테이너로 렌더링해야 마지막 줄에 카드가 몇 개 남든(예: 1개) 그 줄 폭을 항상
//꽉 채울 수 있다(flex-wrap 하나로는 줄이 바뀔 때마다 개수가 달라지는 걸 처리할 수 없다).
//SEATED 구역 그리드(전면형)와 스탠딩 구역 리스트가 이 로직을 공유한다.
function FlexRows({ items, renderItem, size = 3, className, rowClassName }) {
  return (
    <div className={className}>
      {chunkArray(items, size).map((row, rowIndex) => (
        <div className={rowClassName} key={rowIndex}>
          {row.map((item) => renderItem(item))}
        </div>
      ))}
    </div>
  )
}

//구역 카드 하나 — 전면형 그리드/중앙형 세로 리스트(모바일)/스탠딩 리스트에서 공통으로 쓴다.
function ZoneCard({ ticketType, disabled, style, onSelect, quantity, onQuantityChange }) {
  const { unavailable, stockText } = getZoneStatus(ticketType)
  const clickable = !disabled && !unavailable && ticketType.ticketMode === 'SEATED'

  return (
    <div
      className={`${styles.zoneCard} ${unavailable ? styles.zoneCardSoldOut : ''}`}
      style={style}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onClick={clickable ? onSelect : undefined}
      onKeyDown={
        clickable
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') onSelect()
            }
          : undefined
      }
    >
      <p className={styles.zoneName}>{ticketType.name}</p>
      {ticketType.description && <p className={styles.zoneDescription}>{ticketType.description}</p>}
      <p className={styles.zoneStock}>{stockText}</p>
      <p className={styles.zonePrice}>{formatZonePrice(ticketType)}</p>

      {ticketType.ticketMode === 'STANDING' && !unavailable && !disabled && (
        <div className={styles.standingActions} onClick={(event) => event.stopPropagation()}>
          <input
            type="number"
            min={1}
            max={Math.min(ticketType.remainQuantity, MAX_QUANTITY_PER_TICKET_TYPE)}
            value={quantity}
            onChange={(event) => onQuantityChange(event.target.value)}
            className={styles.qtyInput}
            aria-label={`${ticketType.name} 수량`}
          />
          <button type="button" className={styles.standingReserveButton} onClick={onSelect}>
            예매하기
          </button>
        </div>
      )}
    </div>
  )
}

//전면형: STAGE 바(별도 요소, 구역 리스트와 같은 부모 아래 같은 width:100%라 폭이 자연히 맞음) +
//한 줄 최대 3개 청크로 나눠 채우는 SEATED 구역 리스트. positionRow/positionCol은 격자 좌표가 아니라
//표시 순서를 나타내는 값으로만 쓴다 — HostFestivalNew가 항상 idx 기준으로 3개씩 증가시켜 만들기
//때문에(positionRow = floor(idx/3)+1, positionCol = idx%3+1) 이 둘을 합친 값으로 정렬하면
//원래 순서가 그대로 복원된다.
function FrontStageZones({ seatedZones, disabled, onSelectSeated }) {
  const sortedZones = [...seatedZones].sort(
    (a, b) => a.positionRow * 100 + a.positionCol - (b.positionRow * 100 + b.positionCol),
  )

  return (
    <div className={styles.frontStageWrap}>
      <div className={styles.stageBarFront}>STAGE</div>
      <FlexRows
        items={sortedZones}
        className={styles.frontZoneList}
        rowClassName={styles.flexRow}
        renderItem={(ticketType) => (
          <ZoneCard
            key={ticketType.id}
            ticketType={ticketType}
            disabled={disabled}
            onSelect={() => onSelectSeated(ticketType)}
            style={{ flex: 1 }}
          />
        )}
      />
    </div>
  )
}

//중앙형: 중앙 원형 STAGE + SEATED 구역 개수만큼 360도를 균등분할한 도넛 조각. positionAngle 값은
//조각 폭이 아니라 "정렬 순서"로만 쓴다 — 선택된 방향이 8개보다 적거나 개수가 달라도 조각이
//겹치거나 빈틈이 생기지 않도록 항상 360/N으로 나눈다.
//svg(조각 도형)와 라벨 레이어는 같은 position:relative 부모(.donutWrap) 아래 둘 다
//position:absolute; inset:0으로 완전히 포개져 있어, 컨테이너가 어떤 크기로 렌더링되든 라벨이
//조각 위치와 항상 정확히 일치한다.
//좁은 화면에서는 도넛을 숨기고 세로 리스트로 전환한다(centerStageList).
function CenterStageZones({ seatedZones, disabled, onSelectSeated }) {
  const sortedZones = [...seatedZones].sort((a, b) => a.positionAngle - b.positionAngle)
  const sliceAngle = 360 / sortedZones.length
  const isFullRing = sortedZones.length === 1
  const midRadius = (DONUT_INNER_RADIUS + DONUT_OUTER_RADIUS) / 2

  return (
    <div className={styles.centerStageWrap}>
      <div className={styles.donutWrap}>
        <svg
          className={styles.donutSvg}
          viewBox={`0 0 ${DONUT_VIEWBOX_SIZE} ${DONUT_VIEWBOX_SIZE}`}
          preserveAspectRatio="xMidYMid meet"
        >
          {sortedZones.map((ticketType, index) => {
            const { unavailable } = getZoneStatus(ticketType)
            const clickable = !disabled && !unavailable
            const d = isFullRing
              ? fullRingPath(DONUT_CENTER, DONUT_CENTER, DONUT_INNER_RADIUS, DONUT_OUTER_RADIUS)
              : donutSlicePath(
                  DONUT_CENTER,
                  DONUT_CENTER,
                  DONUT_INNER_RADIUS,
                  DONUT_OUTER_RADIUS,
                  index * sliceAngle,
                  (index + 1) * sliceAngle,
                )
            return (
              <path
                key={ticketType.id}
                d={d}
                fillRule="evenodd"
                className={`${styles.donutSlice} ${unavailable ? styles.donutSliceSoldOut : ''}`}
                role={clickable ? 'button' : undefined}
                tabIndex={clickable ? 0 : undefined}
                onClick={clickable ? () => onSelectSeated(ticketType) : undefined}
                onKeyDown={
                  clickable
                    ? (event) => {
                        if (event.key === 'Enter' || event.key === ' ') onSelectSeated(ticketType)
                      }
                    : undefined
                }
                aria-label={`${ticketType.name} ${unavailable ? '선택 불가' : '선택'}`}
              />
            )
          })}
        </svg>

        <div className={styles.donutLabelLayer}>
          {sortedZones.map((ticketType, index) => {
            const midAngle = (index + 0.5) * sliceAngle
            const { x, y } = polarToXY(DONUT_CENTER, DONUT_CENTER, midRadius, midAngle)
            const { unavailable, stockText } = getZoneStatus(ticketType)
            return (
              <div
                key={ticketType.id}
                className={`${styles.donutLabel} ${unavailable ? styles.donutLabelMuted : ''}`}
                style={{
                  left: `${(x / DONUT_VIEWBOX_SIZE) * 100}%`,
                  top: `${(y / DONUT_VIEWBOX_SIZE) * 100}%`,
                }}
              >
                <p className={styles.zoneName}>{ticketType.name}</p>
                <p className={styles.zoneStock}>{stockText}</p>
                <p className={styles.zonePrice}>{formatZonePrice(ticketType)}</p>
              </div>
            )
          })}
        </div>

        <div className={styles.stageBarCenter}>
          <span>STAGE</span>
          <span className={styles.stageBarCenterSub}>중앙 무대</span>
        </div>
      </div>

      {/* 720px 이하에서는 위 도넛을 숨기고 이걸 세로 리스트로 보여준다. */}
      <div className={styles.centerStageList}>
        {sortedZones.map((ticketType) => (
          <ZoneCard
            key={ticketType.id}
            ticketType={ticketType}
            disabled={disabled}
            onSelect={() => onSelectSeated(ticketType)}
          />
        ))}
      </div>
    </div>
  )
}

//스탠딩 구역 섹션 — 전면형/중앙형 공통, 하단에 나열. SEATED 그리드와 같은 청크+flex:1 로직 재사용.
function StandingZones({ standingZones, disabled, onReserveStanding, quantities, onQuantityChange }) {
  if (standingZones.length === 0) return null
  return (
    <section className={styles.standingSection}>
      <h2 className={styles.sectionHeading}>스탠딩 구역</h2>
      <FlexRows
        items={standingZones}
        className={styles.standingList}
        rowClassName={styles.flexRow}
        renderItem={(ticketType) => (
          <ZoneCard
            key={ticketType.id}
            ticketType={ticketType}
            disabled={disabled}
            onSelect={() => onReserveStanding(ticketType)}
            quantity={quantities[ticketType.id] ?? 1}
            onQuantityChange={(value) => onQuantityChange(ticketType, value)}
            style={{ flex: 1 }}
          />
        )}
      />
    </section>
  )
}

/** stageLayout(FRONT_STAGE/CENTER_STAGE)에 따라 구역을 배치해 보여주고, 선택 시 좌석 선택/예매 화면으로 이동한다. */
function ZoneSelect() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user, isLoading: authLoading } = useAuth()
  const isHelper = user?.role === 'HELPER'

  const [festival, setFestival] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [quantities, setQuantities] = useState({})

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')

    fetchFestivalDetail(id)
      .then((response) => {
        if (!cancelled) setFestival(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('구역 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [id])

  function handleQuantityChange(ticketType, value) {
    const max = Math.min(ticketType.remainQuantity, MAX_QUANTITY_PER_TICKET_TYPE)
    const next = Math.min(Math.max(1, Number(value) || 1), max)
    setQuantities((prev) => ({ ...prev, [ticketType.id]: next }))
  }

  function handleSelectSeated(ticketType) {
    navigate(`/festivals/${id}/seats?ticketTypeId=${ticketType.id}`)
  }

  async function handleReserveStanding(ticketType) {
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
            <p className={styles.infoDescription}>구역을 선택하려면 먼저 로그인해주세요.</p>
            <Link to="/login" className={styles.infoButton}>
              로그인하러 가기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (loadError || !festival) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <h1 className={styles.infoTitle}>구역 정보를 확인할 수 없어요</h1>
            <p className={styles.infoDescription}>{loadError || '요청하신 페스티벌을 찾을 수 없어요.'}</p>
            <Link to={`/festivals/${id}`} className={styles.infoLink}>
              페스티벌로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  const seatedZones = festival.ticketTypes.filter((t) => t.ticketMode === 'SEATED')
  const standingZones = festival.ticketTypes.filter((t) => t.ticketMode !== 'SEATED')
  const closed = festival.festivalStatus !== 'PUBLISHED'
  const disabled = closed || isHelper

  return (
    <main className={styles.main}>
      <div className={styles.page}>
        <h1 className={styles.title}>예매하기</h1>
        <p className={styles.subtitle}>
          {festival.stageLayout === 'CENTER_STAGE'
            ? '중앙 무대를 둘러싼 구역 중 원하는 위치를 선택하세요.'
            : '구역을 선택하면 좌석과 매수를 지정할 수 있어요.'}
        </p>

        {disabled && (
          <p className={styles.noticeBanner}>
            {closed ? '종료된 페스티벌이라 예매를 신청할 수 없어요.' : '도우미 계정은 예매를 진행할 수 없어요.'}
          </p>
        )}

        {seatedZones.length > 0 && (
          <>
            <h2 className={styles.sectionHeading}>좌석 구역</h2>
            {festival.stageLayout === 'CENTER_STAGE' ? (
              <CenterStageZones seatedZones={seatedZones} disabled={disabled} onSelectSeated={handleSelectSeated} />
            ) : (
              <FrontStageZones seatedZones={seatedZones} disabled={disabled} onSelectSeated={handleSelectSeated} />
            )}
          </>
        )}

        <StandingZones
          standingZones={standingZones}
          disabled={disabled}
          onReserveStanding={handleReserveStanding}
          quantities={quantities}
          onQuantityChange={handleQuantityChange}
        />

        {seatedZones.length === 0 && standingZones.length === 0 && (
          <p className={styles.emptyZones}>등록된 구역이 없어요.</p>
        )}
      </div>
    </main>
  )
}

export default ZoneSelect
