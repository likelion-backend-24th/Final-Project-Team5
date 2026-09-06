import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { filterByCategory } from '../data/festivals'
import CategoryChips from './CategoryChips'
import FestivalCard from './FestivalCard'


const HOME_PREVIEW_LIMIT = 8

function FestivalBrowser() {
  const [category, setCategory] = useState('all')
  const festivals = useMemo(() => filterByCategory(category), [category])
  const previewFestivals = festivals.slice(0, HOME_PREVIEW_LIMIT)

  return (
    <div className="mx-auto max-w-[1440px] px-6">
      <div className="py-6">
        <CategoryChips value={category} onChange={setCategory} />
      </div>

      <section aria-label="인기 페스티벌">
        <div className="flex items-center gap-3">
          <h2 className="text-2xl font-extrabold tracking-tight text-gray-900">인기 페스티벌</h2>
          <Link
  to="/festivals"
  className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-bold text-blue-600 transition hover:bg-gray-100"
>
   전체 보기
</Link>
        </div>

        {festivals.length === 0 ? (
          <p className="py-10 text-center text-sm text-gray-500">
            해당 카테고리에 등록된 페스티벌이 없습니다.
          </p>
        ) : (
          <div className="mt-5 grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
            {previewFestivals.map((festival) => (
              <FestivalCard key={festival.id} festival={festival} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

export default FestivalBrowser