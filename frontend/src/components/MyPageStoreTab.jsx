import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRightIcon } from 'lucide-react'
import { fetchMyBooths } from '../api/boothApi'

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'

function MyPageStoreTab() {
  const [boothCount, setBoothCount] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    fetchMyBooths()
      .then((response) => {
        if (!cancelled) setBoothCount(response.data.data.length)
      })
      .catch(() => {
        if (!cancelled) setBoothCount(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="space-y-5">
      <div className={cardClass}>
        <h2 className="text-lg font-extrabold text-gray-900">부스 관리</h2>
        <div className="mt-5 space-y-3">
          <Link
            to="/store/booths"
            className="flex items-center justify-between rounded-2xl border border-gray-100 px-5 py-4 transition hover:bg-gray-50"
          >
            <span className="text-[15px] font-bold text-gray-900">내가 개설한 부스</span>
            <span className="flex items-center gap-2">
              <span className="text-sm font-semibold text-gray-500">
                {loading ? '...' : `${boothCount ?? 0}개`}
              </span>
              <ChevronRightIcon className="h-4 w-4 text-gray-400" />
            </span>
          </Link>
        </div>
      </div>
    </section>
  )
}

export default MyPageStoreTab
