import SettlementReport from '../SettlementReport'
import FestivalCancellation from '../FestivalCancellation'
export default function SettlementDashboard() { return <div className="space-y-6"><SettlementReport /><details className="rounded-2xl border border-gray-200 bg-white px-5 py-4"><summary className="cursor-pointer text-sm font-bold text-gray-700">행사 취소 승인 요청 확인</summary><FestivalCancellation /></details></div> }
