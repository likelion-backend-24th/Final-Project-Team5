import { useState } from 'react'
import { UserIcon, TicketIcon, MegaphoneIcon, SettingsIcon, CalendarDaysIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import MyPageInfoTab from '../components/MyPageInfoTab'
import MyPageReservationsTab from '../components/MyPageReservationsTab'
import MyPageHostTab from '../components/MyPageHostTab'
import MyPageSettingsTab from '../components/MyPageSettingsTab'

const ROLE_META = {
  USER: { label: '일반 회원', badge: 'bg-gray-100 text-gray-600' },
  HOST: { label: '주최자', badge: 'bg-blue-50 text-blue-600' },
  ADMIN: { label: '운영자', badge: 'bg-purple-50 text-purple-600' },
}

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'

function MyPage({ initialTab = 'info' }) {
  const { user } = useAuth()
  const [active, setActive] = useState(initialTab)

  if (!user) return null

  const tabs = [
    { key: 'info', label: '내 정보', icon: UserIcon, show: true },
    { key: 'reservations', label: '내 예약', icon: TicketIcon, show: true },
    { key: 'host', label: '주최자 관리', icon: MegaphoneIcon, show: user.role === 'HOST' },
    { key: 'settings', label: '설정', icon: SettingsIcon, show: true },
  ]
  const visibleTabs = tabs.filter((t) => t.show)
  const roleMeta = ROLE_META[user.role] ?? ROLE_META.USER

  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-8 md:py-12">
      <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">마이페이지</h1>

      <div className={`${cardClass} mt-6`}>
        <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-blue-600 text-2xl font-extrabold text-white">
              {user.nickname?.charAt(0)}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <p className="text-xl font-extrabold text-gray-900">{user.nickname}</p>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${roleMeta.badge}`}>
                  {roleMeta.label}
                </span>
              </div>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-gray-400">
                <CalendarDaysIcon className="h-4 w-4" />
                가입일 {user.createdAt?.slice(0, 10)}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-8 flex flex-col gap-6 md:flex-row md:gap-8">
        <nav className="md:w-56 md:shrink-0">
          <div className="flex gap-2 overflow-x-auto pb-1 md:flex-col md:overflow-visible md:pb-0">
            {visibleTabs.map((t) => {
              const Icon = t.icon
              const on = active === t.key
              return (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => setActive(t.key)}
                  className={`flex shrink-0 items-center gap-2.5 whitespace-nowrap rounded-2xl px-4 py-3 text-sm font-bold transition md:w-full ${
                    on ? 'bg-blue-600 text-white shadow-sm' : 'bg-white text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {t.label}
                </button>
              )
            })}
          </div>
        </nav>

        <div className="min-w-0 flex-1">
          {active === 'info' && <MyPageInfoTab user={user} />}
          {active === 'reservations' && <MyPageReservationsTab />}
          {active === 'host' && <MyPageHostTab />}
          {active === 'settings' && <MyPageSettingsTab user={user} />}
        </div>
      </div>
    </div>
  )
}

export default MyPage