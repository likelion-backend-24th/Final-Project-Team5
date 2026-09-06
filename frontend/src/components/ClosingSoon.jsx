import { Link } from 'react-router-dom'
import { AlarmClockIcon } from 'lucide-react'
import { CLOSING_SOON_FESTIVALS } from '../data/festivals'
import Badge from './Badge'
import FestivalCard from './FestivalCard'

function ClosingSoon({ festivals = CLOSING_SOON_FESTIVALS }) {
  if (festivals.length === 0) return null

  return (
    <section aria-label="마감임박" className="mx-auto mt-[44px] max-w-[1440px] px-6">
      <div className="flex items-end justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-2xl font-extrabold tracking-tight text-gray-900">
            <AlarmClockIcon className="h-6 w-6 text-red-500" />
            마감임박
          </h2>
          <p className="mt-1 text-sm text-gray-500">예매 마감이 코앞인 페스티벌을 먼저 확인하세요.</p>
        </div>
        <Link to="/festivals?sort=deadline" className="text-sm font-bold text-blue-600 hover:underline">
          전체 보기
        </Link>
      </div>

      <div className="mt-5 flex snap-x gap-6 overflow-x-auto pb-4">
  {festivals.map((festival) => (
    <div key={festival.id} className="w-[280px] shrink-0 snap-start">
            <FestivalCard festival={festival} badge={<Badge variant="danger">D-{festival.dday}</Badge>} />
          </div>
        ))}
      </div>
    </section>
  )
}

export default ClosingSoon