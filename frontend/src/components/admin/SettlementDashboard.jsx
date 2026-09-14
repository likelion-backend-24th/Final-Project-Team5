import SettlementReport from '../SettlementReport'
import CancellationRequests from './CancellationRequests'

export default function SettlementDashboard() {
  return (
    <div className="space-y-6">
      <SettlementReport />
      <section className="rounded-2xl border border-gray-200 bg-white px-5 py-4">
        <h2 className="text-sm font-bold text-gray-700">행사 취소 승인 요청</h2>
        <CancellationRequests />
      </section>
    </div>
  )
}
