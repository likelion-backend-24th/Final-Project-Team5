import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRightIcon, CalendarIcon, InfoIcon, MapPinIcon, ScanLineIcon } from 'lucide-react'
import { fetchFestivalDetail } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'

function formatDateTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/**
 * 도우미(HELPER) 전용 메인 화면. 도우미는 배정된 행사 하나에서 현장 입장 검증만 담당하므로,
 * 일반 회원 메인(페스티벌 둘러보기·예매 유도)이 아니라 담당 행사와 검증 화면으로 가는 입구만 둔다.
 */
function HelperHome() {
  const { user } = useAuth()
  const festivalId = user?.festivalId

  const [festival, setFestival] = useState(null)

  useEffect(() => {
    if (!festivalId) return
    let cancelled = false
    fetchFestivalDetail(festivalId)
      .then((response) => {
        if (!cancelled) setFestival(response.data.data)
      })
      .catch(() => {
        //행사 이름을 못 불러와도 두 버튼은 그대로 쓸 수 있으므로 조용히 넘어간다.
      })
    return () => {
      cancelled = true
    }
  }, [festivalId])

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 py-10">
      <div className="w-full max-w-md">
        <p className="text-sm font-bold text-blue-600">{user?.nickname}님</p>
        <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-gray-900">도우미 화면</h1>

        {festival ? (
          <div className="mt-5 rounded-3xl border border-gray-200 bg-white p-5 shadow-sm">
            <p className="text-base font-extrabold text-gray-900">{festival.name}</p>
            <p className="mt-2 flex items-center gap-1.5 text-sm text-gray-500">
              <CalendarIcon className="h-4 w-4" />
              {formatDateTime(festival.startAt)} ~ {formatDateTime(festival.endAt)}
            </p>
            <p className="mt-1 flex items-center gap-1.5 text-sm text-gray-500">
              <MapPinIcon className="h-4 w-4" />
              {festival.location}
            </p>
          </div>
        ) : (
          <p className="mt-5 text-sm text-gray-500">
            {festivalId ? '담당 행사 정보를 불러오는 중이에요…' : '담당 행사가 지정되지 않았어요. 주최자에게 계정 재발급을 요청해주세요.'}
          </p>
        )}

        <div className="mt-6 space-y-3">
          {/* 도우미는 담당 행사 하나만 다루므로, 목록을 거치지 않고 그 행사 상세로 바로 보낸다. */}
          {festivalId && (
            <Link
              to={`/festivals/${festivalId}`}
              className="flex items-center gap-4 rounded-3xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:bg-blue-50"
            >
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-blue-600">
                <InfoIcon className="h-6 w-6" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-base font-extrabold text-gray-900">행사 정보</span>
                <span className="block text-sm text-gray-500">담당 행사의 일정·장소·티켓 정보를 확인해요.</span>
              </span>
              <ArrowRightIcon className="h-5 w-5 shrink-0 text-gray-400" />
            </Link>
          )}

          <Link
            to="/check-in"
            className="flex items-center gap-4 rounded-3xl bg-blue-600 p-5 shadow-sm transition hover:bg-blue-700"
          >
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-500 text-white">
              <ScanLineIcon className="h-6 w-6" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-base font-extrabold text-white">현장 입장 검사</span>
              <span className="block text-sm text-blue-100">QR을 스캔하거나 입장 코드로 입장 처리해요.</span>
            </span>
            <ArrowRightIcon className="h-5 w-5 shrink-0 text-blue-100" />
          </Link>
        </div>
      </div>
    </main>
  )
}

export default HelperHome
