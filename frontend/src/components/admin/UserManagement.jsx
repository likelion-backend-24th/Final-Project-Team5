import { useEffect, useState } from 'react'
import { Search, CalendarDays } from 'lucide-react'
import { useAuth } from '../../context/AuthContext.jsx'
import { fetchAdminUsers, unsuspendUser } from '../../api/adminApi'
import { ACCOUNT_STATUS_META, ROLE_BADGE_META, PROVIDER_LABELS, formatDate } from '../../data/admin'
import Pagination from '../Pagination'
import SuspendUserModal from './SuspendUserModal'
import DetailModal from './DetailModal'

const PAGE_SIZE = 10

const ROLE_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'USER', label: 'USER' },
  { key: 'HOST', label: 'HOST' },
  { key: 'HELPER', label: 'HELPER' },
  { key: 'STOREHOST', label: 'STOREHOST' },
  { key: 'ADMIN', label: 'ADMIN' },
]

const STATUS_FILTERS = [
  { key: 'ALL', label: '전체' },
  { key: 'ACTIVE', label: ACCOUNT_STATUS_META.ACTIVE.label },
  { key: 'SUSPENDED', label: ACCOUNT_STATUS_META.SUSPENDED.label },
  { key: 'WITHDRAWN', label: ACCOUNT_STATUS_META.WITHDRAWN.label },
  { key: 'REVOKED', label: ACCOUNT_STATUS_META.REVOKED.label },
  { key: 'PENDING_ACTIVATION', label: ACCOUNT_STATUS_META.PENDING_ACTIVATION.label },
]

function RoleBadge({ role }) {
  const meta = ROLE_BADGE_META[role] ?? { label: role, cls: 'bg-gray-100 text-gray-600' }
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + meta.cls}>{meta.label}</span>
}

function StatusBadge({ status }) {
  const meta = ACCOUNT_STATUS_META[status] ?? { label: status, cls: 'bg-gray-100 text-gray-600' }
  return <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + meta.cls}>{meta.label}</span>
}

function ProviderBadges({ providers }) {
  if (providers == null) return <span className="text-gray-400">—</span>
  if (providers.length === 0) return <span className="text-gray-600">일반</span>
  return (
    <div className="flex flex-wrap gap-1">
      {providers.map((p) => (
        <span key={p} className="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-bold text-gray-600">
          {PROVIDER_LABELS[p] ?? p}
        </span>
      ))}
    </div>
  )
}

function ManageCell({ member, currentUserId, actionPendingId, onSuspend, onUnsuspend, error }) {
  const isSelf = member.id === currentUserId
  const isManageable = member.role !== 'ADMIN' && !isSelf && member.role !== 'HELPER' && member.role !== 'STOREHOST'
  const action = isManageable ? (member.status === 'ACTIVE' ? 'suspend' : member.status === 'SUSPENDED' ? 'unsuspend' : null) : null

  return (
    <div>
      {action === 'suspend' && (
        <button
          type="button"
          onClick={() => onSuspend(member)}
          className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-red-700"
        >
          정지
        </button>
      )}
      {action === 'unsuspend' && (
        <button
          type="button"
          disabled={actionPendingId === member.id}
          onClick={() => onUnsuspend(member)}
          className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {actionPendingId === member.id ? '처리 중…' : '정지 해제'}
        </button>
      )}
      {!action && <span className="text-gray-300">—</span>}
      {error && <p className="mt-2 text-xs font-semibold text-red-600">{error}</p>}
    </div>
  )
}

function StatusCell({ member, onViewSuspendDetail }) {
  return (
    <div>
      <StatusBadge status={member.status} />
      {member.status === 'SUSPENDED' && (
        <button
          type="button"
          onClick={() => onViewSuspendDetail(member)}
          className="mt-2 block text-sm font-bold text-blue-600 hover:underline"
        >
          사유 보기
        </button>
      )}
    </div>
  )
}

/** 어드민 회원 관리 — 전체 회원 조회·검색·필터와 계정 정지/정지 해제. OrganizerList의 구조를 따르되
 * 검색·필터·페이지네이션은 서버 사이드로 처리한다. */
