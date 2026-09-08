import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRightIcon, ArrowUpDownIcon, ImageIcon, QrCodeIcon, TicketIcon, XIcon } from 'lucide-react'
import { fetchFestivalDetail, toAbsoluteImageUrl } from '../api/festivalApi'
import { fetchMyReservations, fetchReservationQr } from '../api/reservationApi'

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'
const primaryBtn =
  'inline-flex items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-3 text-[15px] font-bold text-white transition hover:bg-blue-700'

const STATUS_META = {
  결제대기: 'bg-yellow-50 text-yellow-700',
  예정: 'bg-blue-50 text-blue-600',
  완료: 'bg-gray-100 text-gray-600',
  취소: 'bg-red-50 text-red-600',
}

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replaceAll(' ', '')
}

function formatDateRange(startAt, endAt) {
  const start = formatDate(startAt)
  const end = formatDate(endAt)
  if (!start || !end) return ''
  return start === end ? start : `${start} – ${end}`
}

//백엔드 reservationStatus(PENDING/CONFIRMED/CANCELLED/REFUNDED/PARTIALLY_REFUNDED)를
//화면 라벨로 변환한다. CONFIRMED는 페스티벌 종료 여부로 예정/완료를 다시 나눈다.
function toStatusLabel(reservationStatus, festivalEndAt) {
  if (reservationStatus === 'PENDING') return '결제대기'
  if (reservationStatus === 'CONFIRMED') {
    return festivalEndAt && new Date(festivalEndAt) < new Date() ? '완료' : '예정'
  }
  return '취소' // CANCELLED, REFUNDED, PARTIALLY_REFUNDED
}

