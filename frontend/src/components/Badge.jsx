const VARIANTS = {
  accent: 'inline-flex items-center rounded-full bg-brand-blue/90 px-2.5 py-1 text-xs font-bold text-white',
  magenta: 'inline-flex items-center rounded-full bg-brand-magenta/90 px-2.5 py-1 text-xs font-bold text-white',
  coral: 'inline-flex items-center rounded-full bg-brand-coral/90 px-2.5 py-1 text-xs font-bold text-white',
  amber: 'inline-flex items-center rounded-full bg-amber-500/90 px-2.5 py-1 text-xs font-bold text-white',
  teal: 'inline-flex items-center rounded-full bg-teal-600/90 px-2.5 py-1 text-xs font-bold text-white',
  secondary: 'inline-block rounded-md bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600',
  danger: 'inline-flex items-center rounded-full bg-red-500 px-2.5 py-1 text-xs font-bold text-white',
}

// 페스티벌 카드·배너의 짧은 홍보 문구(festival.badge)를 의미에 맞는 색으로 갈라준다.
// 전부 파란색이면 "HOT"과 "예매 오픈"이 시각적으로 구분되지 않는다. 목록에 없는 문구는
// 기본(accent, 파란색)으로 떨어져 새 문구가 추가돼도 깨지지 않는다.
const LABEL_VARIANTS = {
  HOT: 'coral',
  마감임박: 'magenta',
  지역행사: 'amber',
  접수중: 'teal',
}

export function badgeVariantForLabel(label) {
  return LABEL_VARIANTS[label] ?? 'accent'
}

function Badge({ variant = 'accent', className = '', children }) {
  return <span className={`${VARIANTS[variant]} ${className}`.trim()}>{children}</span>
}

export default Badge