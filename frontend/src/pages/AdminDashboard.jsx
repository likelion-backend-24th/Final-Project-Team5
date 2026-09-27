import { Navigate, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { ADMIN_TABS, ADMIN_TAB_META, isValidAdminStatus, parseAdminPath } from '../components/admin/adminNav'
import AdminOverviewDashboard from '../components/admin/AdminOverviewDashboard'
import OrganizerManagement from '../components/admin/OrganizerManagement'
import FestivalManagement from '../components/admin/FestivalManagement'
import SettlementDashboard from '../components/admin/SettlementDashboard'
import UserManagement from '../components/admin/UserManagement'

//대시보드 카드가 내려주는 옛 tab/서브탭 키를 새 경로 세그먼트로 옮긴다.
const DASHBOARD_NAV_TAB = { member: 'members', organizer: 'hosts', festival: 'festivals' }
const DASHBOARD_NAV_ORGANIZER_SUB = { organizer: 'applications', list: 'list' }
const DASHBOARD_NAV_FESTIVAL_SUB = { festival: 'submissions', operations: 'operations', cancellation: 'cancellations' }

function ComingSoon() {
  return (
    <div className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">
      <p className="py-12 text-center text-sm font-semibold text-gray-400">준비 중인 기능입니다.</p>
    </div>
  )
}

function renderTab(tab, sub, { status, q, onViewOrganizerFestivals, onDashboardNavigate }) {
  if (tab === 'dashboard') return <AdminOverviewDashboard onNavigate={onDashboardNavigate} />
  if (tab === 'members') return <UserManagement />
  if (tab === 'hosts') {
    return <OrganizerManagement sub={sub} onViewFestivals={onViewOrganizerFestivals} initialAccountFilter={status} />
  }
  if (tab === 'festivals') {
    return (
      <FestivalManagement
        sub={sub}
        initialQuery={q}
        initialOperationsFilter={status}
        initialCancellationFilter={status}
      />
    )
  }
  if (tab === 'settlements') return <SettlementDashboard />
  return <ComingSoon />
}

//대시보드 카드 클릭({ tab, organizerSub, organizerAccountFilter, festivalSub, operationsFilter,
//cancellationFilter })을 실제 경로+status 쿼리로 바꾼다. AdminOverviewDashboard의 onNavigate
//호출 형태는 그대로 두고 여기서만 변환한다.
function buildDashboardNavigateUrl(target) {
  const tab = DASHBOARD_NAV_TAB[target.tab] ?? target.tab
  let sub = null
  let status = null
  if (target.tab === 'organizer') {
    sub = DASHBOARD_NAV_ORGANIZER_SUB[target.organizerSub] ?? null
    status = target.organizerAccountFilter ?? null
  } else if (target.tab === 'festival') {
    sub = DASHBOARD_NAV_FESTIVAL_SUB[target.festivalSub] ?? null
    status = target.operationsFilter ?? target.cancellationFilter ?? null
  }
  const pathname = sub ? `/admin/${tab}/${sub}` : `/admin/${tab}`
  const search = status ? `?${new URLSearchParams({ status }).toString()}` : ''
  return `${pathname}${search}`
}

/** 어드민 패널 — 대시보드/회원 관리/주최자 관리/페스티벌 관리/정산 대시보드를 한 화면에서 다룬다.
 * 탭·서브탭은 경로(/admin/festivals/operations)로, 초기 필터·검색어는 쿼리(status, q)로 표현한다. */
function AdminDashboard() {
  const { user, isLoading: authLoading } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  function handleViewOrganizerFestivals(nickname) {
    navigate(`/admin/festivals/submissions?${new URLSearchParams({ q: nickname }).toString()}`)
  }

  function handleDashboardNavigate(target) {
    navigate(buildDashboardNavigateUrl(target))
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

  const parsed = parseAdminPath(location.pathname)
  if (parsed.status === 'invalid') return <Navigate to="/admin" replace />
  if (parsed.status === 'needs-default-sub') return <Navigate to={parsed.redirectTo} replace />

  const { tab, sub } = parsed
  const rawStatus = searchParams.get('status')
  const status = isValidAdminStatus(tab, sub, rawStatus) ? rawStatus : undefined
  const q = searchParams.get('q') ?? ''

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 관리자 전용 상단 네비게이션 */}
      <div className="border-b border-gray-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6">
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">어드민 패널</h1>
          <p className="mt-1 text-sm text-gray-500">FevalGo 운영 관리 콘솔</p>
        </div>
        <div className="mx-auto flex max-w-6xl items-center gap-1 px-4">
          {ADMIN_TABS.map((t) => {
            const Icon = t.icon
            const on = tab === t.key
            return (
              <NavLink
                key={t.key}
                to={t.path}
                className={
                  'flex items-center gap-2 border-b-2 px-5 py-4 text-sm font-bold transition ' +
                  (on ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700')
                }
              >
                <Icon className="h-4 w-4" />
                {t.label}
              </NavLink>
            )
          })}
        </div>
      </div>

      <div className="mx-auto max-w-6xl px-4 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">{ADMIN_TAB_META[tab].title}</h1>
          <p className="mt-1 text-sm text-gray-500">{ADMIN_TAB_META[tab].description}</p>
        </div>

        <div key={tab} className="animate-in fade-in duration-300">
          {renderTab(tab, sub, {
            status,
            q,
            onViewOrganizerFestivals: handleViewOrganizerFestivals,
            onDashboardNavigate: handleDashboardNavigate,
          })}
        </div>
      </div>
    </div>
  )
}

export default AdminDashboard
