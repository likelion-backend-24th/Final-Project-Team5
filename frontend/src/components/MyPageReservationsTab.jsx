import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRightIcon, ArrowUpDownIcon, ClockIcon, TicketIcon } from 'lucide-react'

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'
const primaryBtn =
  'inline-flex items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-3 text-[15px] font-bold text-white transition hover:bg-blue-700'

const SAMPLE_RESERVATIONS = [
  {
    id: 'r1',
    title: '서울 썸머 뮤직 페스티벌 2026',
    image: null,
    date: '2026.07.18 – 07.20',
    ticket: '3일 통합권',
    status: '예정',
  },
  {
    id: 'r2',
    title: '인디 라이브 클럽 나이트',
    image: null,
    date: '2026.06.28',
    ticket: '1일권',
    status: '완료',
  },
  {
    id: 'r3',
    title: '한강 나이트 푸드 마켓',
    image: null,
    date: '2026.08.01',
    ticket: '무료입장',
    status: '취소',
  },
]

const RES_STATUS_META = {
  예정: 'bg-blue-50 text-blue-600',
  완료: 'bg-gray-100 text-gray-600',
  취소: 'bg-red-50 text-red-600',
}

function MyPageReservationsTab() {
  const [reservations, setReservations] = useState(SAMPLE_RESERVATIONS)
  const [filter, setFilter] = useState('전체')
  const [sort, setSort] = useState('latest')

  const filterTabs = ['전체', '예정', '완료', '취소']

  function cancelReservation(id) {
    setReservations((prev) => prev.map((r) => (r.id === id ? { ...r, status: '취소' } : r)))
  }

  const visible = reservations
    .filter((r) => filter === '전체' || r.status === filter)
    .sort((a, b) => (sort === 'latest' ? b.date.localeCompare(a.date) : a.date.localeCompare(b.date)))

  return (
    <section className="space-y-5">
      <div className="flex items-start gap-3 rounded-2xl border border-blue-100 bg-blue-50 px-5 py-4">
        <ClockIcon className="mt-0.5 h-5 w-5 shrink-0 text-blue-600" />
        <p className="text-sm font-semibold text-blue-700">
          예매 기능 준비중입니다. 아래 내역은 예시로 표시되고 있어요.
        </p>
      </div>

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
            const count = f === '전체' ? reservations.length : reservations.filter((r) => r.status === f).length
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
                <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-xl bg-gray-100 text-gray-400">
                  <TicketIcon className="h-6 w-6" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold text-gray-900">{r.title}</p>
                  <p className="mt-0.5 text-sm text-gray-500">{r.date}</p>
                  <p className="text-sm text-gray-400">{r.ticket}</p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-2">
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${RES_STATUS_META[r.status]}`}>
                    {r.status}
                  </span>
                  {r.status === '예정' && (
                    <button
                      type="button"
                      onClick={() => cancelReservation(r.id)}
                      className="rounded-full border border-gray-200 px-3 py-1 text-xs font-bold text-gray-600 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600"
                    >
                      예약 취소
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

export default MyPageReservationsTab