import { CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import HostTabs from '../components/HostTabs'
import SettlementReport from '../components/SettlementReport'

/**
 * 주최자 본인의 정산 목록·상세(읽기 전용). 관리자 정산 대시보드와 같은 SettlementReport를 host 모드로 쓰고,
 * 컨테이너 폭(max-w-6xl)도 관리자 화면과 맞춰 접힘 영역을 열어도 폭이 흔들리지 않게 한다.
 */
function HostSettlements() {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center">
        <p role="status" className="text-sm text-gray-400">불러오는 중…</p>
      </main>
    )
  }

  if (user?.role !== 'HOST') {
    return (
      <main className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-6 text-center">
        <CircleAlertIcon className="h-10 w-10 text-red-500" aria-hidden="true" />
        <h1 className="text-xl font-extrabold text-gray-900">주최자만 이용 가능한 페이지입니다</h1>
        <p className="text-sm text-gray-500">주최자 승인을 받은 계정으로 로그인해주세요.</p>
      </main>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <HostTabs />

      <main className="mx-auto max-w-6xl px-4 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">내 정산</h1>
          <p className="mt-1 text-sm text-gray-500">행사가 끝난 뒤 계산된 정산 금액과 지급 진행 상황을 확인합니다.</p>
        </div>

        <SettlementReport host />
      </main>
    </div>
  )
}

export default HostSettlements