function UserManagement() {
  const { user } = useAuth()
  const [items, setItems] = useState([])
  const [pagination, setPagination] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [refreshKey, setRefreshKey] = useState(0)

  const [rowError, setRowError] = useState({})
  const [actionPendingId, setActionPendingId] = useState(null)
  const [suspendTarget, setSuspendTarget] = useState(null)
  const [suspendDetail, setSuspendDetail] = useState(null)

  //입력이 멈추고 300ms 뒤에만 검색어를 반영한다. 서버 사이드 검색이라 매 타이핑마다 요청하면 낭비다.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(queryInput.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [queryInput])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setLoadError('')
    const params = { page, size: PAGE_SIZE }
    if (query) params.keyword = query
    if (roleFilter !== 'ALL') params.role = roleFilter
    if (statusFilter !== 'ALL') params.status = statusFilter
    fetchAdminUsers(params, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return
        const data = response.data.data
        //정지/정지해제로 현재 페이지의 마지막 회원이 사라지면 빈 페이지가 남는다 — 이전 페이지로 되돌아간다.
        if (data.length === 0 && page > 0) {
          setPage((p) => p - 1)
          return
        }
        setItems(data)
        setPagination(response.data.meta.pagination)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setLoadError(error.response?.data?.message || '회원 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [query, roleFilter, statusFilter, page, refreshKey])

  function selectRole(key) {
    setRoleFilter(key)
    setPage(0)
  }
  function selectStatus(key) {
    setStatusFilter(key)
    setPage(0)
  }

  function reload() {
    setRefreshKey((k) => k + 1)
  }

  async function handleUnsuspend(member) {
    if (!window.confirm(`${member.nickname} 님의 정지를 해제할까요?`)) return
    setActionPendingId(member.id)
    setRowError((prev) => ({ ...prev, [member.id]: '' }))
    try {
      await unsuspendUser(member.id)
      reload()
    } catch (error) {
      setRowError((prev) => ({
        ...prev,
        [member.id]: error.response?.data?.message || '요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.',
      }))
    } finally {
      setActionPendingId(null)
    }
  }

  return (
    <div className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8">
      {/* 툴바 */}
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-2">
          {ROLE_FILTERS.map((f) => {
            const on = roleFilter === f.key
            return (
              <button
                key={f.key}
                type="button"
                onClick={() => selectRole(f.key)}
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

        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap gap-2">
            {STATUS_FILTERS.map((f) => {
              const on = statusFilter === f.key
              return (
                <button
                  key={f.key}
                  type="button"
                  onClick={() => selectStatus(f.key)}
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
              value={queryInput}
              onChange={(e) => setQueryInput(e.target.value)}
              placeholder="닉네임 또는 이메일 검색"
              className="w-64 rounded-2xl border border-gray-200 bg-white py-2.5 pl-4 pr-10 text-sm text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            />
            <Search className="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          </div>
        </div>
      </div>

      {!loading && !loadError && <p className="mt-4 text-sm text-gray-500">총 {pagination?.totalItems ?? 0}명</p>}

      {loading && <p className="py-12 text-center text-sm font-semibold text-gray-400">불러오는 중…</p>}
      {!loading && loadError && <p className="py-12 text-center text-sm font-semibold text-red-500">{loadError}</p>}

      {!loading && !loadError && (
        <>
          {/* 테이블 (데스크톱) */}
          <div className="mt-5 hidden overflow-x-auto lg:block">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-left text-xs font-bold uppercase tracking-wide text-gray-400">
                  <th className="px-3 py-3">회원</th>
                  <th className="px-3 py-3">권한</th>
                  <th className="px-3 py-3">가입 경로</th>
                  <th className="px-3 py-3">가입일</th>
                  <th className="px-3 py-3">상태</th>
                  <th className="px-3 py-3 text-center">관리</th>
                </tr>
              </thead>
              <tbody>
                {items.map((m) => (
                  <tr key={m.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-3 py-4">
                      <p className="font-bold text-gray-900">{m.nickname}</p>
                      <p className="text-xs text-gray-500">{m.email}</p>
                    </td>
                    <td className="px-3 py-4">
                      <RoleBadge role={m.role} />
                    </td>
                    <td className="px-3 py-4">
                      <ProviderBadges providers={m.providers} />
                    </td>
                    <td className="px-3 py-4 text-gray-600">{formatDate(m.joinedAt)}</td>
                    <td className="px-3 py-4">
                      <StatusCell member={m} onViewSuspendDetail={setSuspendDetail} />
                    </td>
                    <td className="px-3 py-4 text-center">
                      <ManageCell
                        member={m}
                        currentUserId={user.id}
                        actionPendingId={actionPendingId}
                        onSuspend={setSuspendTarget}
                        onUnsuspend={handleUnsuspend}
                        error={rowError[m.id]}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 카드 (모바일/태블릿) */}
          <ul className="mt-5 space-y-3 lg:hidden">
            {items.map((m) => (
              <li key={m.id} className="rounded-2xl border border-gray-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-gray-900">{m.nickname}</p>
                    <p className="text-xs text-gray-500">{m.email}</p>
                  </div>
                  <StatusCell member={m} onViewSuspendDetail={setSuspendDetail} />
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                  <div>
                    <dt className="text-xs text-gray-400">권한</dt>
                    <dd>
                      <RoleBadge role={m.role} />
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-400">가입 경로</dt>
                    <dd>
                      <ProviderBadges providers={m.providers} />
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-400">가입일</dt>
                    <dd className="text-gray-700">{formatDate(m.joinedAt)}</dd>
                  </div>
                </dl>
                <div className="mt-3">
                  <ManageCell
                    member={m}
                    currentUserId={user.id}
                    actionPendingId={actionPendingId}
                    onSuspend={setSuspendTarget}
                    onUnsuspend={handleUnsuspend}
                    error={rowError[m.id]}
                  />
                </div>
              </li>
            ))}
          </ul>

          {items.length === 0 && <p className="py-12 text-center text-sm font-semibold text-gray-400">조건에 맞는 회원이 없습니다.</p>}

          <Pagination page={page + 1} totalPages={pagination?.totalPages ?? 0} onChange={(p) => setPage(p - 1)} />
        </>
      )}

      <DetailModal open={!!suspendDetail} onClose={() => setSuspendDetail(null)} title="정지 사유">
        {suspendDetail && (
          <div className="space-y-5">
            <StatusBadge status={suspendDetail.status} />
            <dl className="space-y-3 text-sm">
              <div className="flex items-center gap-2 text-gray-600">
                <CalendarDays className="h-4 w-4 text-gray-400" />
                정지일 {formatDate(suspendDetail.suspendedAt)}
              </div>
            </dl>
            <div>
              <p className="text-sm font-bold text-gray-900">정지 사유</p>
              <p className="mt-2 text-sm leading-relaxed text-gray-600">{suspendDetail.suspendReason || '입력된 사유가 없어요.'}</p>
            </div>
          </div>
        )}
      </DetailModal>

      {suspendTarget && (
        <SuspendUserModal
          member={suspendTarget}
          onClose={() => setSuspendTarget(null)}
          onSuspended={() => {
            setSuspendTarget(null)
            reload()
          }}
        />
      )}
    </div>
  )
}

export default UserManagement
