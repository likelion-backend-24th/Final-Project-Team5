import { NavLink } from 'react-router-dom'
import { CalendarDaysIcon, WalletIcon } from 'lucide-react'

const TABS = [
  { to: '/host/festivals', label: '내 페스티벌', icon: CalendarDaysIcon },
  { to: '/host/settlements', label: '내 정산', icon: WalletIcon },
]

/**
 * 주최자 화면 상단 탭(내 페스티벌 / 내 정산). 어드민 대시보드의 탭 바와 같은 형태라
 * 두 화면을 오갈 때 제목·버튼 위치가 움직이지 않는다. NavLink는 하위 경로(/host/festivals/:id)에서도 활성 처리된다.
 */
function HostTabs() {
  return (
    <div className="border-b border-gray-200 bg-white">
      <nav aria-label="주최자 메뉴" className="mx-auto flex max-w-6xl items-center gap-1 px-4">
        {TABS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              'flex items-center gap-2 border-b-2 px-5 py-4 text-sm font-bold transition ' +
              (isActive ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700')
            }
          >
            <Icon className="h-4 w-4" />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

export default HostTabs
