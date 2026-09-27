import { useEffect, useState } from 'react'
import {
  RotateCw,
  Users,
  Megaphone,
  CalendarCheck,
  CalendarClock,
  UserPlus,
  ClipboardCheck,
  XCircle,
  RotateCcw,
} from 'lucide-react'
import { fetchAdminSummary, fetchAdminUsers, fetchAdminHosts } from '../../api/adminApi'
import { fetchFestivalSubmissionsPage, fetchOrganizerApplications, fetchFestivalOperationsPage, OPERATION_STATUS_META } from '../../data/admin'
import { StatusBadge } from './ReviewListShared'
import { SalesProgress, HighlightBadges } from './FestivalOperations'

const SALES_CARD_COUNT = 4
//3일 이상 대기하면 강조한다(운영자가 놓치기 쉬운 임계값, 프론트 전용).
const WAITING_HIGHLIGHT_DAYS = 3

//data/admin.js의 formatDate가 만드는 'YYYY.MM.DD' 문자열을 다시 Date로 되돌린다.
//공용 매핑 함수(mapFestivalSubmission)를 건드리지 않고 대기일수만 로컬로 계산하기 위함이다.
function parseFormattedDate(value) {
  if (!value) return null
  const [y, m, d] = value.split('.').map(Number)
  if (!y || !m || !d) return null
  return new Date(y, m - 1, d)
}

function daysWaiting(appliedAt) {
  const applied = parseFormattedDate(appliedAt)
  if (!applied) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((today - applied) / 86400000)
}

function formatCount(value) {
  return value.toLocaleString()
}

function formatClock(date) {
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

/* ---------- 로컬 프레젠테이션 컴포넌트 ---------- */

function SectionTitle({ children }) {
  return <h2 className="text-sm font-bold text-gray-900">{children}</h2>
}

function StatCard({ icon: Icon, iconCls, valueCls = 'text-gray-900', label, loading, error, value, suffix, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-2xl border border-gray-200 bg-white p-5 text-left shadow-sm transition hover:border-gray-300"
    >
      <div className={'flex h-10 w-10 items-center justify-center rounded-xl ' + iconCls}>
        <Icon className="h-5 w-5" />
      </div>
      {loading ? (
        <div className="mt-4 h-8 w-16 animate-pulse rounded bg-gray-100" />
      ) : (
        <p className={'mt-4 text-3xl font-extrabold ' + valueCls}>{error ? '—' : formatCount(value)}</p>
      )}
      <p className="mt-1 flex items-center gap-1.5 text-sm text-gray-500">
        {label}
        {suffix}
      </p>
    </button>
  )
}

function PendingStatCard({ icon: Icon, label, loading, error, value, monitorOnly, onClick }) {
  const active = !loading && !error && value > 0
  const iconCls = active ? (monitorOnly ? 'bg-blue-50 text-blue-600' : 'bg-amber-100 text-amber-700') : 'bg-gray-100 text-gray-400'
  const valueCls = active ? (monitorOnly ? 'text-blue-600' : 'text-amber-700') : 'text-gray-300'
  const suffix = active ? (
    <span className={'text-xs font-bold ' + (monitorOnly ? 'text-blue-600' : 'text-amber-700')}>
      {monitorOnly ? '진행 중' : '처리 필요'}
    </span>
  ) : null
  return (
    <StatCard icon={Icon} iconCls={iconCls} valueCls={valueCls} label={label} loading={loading} error={error} value={value} suffix={suffix} onClick={onClick} />
  )
}

function SectionLink({ onClick, children }) {
  return (
    <button type="button" onClick={onClick} className="text-sm font-bold text-blue-600 hover:underline">
      {children}
    </button>
  )
}

function ListRow({ onClick, title, subtitle, right }) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className="flex w-full items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 text-left transition hover:border-gray-300"
      >
        <div className="min-w-0">
          <p className="truncate text-sm font-bold text-gray-900">{title}</p>
          <p className="mt-0.5 truncate text-xs text-gray-500">{subtitle}</p>
        </div>
        <div className="shrink-0">{right}</div>
      </button>
    </li>
  )
}

