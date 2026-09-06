import { Link } from 'react-router-dom'
import { CalendarIcon, ImageIcon, MapPinIcon } from 'lucide-react'
import { CATEGORY_LABELS } from '../data/festivals'
import Badge from './Badge'

/**
 * badge를 넘기면 썸네일 좌상단 배지를 그것으로 덮어쓴다(마감임박 D-day 등).
 * categoryLabel을 넘기면 mock CATEGORY_LABELS 대신 그 값을 노출한다(실제 API 카테고리용).
 * festival.image가 없으면 회색 플레이스홀더 아이콘을 대신 보여준다.
 */
function FestivalCard({ festival, badge, categoryLabel }) {
  const thumbBadge = badge ?? (festival.badge ? <Badge>{festival.badge}</Badge> : null)

  return (
    <Link
      to={`/festivals/${festival.id}`}
      className="group block overflow-hidden rounded-2xl border border-gray-200 bg-white transition hover:shadow-md"
    >
      <div className="relative aspect-square w-full overflow-hidden bg-gray-100">
        {festival.image ? (
          <img
            src={festival.image}
            alt={festival.title}
            loading="lazy"
            className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
          />
        ) : (
          <div
            className="flex h-full w-full items-center justify-center text-gray-400"
            aria-hidden="true"
          >
            <ImageIcon className="h-7 w-7" />
          </div>
        )}
        {thumbBadge ? <span className="absolute left-3 top-3">{thumbBadge}</span> : null}
      </div>

      <div className="space-y-2 p-4">
        <Badge variant="secondary">{categoryLabel ?? CATEGORY_LABELS[festival.category]}</Badge>
        <h3 className="text-base font-bold text-gray-900">{festival.title}</h3>
        <p className="flex items-center gap-1.5 text-sm text-gray-500">
          <MapPinIcon className="h-4 w-4 shrink-0" />
          {festival.location}
        </p>
        <p className="flex items-center gap-1.5 text-sm text-gray-500">
          <CalendarIcon className="h-4 w-4 shrink-0" />
          {festival.date}
        </p>
        <p className="pt-1 text-base font-bold text-blue-600">{festival.price}</p>
      </div>
    </Link>
  )
}

export default FestivalCard