import { useState } from 'react'
import { CATEGORY_BADGE, DEFAULT_CATEGORY_BADGE_CLS, fetchFestivalSubmissions, reviewFestivalSubmission } from '../../data/admin'
import { Pagination, StatusBadge, Toolbar, approveBtn, confirmApprove, rejectBtn, useReviewList } from './ReviewListShared'
import CancellationRequests from './CancellationRequests'
import DetailModal from './DetailModal'

/* ---------- 서브탭 A: 페스티벌 등록 승인 (실제 API 연동) ---------- */

function FestivalApprovals({ initialQuery = '' }) {
  const list = useReviewList(
    fetchFestivalSubmissions,
    (it, q) => it.name.toLowerCase().includes(q) || it.host.toLowerCase().includes(q),
    initialQuery,
  )
  const [descriptionDetail, setDescriptionDetail] = useState(null)
  const [actionError, setActionError] = useState({})
  const [pendingId, setPendingId] = useState(null)
  const [rejectDraftId, setRejectDraftId] = useState(null)
  const [rejectReason, setRejectReason] = useState('')

  async function handleApprove(id) {
    if (!confirmApprove()) return
    setPendingId(id)
    setActionError((prev) => ({ ...prev, [id]: '' }))
    try {
      await reviewFestivalSubmission(id, 'PUBLISHED')
      list.updateStatus(id, 'APPROVED')
    } catch (error) {
      setActionError((prev) => ({ ...prev, [id]: error.message }))
    } finally {
      setPendingId(null)
    }
  }

  //주최자 신청 반려와 같은 방식 — 사유를 필수로 받아 주최자에게 그대로 전달한다.
  async function handleReject(id) {
    if (!rejectReason.trim()) {
      setActionError((prev) => ({ ...prev, [id]: '반려 사유를 입력해주세요.' }))
      return
    }
    setPendingId(id)
    setActionError((prev) => ({ ...prev, [id]: '' }))
    try {
      await reviewFestivalSubmission(id, 'REJECTED', rejectReason.trim())
      list.updateStatus(id, 'REJECTED')
      list.updateField(id, 'rejectReason', rejectReason.trim())
      setRejectDraftId(null)
      setRejectReason('')
    } catch (error) {
      setActionError((prev) => ({ ...prev, [id]: error.message }))
    } finally {
      setPendingId(null)
    }
  }

  return (
    <div>
      <Toolbar status={list.status} setStatus={list.setStatus} query={list.query} setQuery={list.setQuery} sort={list.sort} setSort={list.setSort} />

      {list.loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}

      {!list.loading && list.loadError && (
        <p className="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{list.loadError}</p>
      )}

      {!list.loading && !list.loadError && (
        <ul className="mt-5 space-y-4">
          {list.paged.map((f) => {
            return (
              <li key={f.id} className="flex flex-col gap-4 rounded-2xl border border-gray-200 p-5 sm:flex-row">
                <img
                  src={f.image || '/placeholder.jpg'}
                  alt={f.name}
                  className="h-40 w-full shrink-0 rounded-xl object-cover sm:h-28 sm:w-40"
                  crossOrigin="anonymous"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={f.status} />
                    <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + (CATEGORY_BADGE[f.category] ?? DEFAULT_CATEGORY_BADGE_CLS)}>
                      {f.category}
                    </span>
                  </div>
                  <p className="mt-2 text-lg font-extrabold text-gray-900">{f.name}</p>
                  <p className="text-sm text-gray-500">
                    {f.host} · {f.date} · {f.location}
                  </p>
                  <p className="mt-2 text-sm leading-relaxed text-gray-600 line-clamp-2">{f.description}</p>
                  <button
                    type="button"
                    onClick={() => setDescriptionDetail(f)}
                    className="mt-1 text-sm font-bold text-blue-600 hover:underline"
                  >
                    더보기
                  </button>

                  <div className="mt-3 flex flex-wrap gap-2">
                    {f.tickets.map((t) => (
                      <span key={t.name} className="rounded-lg bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600">
                        {t.name} · {t.price}
                      </span>
                    ))}
                  </div>

                  {f.status === 'REJECTED' && f.rejectReason && (
                    <p className="mt-2 text-sm text-red-600">반려 사유: {f.rejectReason}</p>
                  )}
                  {f.rawStatus === 'CLOSED' && <p className="mt-2 text-xs text-gray-500">기간이 끝나 종료된 페스티벌이에요.</p>}

                  {actionError[f.id] && <p className="mt-2 text-xs font-semibold text-red-600">{actionError[f.id]}</p>}

                  {rejectDraftId === f.id && (
                    <div className="mt-3 max-w-sm space-y-2">
                      <textarea
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="반려 사유를 입력하세요 (주최자에게 그대로 전달됩니다)"
                        rows={3}
                        className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => { setRejectDraftId(null); setRejectReason('') }}
                          disabled={pendingId === f.id}
                          className="rounded-xl border border-gray-200 px-4 py-2 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          취소
                        </button>
                        <button type="button" onClick={() => handleReject(f.id)} disabled={pendingId === f.id} className={rejectBtn}>
                          {pendingId === f.id ? '처리 중…' : '반려 확정'}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* 이미 처리된 페스티벌에는 버튼을 두지 않는다(승인 직후 반려 버튼이 잠깐 남던 문제). */}
                  {rejectDraftId !== f.id && f.status === 'PENDING' && (
                    <div className="mt-4 flex justify-end gap-2">
                      <button type="button" disabled={pendingId === f.id} onClick={() => handleApprove(f.id)} className={approveBtn}>
                        {pendingId === f.id ? '처리 중…' : '공개 승인'}
                      </button>
                      <button
                        type="button"
                        disabled={pendingId === f.id}
                        onClick={() => { setRejectDraftId(f.id); setRejectReason(''); setActionError((prev) => ({ ...prev, [f.id]: '' })) }}
                        className={rejectBtn}
                      >
                        반려
                      </button>
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {!list.loading && !list.loadError && list.filtered.length === 0 && (
        <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 페스티벌이 없습니다.</p>
      )}

      <Pagination page={list.page} pages={list.pages} setPage={list.setPage} />

      <DetailModal open={!!descriptionDetail} onClose={() => setDescriptionDetail(null)} title={descriptionDetail?.name}>
        <p className="text-sm leading-relaxed text-gray-600">{descriptionDetail?.description}</p>
      </DetailModal>
    </div>
  )
}

/* ---------- 서브탭 B: 행사 취소 승인 ---------- */

//정산 대시보드 하단에 있을 때는 운영자가 찾지 못해 승인이 멈췄다. 주최자 심사와 같은 자리에 둔다.
function CancellationApprovals() {
  return (
    <div>
      <h2 className="text-lg font-extrabold text-gray-900">행사 취소 승인</h2>
      <p className="mt-1 text-sm text-gray-500">
        주최자가 요청한 행사 취소를 승인하면 남은 티켓이 위약금 없이 전액 환불되고, 정산은 환불이 끝날 때까지 보류돼요.
      </p>
      <CancellationRequests />
    </div>
  )
}

/* ---------- 페스티벌 관리 (탭) ---------- */

const SUB_TABS = [
  { key: 'festival', label: '페스티벌 등록 승인' },
  { key: 'cancellation', label: '행사 취소 승인' },
]

function FestivalManagement({ initialQuery = '' }) {
  const [sub, setSub] = useState('festival')

  function renderSub() {
    if (sub === 'festival') return <FestivalApprovals key={initialQuery} initialQuery={initialQuery} />
    return <CancellationApprovals />
  }

  return (
    <div>
      {/* 서브탭 */}
      <div className="inline-flex gap-1 rounded-2xl bg-gray-100 p-1">
        {SUB_TABS.map((t) => {
          const on = sub === t.key
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setSub(t.key)}
              className={'rounded-xl px-5 py-2.5 text-sm font-bold transition ' + (on ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500 hover:text-gray-700')}
            >
              {t.label}
            </button>
          )
        })}
      </div>

      <div className="mt-6 rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">{renderSub()}</div>
    </div>
  )
}

export default FestivalManagement
