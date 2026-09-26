import { useState } from 'react'
import { LayoutDashboard, Users, Megaphone, CalendarDays, Wallet, CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import AdminOverviewDashboard from '../components/admin/AdminOverviewDashboard'
import OrganizerManagement from '../components/admin/OrganizerManagement'
import FestivalManagement from '../components/admin/FestivalManagement'
import SettlementDashboard from '../components/admin/SettlementDashboard'
import UserManagement from '../components/admin/UserManagement'

const TABS = [
  { key: 'dashboard', label: '대시보드', icon: LayoutDashboard },
  { key: 'member', label: '회원 관리', icon: Users },
  { key: 'organizer', label: '주최자 관리', icon: Megaphone },
  { key: 'festival', label: '페스티벌 관리', icon: CalendarDays },
  { key: 'settlement', label: '정산 대시보드', icon: Wallet },
]

const TAB_META = {
  dashboard: { title: '대시보드', description: '플랫폼 운영 현황을 한눈에 확인합니다.' },
  member: { title: '회원 관리', description: '전체 회원을 조회하고 계정 정지·해제를 처리합니다.' },
  organizer: { title: '주최자 관리', description: '주최자 신청을 심사하고 주최자 현황을 관리합니다.' },
  festival: { title: '페스티벌 관리', description: '페스티벌 등록과 행사 취소를 심사하고 승인·반려를 처리합니다.' },
  settlement: { title: '정산 대시보드', description: '플랫폼 거래·수수료 현황과 페스티벌별 정산 상태를 확인합니다.' },
}

function ComingSoon() {
  return (
    <div className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">
      <p className="py-12 text-center text-sm font-semibold text-gray-400">준비 중인 기능입니다.</p>
    </div>
  )
}

function renderTab(tab, { festivalQuery, dashboardNav, onViewOrganizerFestivals, onDashboardNavigate }) {
  if (tab === 'dashboard') return <AdminOverviewDashboard onNavigate={onDashboardNavigate} />
  if (tab === 'member') return <UserManagement />
  if (tab === 'organizer') {
    const nav = dashboardNav?.tab === 'organizer' ? dashboardNav : null
    return (
      <OrganizerManagement
        onViewFestivals={onViewOrganizerFestivals}
        initialSub={nav?.organizerSub}
        initialAccountFilter={nav?.organizerAccountFilter}
      />
    )
  }
  if (tab === 'festival') {
    const nav = dashboardNav?.tab === 'festival' ? dashboardNav : null
    return (
      <FestivalManagement
        initialQuery={festivalQuery}
        initialSub={nav?.festivalSub}
        initialOperationsFilter={nav?.operationsFilter}
        initialCancellationFilter={nav?.cancellationFilter}
      />
    )
  }
  if (tab === 'settlement') return <SettlementDashboard />
  return <ComingSoon />
}

/** 어드민 패널 — 대시보드/회원 관리/주최자 관리/페스티벌 관리/정산 대시보드를 한 화면에서 다룬다. */
function AdminDashboard() {
  const { user, isLoading: authLoading } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [tab, setTab] = useState('dashboard')
  const [festivalQuery, setFestivalQuery] = useState('')
  //대시보드 숫자 카드·목록에서 다른 탭으로 이동할 때 전달할 서브탭·필터. { tab, organizerSub,
  //organizerAccountFilter, festivalSub, operationsFilter, cancellationFilter } 형태이며, 관련 없는
  //탭에서는 무시된다(dashboardNav.tab으로 대상 탭을 구분).
  const [dashboardNav, setDashboardNav] = useState(null)

  //주최자 목록의 "등록 페스티벌 N개"는 다른 최상위 탭(페스티벌 관리)으로 이동해야 하므로,
  //직접 탭을 클릭할 때는 이전에 남아있던 검색어·대시보드 이동 정보를 초기화한다.
  function handleTabClick(key) {
    setFestivalQuery('')
    setDashboardNav(null)
    setTab(key)
  }

  function handleViewOrganizerFestivals(nickname) {
    setFestivalQuery(nickname)
    setDashboardNav(null)
    setTab('festival')
  }

  //대시보드에서 다른 탭으로 이동할 때, 주최자 목록에서 넘어온 검색어가 남아 있으면 페스티벌 관리가
  //그 주최자로 검색된 채 열리므로 함께 초기화한다.
  function handleDashboardNavigate(target) {
    setFestivalQuery('')
    setDashboardNav(target)
    setTab(target.tab)
    window.scrollTo({ top: 0 })
  }

  if (authLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center">
        <p className="text-sm text-gray-400">불러오는 중…</p>
      </main>
    )
  }

  if (!isAdmin) {
    return (
      <main className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-6 text-center">
        <CircleAlertIcon className="h-10 w-10 text-red-500" aria-hidden="true" />
        <h1 className="text-xl font-extrabold text-gray-900">운영자 권한이 필요합니다</h1>
        <p className="text-sm text-gray-500">이 페이지는 운영자(ADMIN)만 볼 수 있어요.</p>
      </main>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 관리자 전용 상단 네비게이션 */}
      <div className="border-b border-gray-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6">
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">어드민 패널</h1>
          <p className="mt-1 text-sm text-gray-500">FevalGo 운영 관리 콘솔</p>
        </div>
        <div className="mx-auto flex max-w-6xl items-center gap-1 px-4">
          {TABS.map((t) => {
            const Icon = t.icon
            const on = tab === t.key
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => handleTabClick(t.key)}
                className={
                  'flex items-center gap-2 border-b-2 px-5 py-4 text-sm font-bold transition ' +
                  (on ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700')
                }
              >
                <Icon className="h-4 w-4" />
                {t.label}
              </button>
            )
          })}
        </div>
      </div>

      <div className="mx-auto max-w-6xl px-4 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">{TAB_META[tab].title}</h1>
          <p className="mt-1 text-sm text-gray-500">{TAB_META[tab].description}</p>
        </div>

        <div key={tab} className="animate-in fade-in duration-300">
          {renderTab(tab, {
            festivalQuery,
            dashboardNav,
            onViewOrganizerFestivals: handleViewOrganizerFestivals,
            onDashboardNavigate: handleDashboardNavigate,
          })}
        </div>
      </div>
    </div>
  )
}

export default AdminDashboard
