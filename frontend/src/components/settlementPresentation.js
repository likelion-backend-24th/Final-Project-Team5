export const money = (value) => new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }).format(value || 0)
export const date = (value) => value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '아직 처리되지 않았어요'
export const states = {
  PENDING: { label: '정산 대기', style: 'bg-gray-100 text-gray-600', description: '행사가 끝난 뒤 24시간이 지나면 자동으로 계산해요.' },
  CALCULATED: { label: '검토 대기', style: 'bg-blue-50 text-blue-700', description: '계산이 끝났어요. 관리자가 금액을 확인하면 지급을 준비해요.' },
  HELD: { label: '보류', style: 'bg-amber-50 text-amber-800', description: '환불 내역이나 결제 정보를 확인하고 있어요. 확인이 끝날 때까지 지급하지 않아요.' },
  CONFIRMED: { label: '지급 대기', style: 'bg-indigo-50 text-indigo-700', description: '금액 검토가 완료됐어요. 주최자에게 지급할 차례예요.' },
  PAID: { label: '지급 완료', style: 'bg-emerald-50 text-emerald-700', description: '주최자에게 지급한 내역을 기록했어요.' },
  ADJUSTMENT_REQUIRED: { label: '환불 조정', style: 'bg-orange-50 text-orange-800', description: '확정 후 환불이 발생했어요. 지급 전이면 금액을 다시 승인하고, 지급 후이면 다음 정산에서 차감해요.' },
}
export const primaryButton = 'inline-flex items-center justify-center gap-2 rounded-xl bg-brand-blue px-4 py-2.5 text-sm font-bold text-white transition hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-40'
export const secondaryButton = 'inline-flex items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-600 transition hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-40'
export const inputClass = 'w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm text-gray-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100'
