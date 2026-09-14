import { useEffect, useRef, useState } from 'react'
import { listSettlements, settlementSummary, settlementDetail, settlementCommand } from '../api/settlementApi'

const money = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 })
const states = { PENDING: '계산 대기', CALCULATED: '계산 완료', HELD: '보류', CONFIRMED: '확정', PAID: '지급 완료', ADJUSTMENT_REQUIRED: '조정 필요' }
const amounts = { grossPaymentAmount: '총 결제액', grossRefundedFaceAmount: '환불 액면가', customerRefundAmount: '실제 환급액', cancellationPenaltyAmount: '취소 위약금', platformFeeAmount: '플랫폼 수수료', adjustmentAmount: '이전 정산 조정', payoutAmount: '주최자 지급액' }
const actions = { recalculate: '재계산', confirm: '확정', reapprove: '조정 지급액 재승인', hold: '보류', release: '보류 해제', 'mark-paid': '지급 완료 기록' }
const button = 'rounded-xl border border-gray-300 px-4 py-2 text-sm font-bold disabled:opacity-40'
const date = (value) => value ? new Date(value).toLocaleString('ko-KR') : '—'

export default function SettlementReport({ host = false }) {
  const [filters, setFilters] = useState({ status: '', festivalId: '', hostUserId: '', paymentMethod: '', from: '', to: '', dateBasis: 'SETTLEMENT_AT', testPayment: false })
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [rows, setRows] = useState([])
  const [summary, setSummary] = useState(null)
  const [pagination, setPagination] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [detail, setDetail] = useState(null)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [action, setAction] = useState('')
  const [memo, setMemo] = useState('')
  const [reference, setReference] = useState('')
  const [paidAt, setPaidAt] = useState('')
  const commandRef = useRef(null)
  useEffect(() => {
    const controller = new AbortController()
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
    if (params.from) params.from = new Date(`${params.from}T00:00:00+09:00`).toISOString()
    if (params.to) params.to = new Date(`${params.to}T00:00:00+09:00`).toISOString()
    if (host) delete params.hostUserId
    setLoading(true); setError('')
    Promise.all([listSettlements(host, { ...params, page, size: 20 }, controller.signal), settlementSummary(host, params, controller.signal)])
      .then(([list, total]) => { if (!controller.signal.aborted) { setRows(list.data.data); setPagination(list.data.meta.pagination); setSummary(total.data.data) } })
      .catch(() => { if (!controller.signal.aborted) setError('정산 내역을 불러오지 못했습니다. 다시 시도해 주세요.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [filters, page, refresh, host])
  async function open(id) {
    if (busyRef.current) return
    busyRef.current = true; setBusy(true); setError('')
    try { setDetail((await settlementDetail(host, id)).data.data) }
    catch { setError('정산 상세를 불러오지 못했습니다.') }
    finally { busyRef.current = false; setBusy(false) }
  }
  async function execute(event) {
    event.preventDefault()
    if (busyRef.current) return
    busyRef.current = true; setBusy(true); setError('')
    const body = { memo, paymentReference: reference || null, paidAt: paidAt ? new Date(paidAt).toISOString() : null }
    const identity = JSON.stringify([detail.id, action, body])
    if (commandRef.current?.identity !== identity) commandRef.current = { identity, key: crypto.randomUUID() }
    try {
      setDetail((await settlementCommand(detail.id, action, body, commandRef.current.key)).data.data)
      setAction(''); commandRef.current = null; setRefresh((v) => v + 1)
    } catch (e) { setError(e.response?.data?.message || '처리에 실패했습니다. 최신 상태를 확인해 주세요.') }
    finally { busyRef.current = false; setBusy(false) }
  }
  function filter(name, value) { setFilters((old) => ({ ...old, [name]: value })); setPage(0) }
  const allowed = detail ? (['PENDING', 'CALCULATED', 'HELD'].includes(detail.status) ? ['recalculate'] : []).concat(detail.status === 'CALCULATED' ? ['confirm', 'hold'] : [], detail.status === 'HELD' ? ['release'] : [], detail.status === 'CONFIRMED' ? ['mark-paid'] : [], detail.status === 'ADJUSTMENT_REQUIRED' && !detail.paidAt ? ['reapprove'] : []) : []
  return <div className="space-y-6 text-gray-900">
    <p className="text-sm text-gray-600">종료 24시간 후 자동 계산 · 운영자 확인 후 외부 송금 · 금액은 조회된 페스티벌의 전체 정산 합계입니다.</p>
    <div className="flex flex-wrap gap-3 rounded-2xl bg-gray-50 p-4">
      <label>기간 기준<select className="ml-2 rounded border p-2" value={filters.dateBasis} onChange={(e) => filter('dateBasis', e.target.value)}><option value="SETTLEMENT_AT">정산 계산일</option><option value="PAID_AT">결제일(해당 결제가 있는 행사)</option></select></label>
      {import.meta.env.DEV && <label>테스트 거래<input type="checkbox" checked={filters.testPayment} onChange={(e) => filter('testPayment', e.target.checked)} /></label>}
      <label>상태<select className="ml-2 rounded border p-2" value={filters.status} onChange={(e) => filter('status', e.target.value)}><option value="">전체</option>{Object.entries(states).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></label>
      {['festivalId', ...(!host ? ['hostUserId'] : [])].map((key) => <label key={key}>{key === 'festivalId' ? '페스티벌 ID' : '주최자 ID'}<input className="ml-2 w-24 rounded border p-2" type="number" min="1" value={filters[key]} onChange={(e) => filter(key, e.target.value)} /></label>)}
      <label>결제수단<select className="ml-2 rounded border p-2" value={filters.paymentMethod} onChange={(e) => filter('paymentMethod', e.target.value)}><option value="">전체</option><option value="CARD">카드</option><option value="EASY_PAY">간편결제</option><option value="VIRTUAL_ACCOUNT">무통장/가상계좌</option></select></label>
      {['from', 'to'].map((key) => <label key={key}>{key === 'from' ? '시작일' : '종료일(미포함)'}<input className="ml-2 rounded border p-2" type="date" value={filters[key]} onChange={(e) => filter(key, e.target.value)} /></label>)}
      <button className={button} onClick={() => setRefresh((v) => v + 1)}>새로고침</button>
    </div>
    {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{error}</p>}
    {loading ? <p role="status">정산 내역을 불러오는 중…</p> : !error && <>
      {summary && <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{['grossPaymentAmount', 'customerRefundAmount', 'platformFeeAmount', 'payoutAmount'].map((key) => <div className="rounded-2xl border p-5" key={key}><p>{amounts[key]}</p><strong className="mt-2 block text-xl">{money.format(summary[key])}</strong></div>)}</div>}
      {rows.length === 0 ? <p className="rounded-2xl border p-8">조회 조건에 해당하는 정산이 없습니다.</p> : <div className="overflow-x-auto rounded-2xl border"><table className="w-full min-w-[1000px] text-sm"><caption className="p-4 text-left font-bold">페스티벌별 정산 · KRW</caption><thead><tr>{['페스티벌', '총 결제액', '실제 환급액', '취소 위약금', '수수료', '지급액', '상태', '상세'].map((label) => <th className="p-3 text-left" key={label}>{label}</th>)}</tr></thead><tbody>{rows.map((row) => <tr key={row.id} className="border-t"><td className="p-3">{row.festivalName}<small className="block">#{row.festivalId} · 정산 #{row.id}</small></td>{['grossPaymentAmount', 'customerRefundAmount', 'cancellationPenaltyAmount', 'platformFeeAmount', 'payoutAmount'].map((key) => <td className="p-3" key={key}>{money.format(key === 'payoutAmount' ? (row.payableAmount ?? row[key]) : row[key])}</td>)}<td className="p-3">{states[row.status]}</td><td className="p-3"><button disabled={busy} className={button} onClick={() => open(row.id)}>상세</button></td></tr>)}</tbody></table></div>}
      <nav aria-label="정산 페이지" className="flex items-center gap-4"><button className={button} disabled={!pagination?.hasPrev} onClick={() => setPage((p) => p - 1)}>이전</button><span>{page + 1} / {Math.max(1, pagination?.totalPages || 0)}</span><button className={button} disabled={!pagination?.hasNext} onClick={() => setPage((p) => p + 1)}>다음</button></nav>
    </>}
    {detail && <section aria-label="정산 상세" className="space-y-4 rounded-2xl border bg-white p-6">
      <div className="flex justify-between"><h2 className="text-xl font-bold">{detail.festivalName} 정산 #{detail.id}</h2><button className={button} disabled={busy} onClick={() => { setDetail(null); setAction('') }}>상세 닫기</button></div>
      <p>{states[detail.status]} · 정산 가능 {date(detail.eligibleAt)} · 확정 {date(detail.confirmedAt)} · 지급 {date(detail.paidAt)}</p>
      {detail.holdMessage && <p role="status">{detail.holdMessage}</p>}
      <p className="rounded-xl bg-blue-50 p-4">지급액 = 총 결제액 − 환불 액면가 − 플랫폼 수수료 + 취소 위약금 + 이전 정산 조정액<br />카드·간편결제 7.5%, 무통장/가상계좌 5% (VAT 포함). 결제별 잔존 티켓 매출에 원 단위 내림을 적용합니다.</p>
      <dl className="grid gap-3 sm:grid-cols-3">{Object.entries(amounts).map(([key, label]) => <div key={key}><dt>{label}</dt><dd className="font-bold">{money.format(detail[key])}</dd></div>)}</dl>
      <p className="rounded-xl border p-3">최초 산출 지급액 {money.format(detail.payoutAmount)} · 지급 전 승인된 조정 {money.format(detail.confirmedAdjustmentAmount ?? 0)} · 현재 승인 지급액 {money.format(detail.payableAmount ?? detail.payoutAmount)}{detail.paidPayoutAmount != null && <> · 실제 지급 기록 {money.format(detail.paidPayoutAmount)}</>}</p>
      {detail.status === 'ADJUSTMENT_REQUIRED' && !detail.paidAt && <p>재승인 대상 지급액: {money.format(detail.proposedPayoutAmount)}. 기존 확정 내역은 보존됩니다.</p>}
      <h3 className="font-bold">결제수단별 집계</h3>
      {detail.adjustments?.length > 0 && <div><h3 className="font-bold">환불 조정</h3>{detail.adjustments.map((adjustment, i) => <p key={i}>{money.format(adjustment.amount)} · {adjustment.status === 'PRE_PAYMENT' ? '지급 전 조정' : adjustment.status === 'RECEIVABLE' ? `미수금 잔액 ${money.format(adjustment.remainingAmount)}` : '다음 정산에 반영'}</p>)}</div>}
      {['CARD', 'EASY_PAY', 'VIRTUAL_ACCOUNT'].map((method) => { const lines = detail.lines.filter((line) => line.paymentMethod === method); return <p key={method}>{method} · {lines.length}건 · 결제액 {money.format(lines.reduce((sum, line) => sum + line.grossAmount, 0))} · 수수료 {money.format(lines.reduce((sum, line) => sum + line.finalFeeAmount, 0))}</p> })}
      <details><summary>결제별 계산 내역 ({detail.lines.length}건)</summary><div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr>{['수단', '결제액', '환불 액면가', '환급액', '최초 수수료', '수수료 환입', '최종 수수료'].map((v) => <th className="p-2" key={v}>{v}</th>)}</tr></thead><tbody>{detail.lines.map((line, i) => <tr key={i}><td className="p-2">{line.paymentMethod}</td>{['grossAmount', 'refundedFaceAmount', 'customerRefundAmount', 'initialFeeAmount', 'feeReversalAmount', 'finalFeeAmount'].map((key) => <td className="p-2" key={key}>{money.format(line[key])}</td>)}</tr>)}</tbody></table></div></details>
      {!host && <>
        <div className="flex flex-wrap gap-2">{allowed.map((value) => <button className={button} disabled={busy} key={value} onClick={() => { setAction(value); setMemo(''); setReference(''); setPaidAt('') }}>{actions[value]}</button>)}</div>
        {action && <form onSubmit={execute} className="space-y-3 rounded-xl border border-amber-300 p-4"><p className="font-bold">{actions[action]} 처리를 확인해 주세요. 확정 후 일반 재계산은 차단됩니다.</p>{action === 'mark-paid' && <><p>외부 송금을 완료한 뒤 실제 지급 정보를 입력하세요.</p><label className="block">지급 식별자<input required maxLength={200} className="ml-2 rounded border p-2" value={reference} onChange={(e) => setReference(e.target.value)} /></label><label className="block">지급 시각<input required type="datetime-local" className="ml-2 rounded border p-2" value={paidAt} onChange={(e) => setPaidAt(e.target.value)} /></label></>}<label className="block">내부 메모<input maxLength={1000} className="ml-2 rounded border p-2" value={memo} onChange={(e) => setMemo(e.target.value)} /></label><button className={button} disabled={busy} type="submit">{busy ? '처리 중…' : '확인하고 실행'}</button><button className={`${button} ml-2`} disabled={busy} type="button" onClick={() => setAction('')}>취소</button></form>}
        <details><summary>내부 감사 이력</summary>{detail.auditLogs?.map((log) => <p className="py-2 text-sm" key={log.id}>{date(log.createdAt)} · {log.action} · {log.previousStatus} → {log.nextStatus} · 담당자 {log.actorUserId ?? '자동 처리'} · {log.memo}</p>)}</details>
      </>}
    </section>}
  </div>
}
