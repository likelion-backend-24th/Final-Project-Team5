import { Link } from 'react-router-dom'
import { ArrowRightIcon, LayoutDashboardIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'

/** ADMIN 계정에게만 보이는 홈 상단 배너 — 어드민 대시보드로 가는 두 번째 진입점. */
function AdminHomeBanner() {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') return null

  return (
    <section className="mx-auto mb-4 max-w-[1440px] px-6">
      <div className="flex flex-col items-start gap-4 rounded-3xl border border-blue-100 bg-blue-50 px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-600 text-white">
            <LayoutDashboardIcon className="h-5 w-5" />
          </span>
          <div>
            <p className="text-sm font-bold text-gray-900">관리자님, 대시보드로 이동하시겠어요?</p>
            <p className="text-xs text-gray-500">주최자 심사와 정산 현황을 어드민 대시보드에서 확인할 수 있어요.</p>
          </div>
        </div>
        <Link
          to="/admin"
          className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-2.5 text-sm font-bold text-white transition hover:bg-blue-700 sm:w-auto"
        >
          어드민 대시보드로 이동
          <ArrowRightIcon className="h-4 w-4" />
        </Link>
      </div>
    </section>
  )
}

export default AdminHomeBanner