function QrModal({ reservationId, onClose }) {
  const [qr, setQr] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchReservationQr(reservationId)
      .then((response) => {
        if (!cancelled) setQr(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setError('QR을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
    return () => {
      cancelled = true
    }
  }, [reservationId])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-6">
      <div className="w-full max-w-sm rounded-3xl bg-white p-6 text-center shadow-lg">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-extrabold text-gray-900">입장용 QR</h3>
          <button type="button" onClick={onClose} aria-label="닫기" className="text-gray-400 hover:text-gray-600">
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        {error && <p className="mt-6 text-sm font-semibold text-red-600">{error}</p>}

        {!error && !qr && <p className="mt-6 text-sm text-gray-500">불러오는 중…</p>}

        {qr && (
          <>
            <img src={qr.qrImageUrl} alt="입장용 QR 코드" className="mx-auto mt-6 h-48 w-48" />
            <p className="mt-4 text-xs text-gray-400">현장 입장 시 이 QR을 주최자에게 제시해주세요.</p>
          </>
        )}
      </div>
    </div>
  )
}

function MyPageReservationsTab() {
  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [filter, setFilter] = useState('전체')
  const [sort, setSort] = useState('latest')
  const [qrReservationId, setQrReservationId] = useState(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const listResponse = await fetchMyReservations()
        const list = listResponse.data.data

        //예매 응답에는 ticketTypeId만 있어 화면에 필요한 페스티벌 이름·날짜·이미지를
        //페스티벌 상세 조회로 보강한다. 같은 festivalId는 한 번만 조회한다.
        const festivalCache = new Map()
        async function getFestival(festivalId) {
          if (!festivalCache.has(festivalId)) {
            festivalCache.set(
              festivalId,
              fetchFestivalDetail(festivalId)
                .then((response) => response.data.data)
                .catch(() => null),
            )
          }
          return festivalCache.get(festivalId)
        }

        const enriched = await Promise.all(
          list.map(async (reservation) => {
            const festival = await getFestival(reservation.festivalId)
            const ticketType = festival?.ticketTypes?.find((t) => t.id === reservation.ticketTypeId)
            return {
              ...reservation,
              festivalName: festival?.name ?? '알 수 없는 페스티벌',
              festivalImage: toAbsoluteImageUrl(festival?.imageUrls?.[0]),
              festivalDate: festival ? formatDateRange(festival.startAt, festival.endAt) : '',
              ticketTypeName: ticketType?.name ?? '',
              statusLabel: toStatusLabel(reservation.reservationStatus, festival?.endAt),
            }
          }),
        )

        if (!cancelled) setReservations(enriched)
      } catch {
        if (!cancelled) setLoadError('예매 내역을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [])

  const filterTabs = ['전체', '결제대기', '예정', '완료', '취소']

  const visible = reservations
    .filter((r) => filter === '전체' || r.statusLabel === filter)
    .sort((a, b) => (sort === 'latest' ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt)))

  if (loading) {
    return (
      <section className={cardClass}>
        <p className="text-sm text-gray-500">불러오는 중…</p>
      </section>
    )
  }

  if (loadError) {
    return (
      <section className={cardClass}>
        <p className="text-sm font-semibold text-red-600">{loadError}</p>
      </section>
    )
  }

  return (
    <section className="space-y-5">
      <div className={cardClass}>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-lg font-extrabold text-gray-900">내 예약</h2>

          <label className="flex items-center gap-2 self-start rounded-2xl border border-gray-200 px-3 py-2 text-sm font-semibold text-gray-600 sm:self-auto">
            <ArrowUpDownIcon className="h-4 w-4 text-gray-400" />
            <select
              value={sort}
              onChange={(event) => setSort(event.target.value)}
              className="bg-transparent pr-1 outline-none"
              aria-label="예약 정렬"
            >
              <option value="latest">최신순</option>
              <option value="oldest">오래된순</option>
            </select>
          </label>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          {filterTabs.map((f) => {
            const on = filter === f
            const count = f === '전체' ? reservations.length : reservations.filter((r) => r.statusLabel === f).length
            return (
              <button
                key={f}
                type="button"
                onClick={() => setFilter(f)}
                className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                  on ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {f} <span className={on ? 'text-blue-100' : 'text-gray-400'}>{count}</span>
              </button>
            )
          })}
        </div>

        {visible.length === 0 ? (
          <div className="flex flex-col items-center py-10 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 text-gray-400">
              <TicketIcon className="h-8 w-8" />
            </div>
            <p className="mt-5 text-base font-bold text-gray-900">
              {filter === '전체' ? '아직 예약한 페스티벌이 없어요' : `${filter} 상태의 예약이 없어요`}
            </p>
            {filter === '전체' && (
              <Link to="/" className={`${primaryBtn} mt-6`}>
                페스티벌 둘러보기
                <ArrowRightIcon className="h-4 w-4" />
              </Link>
            )}
          </div>
        ) : (
          <ul className="mt-5 space-y-4">
            {visible.map((r) => (
              <li
                key={r.id}
                className="flex items-center gap-4 rounded-2xl border border-gray-100 p-3 transition hover:bg-gray-50"
              >
                <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-gray-100 text-gray-400">
                  {r.festivalImage ? (
                    <img src={r.festivalImage} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <ImageIcon className="h-6 w-6" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold text-gray-900">{r.festivalName}</p>
                  <p className="mt-0.5 text-sm text-gray-500">{r.festivalDate}</p>
                  <p className="text-sm text-gray-400">
                    {r.ticketTypeName} · {r.quantity}장
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-2">
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${STATUS_META[r.statusLabel]}`}>
                    {r.statusLabel}
                  </span>
                  {r.reservationStatus === 'CONFIRMED' && (
                    <button
                      type="button"
                      onClick={() => setQrReservationId(r.id)}
                      className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1 text-xs font-bold text-gray-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600"
                    >
                      <QrCodeIcon className="h-3.5 w-3.5" />
                      QR 보기
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {qrReservationId && <QrModal reservationId={qrReservationId} onClose={() => setQrReservationId(null)} />}
    </section>
  )
}

export default MyPageReservationsTab
