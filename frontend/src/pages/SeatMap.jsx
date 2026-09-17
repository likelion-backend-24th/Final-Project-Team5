import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import { ArrowRightIcon, CircleAlertIcon, LockIcon, TicketIcon } from 'lucide-react'
import { API_BASE_URL } from '../api/client'
import { fetchFestivalDetail } from '../api/festivalApi'
import { fetchSeats } from '../api/seatApi'
import { useAuth } from '../context/AuthContext.jsx'
import { MAX_QUANTITY_PER_TICKET_TYPE } from '../constants'
import styles from './SeatMap.module.css'

//API_BASE_URL의 프로토콜(http/https)을 그대로 ws/wss로 바꿔 같은 호스트로 WebSocket을 연결한다.
function toWebSocketUrl(baseUrl) {
  return baseUrl.replace(/^http/, 'ws')
}

//zone → rowLabel → seatNumber 순으로 그룹핑해 그리드 렌더링용 구조로 바꾼다.
//seatLayout이 있으면 통로(결번) 번호도 빈 칸(placeholder)으로 채워서, 한 행 안에서 좌석 번호가
//띄엄띄엄 나오는 이유(1,2,3,4,7,8,9,10 — 5,6 결번 같은)가 시각적으로 드러나게 한다.
//SeatGenerationService가 rowLabel을 "(행 순서)열"로 생성하므로, seatLayout.rows의 같은 순서 행과 매칭된다.
function groupSeatsByZone(seats, seatLayout) {
  const zoneMap = new Map()
  for (const seat of seats) {
    if (!zoneMap.has(seat.zone)) zoneMap.set(seat.zone, new Map())
    const rowMap = zoneMap.get(seat.zone)
    if (!rowMap.has(seat.rowLabel)) rowMap.set(seat.rowLabel, new Map())
    rowMap.get(seat.rowLabel).set(seat.seatNumber, seat)
  }

  return Array.from(zoneMap.entries())
    .sort(([a], [b]) => a.localeCompare(b, 'ko', { numeric: true }))
    .map(([zone, rowMap]) => ({
      zone,
      rows: seatLayout
        ? seatLayout.rows.map((rowLayout, index) => {
            const rowLabel = `${index + 1}열`
            const seatsByNumber = rowMap.get(rowLabel) ?? new Map()
            const cells = []
            for (let seatNumber = 1; seatNumber <= rowLayout.seatCount; seatNumber++) {
              if (rowLayout.excludedSeats.includes(seatNumber)) {
                cells.push({ placeholder: true, key: `${rowLabel}-${seatNumber}` })
              } else {
                const seat = seatsByNumber.get(seatNumber)
                cells.push(seat ? { placeholder: false, seat } : { placeholder: true, key: `${rowLabel}-${seatNumber}` })
              }
            }
            return { rowLabel, cells }
          })
        : Array.from(rowMap.entries())
            .sort(([a], [b]) => a.localeCompare(b, 'ko', { numeric: true }))
            .map(([rowLabel, seatsByNumber]) => ({
              rowLabel,
              cells: Array.from(seatsByNumber.values())
                .sort((a, b) => a.seatNumber - b.seatNumber)
                .map((seat) => ({ placeholder: false, seat })),
            })),
    }))
}

