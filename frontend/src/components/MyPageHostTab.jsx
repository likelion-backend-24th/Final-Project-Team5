import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRightIcon, CheckCircle2Icon, ClockIcon, XCircleIcon } from 'lucide-react'
import { fetchMyHostApplication } from '../api/hostApplicationApi'
import { fetchMyFestivals } from '../api/hostFestivalApi'

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'

const APPLY_STATUS_META = {
  PENDING: { label: '대기중', cls: 'bg-blue-50 text-blue-600', icon: ClockIcon },
  APPROVAL_PENDING: { label: '대기중', cls: 'bg-blue-50 text-blue-600', icon: ClockIcon },
  APPROVED: { label: '승인됨', cls: 'bg-green-50 text-green-600', icon: CheckCircle2Icon },
  REJECTED: { label: '반려됨', cls: 'bg-red-50 text-red-600', icon: XCircleIcon },
}

function MyPageHostTab() {
  const [applyStatus, setApplyStatus] = useState(null)
  const [festivalCount, setFestivalCount] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    Promise.allSettled([fetchMyHostApplication(), fetchMyFestivals()]).then(([applicationResult, festivalsResult]) => {
      if (cancelled) return

      if (applicationResult.status === 'fulfilled') {
        setApplyStatus(applicationResult.value.data.data.status)
      }
      if (festivalsResult.status === 'fulfilled') {
        setFestivalCount(festivalsResult.value.data.data.length)
      }
      setLoading(false)
    })

    return () => {
      cancelled = true
    }
  }, [])

  const applyMeta = applyStatus ? APPLY_STATUS_META[applyStatus] : null
  const ApplyIcon = applyMeta?.icon

  return (
    <section className="space-y-5">
      <div className={cardClass}>
        <h2 className="text-lg font-extrabold text-gray-900">주최자 관리</h2>
        <div className="mt-5 space-y-3">
          <Link
            to="/host-application"
            className="flex items-center justify-between rounded-2xl border border-gray-100 px-5 py-4 transition hover:bg-gray-50"
          >
            <span className="text-[15px] font-bold text-gray-900">주최자 신청 상태</span>
            <span className="flex items-center gap-2">
              {!loading && applyMeta && (
                <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold ${applyMeta.cls}`}>
                  <ApplyIcon className="h-3.5 w-3.5" />
                  {applyMeta.label}
                </span>
              )}
              <ChevronRightIcon className="h-4 w-4 text-gray-400" />
            </span>
          </Link>

          <Link
            to="/host/festivals"
            className="flex items-center justify-between rounded-2xl border border-gray-100 px-5 py-4 transition hover:bg-gray-50"
          >
            <span className="text-[15px] font-bold text-gray-900">내가 등록한 페스티벌</span>
            <span className="flex items-center gap-2">
              <span className="text-sm font-semibold text-gray-500">
                {loading ? '...' : `${festivalCount ?? 0}개`}
              </span>
              <ChevronRightIcon className="h-4 w-4 text-gray-400" />
            </span>
          </Link>
        </div>
      </div>

      <div className={cardClass}>
        <h3 className="text-base font-extrabold text-gray-900">주최자 소개 문구</h3>
        <p className="mt-1 text-sm text-gray-500">페스티벌 상세페이지의 주최자 영역에 노출됩니다.</p>
        <textarea
          rows={4}
          maxLength={500}
          disabled
          placeholder="준비 중인 기능이에요"
          className="mt-4 w-full resize-none rounded-2xl border border-gray-200 bg-gray-50 px-4 py-3 text-[15px] text-gray-400 outline-none"
        />
        <div className="mt-3 flex justify-end">
          <button
            type="button"
            disabled
            title="준비 중인 기능이에요"
            className="inline-flex items-center justify-center gap-2 rounded-2xl bg-gray-100 px-5 py-3 text-[15px] font-bold text-gray-400 disabled:cursor-not-allowed"
          >
            소개 저장
          </button>
        </div>
      </div>
    </section>
  )
}

export default MyPageHostTab