import { AlertTriangle, TrendingUp, TrendingDown } from 'lucide-react'
import { settlementKpis, monthlyRevenue, settlements } from '../../data/admin'

function KpiCards() {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {settlementKpis.map((k) => (
        <div key={k.label} className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
          <p className="text-sm font-semibold text-gray-500">{k.label}</p>
          <p className="mt-2 text-2xl font-extrabold tracking-tight text-gray-900">{k.value}</p>
          <p className={'mt-2 inline-flex items-center gap-1 text-sm font-bold ' + (k.up ? 'text-blue-600' : 'text-red-500')}>
            {k.up ? <TrendingUp className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />}
            {k.delta}
            <span className="font-medium text-gray-400">전월 대비</span>
          </p>
        </div>
      ))}
    </div>
  )
}

/* 막대(거래대금) + 선(수수료) 혼합 차트 — 경량 커스텀 SVG */
function RevenueChart() {
  const width = 640
  const height = 260
  const padX = 44
  const padTop = 20
  const padBottom = 36
  const plotW = width - padX * 2
  const plotH = height - padTop - padBottom

  const maxGmv = Math.max(...monthlyRevenue.map((m) => m.gmv))
  const maxFee = Math.max(...monthlyRevenue.map((m) => m.fee))
  const stepX = plotW / monthlyRevenue.length
  const barW = stepX * 0.4

  const gmvY = (v) => padTop + plotH - (v / maxGmv) * plotH
  const feeY = (v) => padTop + plotH - (v / maxFee) * (plotH * 0.85)

  const linePoints = monthlyRevenue.map((m, i) => `${padX + stepX * i + stepX / 2},${feeY(m.fee)}`).join(' ')

  const gridLines = [0, 0.25, 0.5, 0.75, 1]

  return (
    <div className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-lg font-extrabold text-gray-900">월별 거래대금 vs 수수료</h3>
        <div className="flex items-center gap-4 text-xs font-semibold text-gray-500">
          <span className="flex items-center gap-1.5">
            <span className="h-3 w-3 rounded-sm bg-blue-600" />
            거래대금 (백만원)
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
            수수료 (백만원)
          </span>
        </div>
      </div>

      <svg viewBox={`0 0 ${width} ${height}`} className="mt-4 w-full" role="img" aria-label="월별 거래대금과 수수료 차트">
        {gridLines.map((g) => {
          const y = padTop + plotH * g
          return (
            <g key={g}>
              <line x1={padX} y1={y} x2={width - padX} y2={y} stroke="#f1f5f9" strokeWidth={1} />
              <text x={padX - 8} y={y + 4} textAnchor="end" className="fill-gray-400 text-[10px]">
                {Math.round(maxGmv * (1 - g))}
              </text>
            </g>
          )
        })}

        {monthlyRevenue.map((m, i) => {
          const x = padX + stepX * i + stepX / 2
          return (
            <g key={m.month}>
              <rect x={x - barW / 2} y={gmvY(m.gmv)} width={barW} height={padTop + plotH - gmvY(m.gmv)} rx={5} className="fill-blue-600" />
              <text x={x} y={height - 12} textAnchor="middle" className="fill-gray-500 text-[11px] font-semibold">
                {m.month}
              </text>
            </g>
          )
        })}

        <polyline points={linePoints} fill="none" stroke="#f59e0b" strokeWidth={2.5} strokeLinecap="round" />
        {monthlyRevenue.map((m, i) => {
          const x = padX + stepX * i + stepX / 2
          return <circle key={m.month} cx={x} cy={feeY(m.fee)} r={4} className="fill-white" stroke="#f59e0b" strokeWidth={2.5} />
        })}
      </svg>
    </div>
  )
}

function SettlementTable() {
  return (
    <div className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">
      <h3 className="text-lg font-extrabold text-gray-900">주최자별 정산 내역</h3>

      <div className="mt-5 overflow-x-auto">
        <table className="w-full min-w-[720px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-left text-xs font-bold uppercase tracking-wide text-gray-400">
              <th className="px-3 py-3">주최자명</th>
              <th className="px-3 py-3">운영 페스티벌</th>
              <th className="px-3 py-3">총 매출</th>
              <th className="px-3 py-3">수수료</th>
              <th className="px-3 py-3">정산 상태</th>
              <th className="px-3 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {settlements.map((s) => (
              <tr key={s.id} className="border-b border-gray-100 last:border-0">
                <td className="px-3 py-4 font-bold text-gray-900">{s.host}</td>
                <td className="px-3 py-4 text-gray-600">{s.festivals}개</td>
                <td className="px-3 py-4 font-semibold text-gray-900">{s.revenue}</td>
                <td className="px-3 py-4 text-gray-600">{s.fee}</td>
                <td className="px-3 py-4">
                  <span
                    className={
                      'rounded-full px-2.5 py-1 text-xs font-bold ' +
                      (s.status === '정산완료' ? 'bg-gray-100 text-gray-600' : 'bg-orange-100 text-orange-700')
                    }
                  >
                    {s.status}
                  </span>
                </td>
                <td className="px-3 py-4">
                  <div className="flex justify-end gap-2">
                    <button type="button" className="rounded-xl border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:bg-gray-50">
                      상세보기
                    </button>
                    <button
                      type="button"
                      disabled={s.status === '정산완료'}
                      onClick={() => window.confirm('정산을 처리하시겠습니까?')}
                      className="rounded-xl bg-blue-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
                    >
                      정산 처리
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function SettlementDashboard() {
  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
        <p className="text-sm font-semibold text-amber-700">정산 로직은 개발 예정이며 현재는 목업 데이터로 표시됩니다.</p>
      </div>

      <KpiCards />
      <RevenueChart />
      <SettlementTable />
    </div>
  )
}

export default SettlementDashboard