/** SEATED 티켓의 좌석 목록을 불러와 클릭으로 선택하게 하는 화면. 선택 후 예매 확인 화면으로 넘어간다. */
function SeatMap() {
  const { id: festivalId } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const ticketTypeId = Number(searchParams.get('ticketTypeId'))
  const { user, isLoading: authLoading } = useAuth()

  const [festival, setFestival] = useState(null)
  const [seats, setSeats] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [selectedSeatIds, setSelectedSeatIds] = useState([])
  const [seatNotice, setSeatNotice] = useState('')

  //다른 사용자의 선택/구매가 새로고침 없이 반영되도록 좌석 상태 변경을 실시간 구독한다.
  //연결이 안 되거나 끊겨도 좌석 조회·선택·예매는 REST API 기반이라 화면 자체는 그대로 동작한다.
  useEffect(() => {
    const connectionId = Date.now()
    console.log(`[WS DEBUG] effect 실행 → Client 생성 id=${connectionId}`)
    const client = new Client({
      brokerURL: `${toWebSocketUrl(API_BASE_URL)}/ws`,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log(`[WS DEBUG] onConnect 성공 id=${connectionId}`)
        client.subscribe(`/topic/festivals/${festivalId}/ticket-types/${ticketTypeId}/seats`, (message) => {
          const { seatId, status } = JSON.parse(message.body)
          setSeats((prev) => prev.map((seat) => (seat.id === seatId ? { ...seat, seatStatus: status } : seat)))
          setSelectedSeatIds((prev) => {
            if (status === 'AVAILABLE' || !prev.includes(seatId)) return prev
            setSeatNotice('선택하신 좌석 중 일부가 다른 사용자에게 선점되어 선택이 해제되었어요.')
            return prev.filter((id) => id !== seatId)
          })
        })
      },
      onWebSocketClose: (event) => {
        console.log(`[WS DEBUG] onWebSocketClose id=${connectionId} code=${event.code} reason=${event.reason}`)
      },
      onStompError: () => {
        console.log(`[WS DEBUG] onStompError id=${connectionId}`)
      },
      onWebSocketError: () => {
        console.log(`[WS DEBUG] onWebSocketError id=${connectionId}`)
      },
    })
    client.activate()
    return () => {
      console.log(`[WS DEBUG] cleanup 호출 → deactivate id=${connectionId}`)
      client.deactivate()
    }
  }, [festivalId, ticketTypeId])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')

    Promise.all([fetchFestivalDetail(festivalId), fetchSeats(festivalId, ticketTypeId)])
      .then(([festivalRes, seatsRes]) => {
        if (cancelled) return
        setFestival(festivalRes.data.data)
        setSeats(seatsRes.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('좌석 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [festivalId, ticketTypeId])

  const ticketType = festival?.ticketTypes.find((t) => t.id === ticketTypeId)
  const zones = useMemo(() => groupSeatsByZone(seats, ticketType?.seatLayout), [seats, ticketType])
  const totalAmount = ticketType ? ticketType.price * selectedSeatIds.length : 0

  function toggleSeat(seat) {
    if (seat.seatStatus !== 'AVAILABLE') return
    setSelectedSeatIds((prev) => {
      if (prev.includes(seat.id)) return prev.filter((id) => id !== seat.id)
      if (prev.length >= MAX_QUANTITY_PER_TICKET_TYPE) return prev
      return [...prev, seat.id]
    })
  }

  function handleReserve() {
    if (selectedSeatIds.length === 0) return
    navigate(`/festivals/${festivalId}/reserve?ticketTypeId=${ticketTypeId}&seatIds=${selectedSeatIds.join(',')}`)
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
            <p className={styles.infoDescription}>좌석을 선택하려면 먼저 로그인해주세요.</p>
            <Link to="/login" className={styles.infoButton}>
              로그인하러 가기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (loadError || !ticketType) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleAlertIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>좌석 정보를 확인할 수 없어요</h1>
            <p className={styles.infoDescription}>{loadError || '요청하신 티켓 종류를 찾을 수 없어요.'}</p>
            <Link to={`/festivals/${festivalId}`} className={styles.infoLink}>
              페스티벌로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>좌석 선택</h1>
        <p className={styles.subtitle}>
          <TicketIcon size={16} aria-hidden="true" />
          {ticketType.name} · {ticketType.price <= 0 ? '무료' : `${ticketType.price.toLocaleString()}원`}
        </p>

        <div className={styles.legend}>
          <span className={styles.legendItem}>
            <span className={`${styles.seatSample} ${styles.available}`} />
            선택 가능
          </span>
          <span className={styles.legendItem}>
            <span className={`${styles.seatSample} ${styles.selected}`} />
            선택됨
          </span>
          <span className={styles.legendItem}>
            <span className={`${styles.seatSample} ${styles.unavailable}`} />
            선택 불가
          </span>
        </div>

        {seatNotice && (
          <p className={styles.notice} role="alert">
            {seatNotice}
          </p>
        )}

        {zones.length === 0 ? (
          <p className={styles.empty}>등록된 좌석이 없어요.</p>
        ) : (
          <div className={styles.zoneList}>
            {zones.map(({ zone, rows }) => (
              <section key={zone} className={styles.zone}>
                <h2 className={styles.zoneTitle}>{zone}</h2>
                <div className={styles.rowList}>
                  {rows.map(({ rowLabel, cells }) => (
                    <div key={rowLabel} className={styles.row}>
                      <span className={styles.rowLabel}>{rowLabel}</span>
                      <div className={styles.seatList}>
                        {cells.map((cell) => {
                          if (cell.placeholder) {
                            return <span key={cell.key} className={styles.seatPlaceholder} aria-hidden="true" />
                          }
                          const seat = cell.seat
                          const selected = selectedSeatIds.includes(seat.id)
                          const statusClass =
                            seat.seatStatus !== 'AVAILABLE'
                              ? styles.unavailable
                              : selected
                                ? styles.selected
                                : styles.available
                          return (
                            <button
                              key={seat.id}
                              type="button"
                              className={`${styles.seat} ${statusClass}`}
                              disabled={seat.seatStatus !== 'AVAILABLE'}
                              onClick={() => toggleSeat(seat)}
                              aria-pressed={selected}
                              aria-label={`${zone} ${rowLabel} ${seat.seatNumber}번`}
                            >
                              {seat.seatNumber}
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}

        <div className={styles.summaryBar}>
          <div className={styles.summaryText}>
            <span className={styles.summaryCount}>
              {selectedSeatIds.length}석 선택 (최대 {MAX_QUANTITY_PER_TICKET_TYPE}석)
            </span>
            <span className={styles.summaryAmount}>{totalAmount.toLocaleString()}원</span>
          </div>
          <button
            type="button"
            className={styles.submit}
            disabled={selectedSeatIds.length === 0}
            onClick={handleReserve}
          >
            예매하기
            <ArrowRightIcon size={16} aria-hidden="true" />
          </button>
        </div>
      </div>
    </main>
  )
}

export default SeatMap
