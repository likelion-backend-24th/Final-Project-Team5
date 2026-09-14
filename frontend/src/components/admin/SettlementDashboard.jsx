import SettlementReport from '../SettlementReport'
import CancellationRequests from './CancellationRequests'

/** 운영자 정산 대시보드 — 페스티벌별 정산 현황과, 그 아래 주최자 귀책 행사 취소 승인 요청. */
function SettlementDashboard() {
  return (
    <div className="space-y-6">
      <SettlementReport />
      <section className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-extrabold text-gray-900">행사 취소 승인 요청</h2>
        <p className="mt-1 text-sm text-gray-500">
          승인하면 남은 티켓이 위약금 없이 전액 환불되고 정산은 환불이 끝날 때까지 보류돼요.
        </p>
        <CancellationRequests />
      </section>
    </div>
  )
}

export default SettlementDashboard
