import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRightIcon, StoreIcon } from 'lucide-react'
import { fetchMyBooths } from '../api/boothApi'
import Badge from './Badge'

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'

const STATUS_LABELS = { WAITING: '대기', OPEN: '운영중', CLOSED: '마감' }
const STATUS_VARIANTS = { WAITING: 'secondary', OPEN: 'accent', CLOSED: 'secondary' }

function MyPageStoreTab() {
  const [booths, setBooths] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let cancelled = false

    fetchMyBooths()
      .then((response) => {
        if (!cancelled) setBooths(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('부스 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
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

        {loading && <p className="mt-4 text-sm text-gray-500">불러오는 중…</p>}

        {!loading && loadError && (
          <p className="mt-4 text-sm font-semibold text-red-600" role="alert">
            {loadError}
          </p>
        )}

        {!loading && !loadError && booths.length === 0 && (
          <p className="mt-4 text-sm text-gray-500">
            아직 개설한 부스가 없어요. 참여할 페스티벌 상세 페이지에서 개설할 수 있어요.
          </p>
        )}

        {!loading && !loadError && booths.length > 0 && (
          <div className="mt-5 space-y-3">
            {booths.map((booth) => (
              <Link
                key={booth.id}
                to={`/store/booths/${booth.id}`}
                className="flex items-center justify-between rounded-2xl border border-gray-100 px-5 py-4 transition hover:bg-gray-50"
              >
                <span className="flex items-center gap-2 text-[15px] font-bold text-gray-900">
                  <StoreIcon className="h-4 w-4 text-gray-400" />
                  {booth.title}
                </span>
                <span className="flex items-center gap-2">
                  <Badge variant={STATUS_VARIANTS[booth.boothStatus]}>
                    {STATUS_LABELS[booth.boothStatus] ?? booth.boothStatus}
                  </Badge>
                  <ChevronRightIcon className="h-4 w-4 text-gray-400" />
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

export default MyPageStoreTab
