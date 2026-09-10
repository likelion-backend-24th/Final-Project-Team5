import { useState } from 'react'
import { Users, Wallet, CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import OrganizerManagement from '../components/admin/OrganizerManagement'
import SettlementDashboard from '../components/admin/SettlementDashboard'

const TABS = [
  { key: 'organizer', label: '주최자 관리', icon: Users },
  { key: 'settlement', label: '정산 대시보드', icon: Wallet },
]

/** 어드민 대시보드 — 주최자 관리(신청/페스티벌 승인, 목록)와 정산 대시보드를 한 화면에서 다룬다. */
function AdminDashboard() {
  const { user, isLoading: authLoading } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [tab, setTab] = useState('organizer')

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
        <div className="mx-auto flex max-w-6xl items-center gap-1 px-4">
          {TABS.map((t) => {
            const Icon = t.icon
            const on = tab === t.key
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => setTab(t.key)}
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
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">{tab === 'organizer' ? '주최자 관리' : '정산 대시보드'}</h1>
          <p className="mt-1 text-sm text-gray-500">
            {tab === 'organizer' ? '주최자 신청과 페스티벌 등록을 심사하고 승인·반려를 처리합니다.' : '플랫폼 거래·수수료 현황과 주최자별 정산 상태를 확인합니다.'}
          </p>
        </div>

        <div key={tab} className="animate-in fade-in duration-300">
          {tab === 'organizer' ? <OrganizerManagement /> : <SettlementDashboard />}
        </div>
      </div>
    </div>
  )
}

export default AdminDashboard