function ListPanel({ loading, error, isEmpty, emptyMessage, children }) {
  if (loading) return <p className="py-12 text-center text-sm text-gray-400">불러오는 중…</p>
  if (error) return <p className="py-12 text-center text-sm text-gray-400">불러오지 못했어요. 새로고침해주세요.</p>
  if (isEmpty) return <p className="py-12 text-center text-sm text-gray-400">{emptyMessage}</p>
  return <ul className="space-y-2">{children}</ul>
}

/* ---------- 대시보드 ---------- */

const EMPTY_SECTION = { loading: true, error: false, data: null }

/** 어드민 패널 대시보드 탭 — 운영 현황 요약과 처리 대기 항목을 한눈에 보여주고, 각 항목에서
 * 해당 탭·서브탭·필터로 바로 이동할 수 있게 한다. onNavigate({ tab, ...초기값 })을 호출해 이동한다. */
function AdminOverviewDashboard({ onNavigate }) {
  const [summary, setSummary] = useState(EMPTY_SECTION)
  const [totalMembers, setTotalMembers] = useState(EMPTY_SECTION)
  const [activeHosts, setActiveHosts] = useState(EMPTY_SECTION)
  const [reviewFestivals, setReviewFestivals] = useState(EMPTY_SECTION)
  const [organizerApps, setOrganizerApps] = useState(EMPTY_SECTION)
  const [sales, setSales] = useState(EMPTY_SECTION)

  const [refreshing, setRefreshing] = useState(true)
  const [lastUpdated, setLastUpdated] = useState(null)

  function loadAll(signal) {
    setRefreshing(true)
    setSummary((s) => ({ ...s, loading: true, error: false }))
    setTotalMembers((s) => ({ ...s, loading: true, error: false }))
    setActiveHosts((s) => ({ ...s, loading: true, error: false }))
    setReviewFestivals((s) => ({ ...s, loading: true, error: false }))
    setOrganizerApps((s) => ({ ...s, loading: true, error: false }))
    setSales((s) => ({ ...s, loading: true, error: false }))

    //각 항목은 signal이 이미 중단됐으면(언마운트 등) state를 건드리지 않는다.
    function guard(signal, setState) {
      return {
        ok: (data) => { if (!signal?.aborted) setState({ loading: false, error: false, data }) },
        fail: () => { if (!signal?.aborted) setState({ loading: false, error: true, data: null }) },
      }
    }

    const summaryGuard = guard(signal, setSummary)
    const membersGuard = guard(signal, setTotalMembers)
    const hostsGuard = guard(signal, setActiveHosts)
    const reviewGuard = guard(signal, setReviewFestivals)
    const appsGuard = guard(signal, setOrganizerApps)
    const salesGuard = guard(signal, setSales)

    const tasks = [
      fetchAdminSummary(signal)
        .then((res) => summaryGuard.ok(res.data.data))
        .catch(summaryGuard.fail),

      fetchAdminUsers({ size: 1 }, signal)
        .then((res) => membersGuard.ok(res.data.meta.pagination.totalItems))
        .catch(membersGuard.fail),

      fetchAdminHosts({ status: 'ACTIVE', size: 1 }, signal)
        .then((res) => hostsGuard.ok(res.data.meta.pagination.totalItems))
        .catch(hostsGuard.fail),

      fetchFestivalSubmissionsPage({ status: 'PENDING', size: 5, sort: 'createdAt,asc' }, signal)
        .then(({ items }) => reviewGuard.ok(items))
        .catch(reviewGuard.fail),

      fetchOrganizerApplications()
        .then((items) => appsGuard.ok(items.slice(0, 5)))
        .catch(appsGuard.fail),

      fetchFestivalOperationsPage({ operationStatus: 'ONGOING', size: SALES_CARD_COUNT }, signal)
        .then(async ({ items }) => {
          if (items.length >= SALES_CARD_COUNT) return items
          const need = SALES_CARD_COUNT - items.length
          const filled = await fetchFestivalOperationsPage({ operationStatus: 'SCHEDULED', sort: 'startAt,asc', size: need }, signal)
          return items.concat(filled.items)
        })
        .then(salesGuard.ok)
        .catch(salesGuard.fail),
    ]

    Promise.allSettled(tasks).then(() => {
      if (signal?.aborted) return
      setLastUpdated(new Date())
      setRefreshing(false)
    })
  }

  useEffect(() => {
    const controller = new AbortController()
    loadAll(controller.signal)
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleRefresh() {
    if (refreshing) return
    loadAll()
  }

  const summaryData = summary.data
  function summaryValue(key) {
    return summaryData ? summaryData[key] : 0
  }

  return (
    <div>
      {/* 상단 바 */}
      <div className="flex items-center justify-end gap-2">
        {lastUpdated && <span className="text-xs text-gray-400">마지막 갱신 {formatClock(lastUpdated)}</span>}
        <button
          type="button"
          onClick={handleRefresh}
          disabled={refreshing}
          aria-label="새로고침"
          className="rounded-full p-1.5 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <RotateCw className={'h-4 w-4' + (refreshing ? ' animate-spin' : '')} />
        </button>
      </div>

      {/* 운영 현황 */}
      <div className="mt-8">
        <SectionTitle>운영 현황</SectionTitle>
        <div className="mt-3 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            icon={Users}
            iconCls="bg-blue-50 text-blue-600"
            label="총 회원"
            loading={totalMembers.loading}
            error={totalMembers.error}
            value={totalMembers.data}
            onClick={() => onNavigate({ tab: 'member' })}
          />
          <StatCard
            icon={Megaphone}
            iconCls="bg-teal-50 text-teal-600"
            label="활동 주최자"
            loading={activeHosts.loading}
            error={activeHosts.error}
            value={activeHosts.data}
            onClick={() => onNavigate({ tab: 'organizer', organizerSub: 'list', organizerAccountFilter: 'ACTIVE' })}
          />
          <StatCard
            icon={CalendarCheck}
            iconCls="bg-emerald-50 text-emerald-600"
            label="진행 중 페스티벌"
            loading={summary.loading}
            error={summary.error}
            value={summaryValue('ongoingFestivalCount')}
            onClick={() => onNavigate({ tab: 'festival', festivalSub: 'operations', operationsFilter: 'ONGOING' })}
          />
          <StatCard
            icon={CalendarClock}
            iconCls="bg-purple-50 text-purple-600"
            label="예정 페스티벌"
            loading={summary.loading}
            error={summary.error}
            value={summaryValue('scheduledFestivalCount')}
            onClick={() => onNavigate({ tab: 'festival', festivalSub: 'operations', operationsFilter: 'SCHEDULED' })}
          />
        </div>
      </div>

      {/* 처리 대기 */}
      <div className="mt-8">
        <SectionTitle>처리 대기</SectionTitle>
        <div className="mt-3 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <PendingStatCard
            icon={UserPlus}
            label="주최자 신청 대기"
            loading={summary.loading}
            error={summary.error}
            value={summaryValue('hostApplicationPendingCount')}
            onClick={() => onNavigate({ tab: 'organizer', organizerSub: 'organizer' })}
          />
          <PendingStatCard
            icon={ClipboardCheck}
            label="등록 심사 대기"
            loading={summary.loading}
            error={summary.error}
            value={summaryValue('festivalReviewPendingCount')}
            onClick={() => onNavigate({ tab: 'festival', festivalSub: 'festival' })}
          />
          <PendingStatCard
            icon={XCircle}
            label="취소 요청 대기"
            loading={summary.loading}
            error={summary.error}
            value={summaryValue('cancellationPendingCount')}
            onClick={() => onNavigate({ tab: 'festival', festivalSub: 'cancellation', cancellationFilter: 'PENDING' })}
          />
          <PendingStatCard
            icon={RotateCcw}
            label="환불 진행 중"
            monitorOnly
            loading={summary.loading}
            error={summary.error}
            value={summaryValue('refundingCount')}
            onClick={() => onNavigate({ tab: 'festival', festivalSub: 'cancellation', cancellationFilter: 'REFUNDING' })}
          />
        </div>
      </div>

      {/* 2단 목록 */}
      <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div>
          <div className="flex items-center justify-between">
            <SectionTitle>심사 대기 페스티벌</SectionTitle>
            <SectionLink onClick={() => onNavigate({ tab: 'festival', festivalSub: 'festival' })}>심사하기 →</SectionLink>
          </div>
          <div className="mt-3">
            <ListPanel
              loading={reviewFestivals.loading}
              error={reviewFestivals.error}
              isEmpty={reviewFestivals.data?.length === 0}
              emptyMessage="심사 대기 중인 페스티벌이 없어요"
            >
              {reviewFestivals.data?.map((f) => {
                const days = daysWaiting(f.appliedAt)
                const waitingLabel = days === 0 ? '오늘 신청' : days != null ? `${days}일째 대기` : ''
                const waitingCls = days >= WAITING_HIGHLIGHT_DAYS ? 'text-xs font-bold text-amber-700' : 'text-xs text-gray-500'
                return (
                  <ListRow
                    key={f.id}
                    onClick={() => onNavigate({ tab: 'festival', festivalSub: 'festival' })}
                    title={f.name}
                    subtitle={`${f.host} · 신청일 ${f.appliedAt}`}
                    right={<span className={waitingCls}>{waitingLabel}</span>}
                  />
                )
              })}
            </ListPanel>
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between">
            <SectionTitle>주최자 신청 현황</SectionTitle>
            <SectionLink onClick={() => onNavigate({ tab: 'organizer', organizerSub: 'organizer' })}>심사하기 →</SectionLink>
          </div>
          <div className="mt-3">
            <ListPanel
              loading={organizerApps.loading}
              error={organizerApps.error}
              isEmpty={organizerApps.data?.length === 0}
              emptyMessage="주최자 신청이 없어요"
            >
              {organizerApps.data?.map((a) => (
                <ListRow
                  key={a.id}
                  onClick={() => onNavigate({ tab: 'organizer', organizerSub: 'organizer' })}
                  title={a.name}
                  subtitle={`${a.email} · 신청일 ${a.appliedAt}`}
                  right={<StatusBadge status={a.status} />}
                />
              ))}
            </ListPanel>
          </div>
        </div>
      </div>

      {/* 판매 현황 */}
      <div className="mt-8">
        <div className="flex items-center justify-between">
          <SectionTitle>판매 현황</SectionTitle>
          <SectionLink onClick={() => onNavigate({ tab: 'festival', festivalSub: 'operations' })}>전체보기 →</SectionLink>
        </div>
        <p className="mt-1 text-xs text-gray-400">진행 중인 페스티벌을 먼저, 부족하면 곧 시작하는 페스티벌을 보여줘요.</p>

        <div className="mt-3">
          {sales.loading && <p className="py-12 text-center text-sm text-gray-400">불러오는 중…</p>}
          {!sales.loading && sales.error && <p className="py-12 text-center text-sm text-gray-400">불러오지 못했어요. 새로고침해주세요.</p>}
          {!sales.loading && !sales.error && sales.data?.length === 0 && (
            <p className="py-12 text-center text-sm text-gray-400">진행 중이거나 예정된 페스티벌이 없어요</p>
          )}
          {!sales.loading && !sales.error && sales.data?.length > 0 && (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {sales.data.map((item) => {
                const opMeta = OPERATION_STATUS_META[item.operationStatus] ?? { label: item.operationStatus, cls: 'bg-gray-100 text-gray-600' }
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onNavigate({ tab: 'festival', festivalSub: 'operations', operationsFilter: item.operationStatus })}
                    className="rounded-2xl border border-gray-200 bg-white p-5 text-left shadow-sm transition hover:border-gray-300"
                  >
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className={'rounded-full px-2.5 py-1 text-xs font-bold ' + opMeta.cls}>{opMeta.label}</span>
                      <HighlightBadges item={item} />
                    </div>
                    <p className="mt-3 line-clamp-2 text-sm font-bold text-gray-900">{item.name}</p>
                    <p className="mt-1 text-xs text-gray-500">{item.dateRange}</p>
                    <div className="mt-3">
                      <SalesProgress item={item} showBadges={false} />
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default AdminOverviewDashboard
