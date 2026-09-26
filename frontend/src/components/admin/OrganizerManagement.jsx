import { useEffect, useMemo, useState } from 'react'
import { Search, Mail, Phone, CalendarDays } from 'lucide-react'
import {
  ACCOUNT_STATUS_META,
  fetchOrganizerApplications,
  reviewOrganizerApplication,
  fetchOrganizers,
} from '../../data/admin'
import { Pagination, PAGE_SIZE, StatusBadge, Toolbar, approveBtn, confirmApprove, rejectBtn, useReviewList } from './ReviewListShared'
import DetailModal from './DetailModal'

/* ---------- 서브탭 A: 주최자 신청 승인 (실제 API 연동) ---------- */

function OrganizerApprovals() {
  const list = useReviewList(fetchOrganizerApplications, (it, q) => it.email.toLowerCase().includes(q) || it.name.toLowerCase().includes(q))
  const [detail, setDetail] = useState(null)
  const [rejectDraftId, setRejectDraftId] = useState(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState({})
  const [pendingId, setPendingId] = useState(null)

  function setError(id, message) {
    setActionError((prev) => ({ ...prev, [id]: message }))
  }

  async function handleApprove(id) {
    if (!confirmApprove()) return
    setPendingId(id)
    setError(id, '')
    try {
      const resultStatus = await reviewOrganizerApplication(id, { status: 'APPROVED' })
      //권한 부여 응답을 못 받으면 APPROVAL_PENDING으로 남는다(서버 배치가 자동 재시도, 다시 눌러도 됨).
      list.updateStatus(id, resultStatus ?? 'APPROVED')
    } catch (error) {
      setError(id, error.message)
    } finally {
      setPendingId(null)
    }
  }

  function openRejectDraft(id) {
    setRejectDraftId(id)
    setRejectReason('')
    setError(id, '')
  }
  function cancelRejectDraft() {
    setRejectDraftId(null)
    setRejectReason('')
  }

  // 실제 백엔드는 반려 시 사유(rejectReason)를 필수로 받기 때문에, 참고 디자인의 즉시-반려 버튼 대신
  // 사유를 입력받는 작은 폼을 펼친다.
  async function handleReject(id) {
    if (!rejectReason.trim()) {
      setError(id, '반려 사유를 입력해주세요.')
      return
    }
    setPendingId(id)
    setError(id, '')
    try {
      await reviewOrganizerApplication(id, { status: 'REJECTED', rejectReason: rejectReason.trim() })
      list.updateStatus(id, 'REJECTED')
      setRejectDraftId(null)
      setRejectReason('')
    } catch (error) {
      setError(id, error.message)
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
        <ul className="mt-5 space-y-3">
          {list.paged.map((a) => (
            <li key={a.id} className="flex flex-col gap-4 rounded-2xl border border-gray-200 p-5 md:flex-row md:items-center md:justify-between">
              <div className="flex items-start gap-4">
                <StatusBadge status={a.status} />
                <div>
                  <p className="font-bold text-gray-900">{a.email}</p>
                  <p className="mt-0.5 text-sm text-gray-500">
                    {a.name} · 신청일 {a.appliedAt}
                    {a.reviewedAt && ` · 처리일 ${a.reviewedAt}`}
                  </p>
                  {a.status === 'REJECTED' && a.rejectReason && (
                    <p className="mt-1 text-sm text-red-600">반려 사유: {a.rejectReason}</p>
                  )}
                  {a.status === 'APPROVAL_PENDING' && (
                    <p className="mt-1 text-xs text-blue-600">권한 부여 확인 중이에요. 잠시 뒤 자동으로 승인 처리되며, 승인을 다시 눌러도 됩니다.</p>
                  )}
                  <button type="button" onClick={() => setDetail(a)} className="mt-2 text-sm font-bold text-blue-600 hover:underline">
                    소개글 보기
                  </button>

                  {actionError[a.id] && <p className="mt-2 text-xs font-semibold text-red-600">{actionError[a.id]}</p>}

                  {rejectDraftId === a.id && (
                    <div className="mt-3 max-w-sm space-y-2">
                      <textarea
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="반려 사유를 입력하세요"
                        rows={3}
                        className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={cancelRejectDraft}
                          disabled={pendingId === a.id}
                          className="rounded-xl border border-gray-200 px-4 py-2 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          취소
                        </button>
                        <button type="button" onClick={() => handleReject(a.id)} disabled={pendingId === a.id} className={rejectBtn}>
                          {pendingId === a.id ? '처리 중…' : '반려 확정'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* 이미 승인·반려된 신청에는 버튼을 아예 두지 않는다(반려 후에도 승인 버튼이 남아 눌리던 문제). */}
              {rejectDraftId !== a.id && (a.status === 'PENDING' || a.status === 'APPROVAL_PENDING') && (
                <div className="flex shrink-0 gap-2">
                  <button type="button" disabled={pendingId === a.id} onClick={() => handleApprove(a.id)} className={approveBtn}>
                    {a.status === 'APPROVAL_PENDING' ? '승인 재시도' : '승인'}
                  </button>
                  {a.status === 'PENDING' && (
                    <button type="button" disabled={pendingId === a.id} onClick={() => openRejectDraft(a.id)} className={rejectBtn}>
                      반려
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {!list.loading && !list.loadError && list.filtered.length === 0 && (
        <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 신청이 없습니다.</p>
      )}

      <Pagination page={list.page} pages={list.pages} setPage={list.setPage} />

      <DetailModal open={!!detail} onClose={() => setDetail(null)} title="주최자 신청 상세">
        {detail && (
          <div className="space-y-5">
            <StatusBadge status={detail.status} />
            <dl className="space-y-3 text-sm">
              <div className="flex items-center gap-2 text-gray-600">
                <Mail className="h-4 w-4 text-gray-400" />
                {detail.email}
              </div>
              <div className="flex items-center gap-2 text-gray-600">
                <Phone className="h-4 w-4 text-gray-400" />
                <span className="text-gray-400">연락처</span> {detail.contact || '입력 안 함'}
              </div>
              {detail.rejectReason && (
                <div className="rounded-xl bg-red-50 px-3 py-2 text-red-700">반려 사유: {detail.rejectReason}</div>
              )}
              <div className="flex items-center gap-2 text-gray-600">
                <CalendarDays className="h-4 w-4 text-gray-400" />
                신청일 {detail.appliedAt}
              </div>
            </dl>
            <div>
              <p className="text-sm font-bold text-gray-900">소개글</p>
              <p className="mt-2 text-sm leading-relaxed text-gray-600">{detail.intro}</p>
            </div>
          </div>
        )}
      </DetailModal>
    </div>
  )
}

/* ---------- 서브탭 B: 주최자 목록 ---------- */

const ACCOUNT_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'ACTIVE', label: '활동중' },
  { key: 'SUSPENDED', label: '정지됨' },
]

function OrganizerList({ onViewFestivals }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [account, setAccount] = useState('ALL')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)

  useEffect(() => {
    let cancelled = false
    async function loadOrganizers() {
      try {
        const data = await fetchOrganizers()
        if (!cancelled) setItems(data)
      } catch (error) {
        if (!cancelled) setLoadError(error.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadOrganizers()
    return () => {
      cancelled = true
    }
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return items
      .filter((o) => account === 'ALL' || o.accountStatus === account)
      .filter((o) => q === '' || o.nickname.toLowerCase().includes(q) || o.email.toLowerCase().includes(q))
  }, [items, account, query])

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pages)
  const paged = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)

  return (
    <div>
      {/* 툴바 */}
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-2">
          {ACCOUNT_FILTERS.map((f) => {
            const on = account === f.key
            return (
              <button
                key={f.key}
                type="button"
                onClick={() => {
                  setAccount(f.key)
                  setPage(1)
                }}
                className={
                  'rounded-full px-4 py-2 text-sm font-bold transition ' +
                  (on ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200')
                }
              >
                {f.label}
              </button>
            )
          })}
        </div>
        <div className="relative">
          <input
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setPage(1)
            }}
            placeholder="닉네임 또는 이메일 검색"
            className="w-64 rounded-2xl border border-gray-200 bg-white py-2.5 pl-4 pr-10 text-sm text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <Search className="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
        </div>
      </div>

      {loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}
      {!loading && loadError && <p className="py-12 text-center text-sm font-semibold text-red-500">{loadError}</p>}

      {!loading && !loadError && (
        <>
          {/* 테이블 (데스크톱) */}
          <div className="mt-5 hidden overflow-x-auto lg:block">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-left text-xs font-bold uppercase tracking-wide text-gray-400">
                  <th className="px-3 py-3">주최자</th>
                  <th className="px-3 py-3">상태</th>
                  <th className="px-3 py-3">가입일</th>
                  <th className="px-3 py-3 text-center">등록 페스티벌</th>
                </tr>
              </thead>
              <tbody>
                {paged.map((o) => (
                  <tr key={o.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-3 py-4">
                      <p className="font-bold text-gray-900">{o.nickname}</p>
                      <p className="text-xs text-gray-500">{o.email}</p>
                    </td>
                    <td className="px-3 py-4">
                      <AccountBadge status={o.accountStatus} />
                    </td>
                    <td className="px-3 py-4 text-gray-600">{o.joinedAt}</td>
                    <td className="px-3 py-4 text-center">
                      <button
                        type="button"
                        onClick={() => onViewFestivals(o.nickname)}
                        className="rounded-lg px-2 py-1 font-bold text-blue-600 transition hover:bg-blue-50 hover:underline"
                      >
                        {o.festivalCount}개
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 카드 (모바일/태블릿) */}
          <ul className="mt-5 space-y-3 lg:hidden">
            {paged.map((o) => (
              <li key={o.id} className="rounded-2xl border border-gray-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-gray-900">{o.nickname}</p>
                    <p className="text-xs text-gray-500">{o.email}</p>
                  </div>
                  <AccountBadge status={o.accountStatus} />
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                  <div>
                    <dt className="text-xs text-gray-400">가입일</dt>
                    <dd className="text-gray-700">{o.joinedAt}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-400">등록 페스티벌</dt>
                    <dd>
                      <button type="button" onClick={() => onViewFestivals(o.nickname)} className="font-bold text-blue-600 hover:underline">
                        {o.festivalCount}개
                      </button>
                    </dd>
                  </div>
                </dl>
              </li>
            ))}
          </ul>

          {filtered.length === 0 && <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 주최자가 없습니다.</p>}

          <Pagination page={current} pages={pages} setPage={setPage} />
        </>
      )}
    </div>
  )
}

function AccountBadge({ status }) {
  const meta = ACCOUNT_STATUS_META[status]
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + meta.cls}>{meta.label}</span>
}

/* ---------- 주최자 관리 (탭 1) ---------- */

const SUB_TABS = [
  { key: 'organizer', label: '주최자 신청 승인' },
  { key: 'list', label: '주최자 목록' },
]

function OrganizerManagement({ onViewFestivals = () => {} }) {
  const [sub, setSub] = useState('organizer')

  function renderSub() {
    if (sub === 'organizer') return <OrganizerApprovals />
    return <OrganizerList onViewFestivals={onViewFestivals} />
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

export default OrganizerManagement
