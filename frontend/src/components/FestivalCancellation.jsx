import { useEffect, useRef, useState } from 'react'
import apiClient from '../api/client'
export default function FestivalCancellation({ festivalId }) {
  const [reason, setReason] = useState('')
  const [requests, setRequests] = useState([])
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    if (festivalId) return
    let active = true
    apiClient.get('/api/admin/festivals/cancellation-requests').then((r) => { if (active) setRequests(r.data.data) })
      .catch(() => { if (active) setMessage('행사 취소 요청을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [festivalId, refresh])
  async function submit(id) {
    if (lock.current || !window.confirm(festivalId ? '행사 취소를 요청하면 신규 예매가 중단됩니다. 계속할까요?' : '주최자 귀책 전액 환불을 승인할까요? 승인 후 환불 작업을 되돌릴 수 없습니다.')) return
    lock.current = true; setBusy(true)
    try {
      await apiClient.post(festivalId ? `/api/host/festivals/${id}/cancellation-request` : `/api/admin/festivals/${id}/approve-cancellation`, { reason })
      setMessage(festivalId ? '취소 요청이 접수되었습니다. 운영자 승인 후 전액 환불이 진행됩니다.' : '승인했습니다. 환불 배치가 순차적으로 처리합니다.')
      setRefresh((v) => v + 1)
    } catch { setMessage('처리에 실패했습니다. 최신 행사 상태를 확인해 주세요.') }
    finally { lock.current = false; setBusy(false) }
  }
  return <section className="my-6 space-y-3 rounded-2xl border border-amber-200 p-5"><h2 className="font-bold">주최자 귀책 행사 취소</h2>
    {message && <p role="status">{message}</p>}
    {festivalId ? <><p>운영자 승인 후 입장 여부와 무관하게 남은 티켓을 위약금 없이 전액 환불합니다.</p><label>취소 사유<input className="mx-3 rounded border p-2" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></label><button className="rounded border px-4 py-2 disabled:opacity-40" disabled={busy || !reason.trim()} onClick={() => submit(festivalId)}>취소 승인 요청</button></> : requests.length === 0 ? <p>대기 중인 행사 취소 요청이 없습니다.</p> : requests.map((r) => <div key={r.festivalId} className="flex flex-wrap items-center justify-between gap-2 border-t py-3"><p>{r.name} · #{r.festivalId} · {r.reason}</p><button className="rounded border px-4 py-2 disabled:opacity-40" disabled={busy || r.approved} onClick={() => submit(r.festivalId)}>{r.approved ? '환불 진행 중' : '전액 환불 승인'}</button></div>)}
  </section>
}
