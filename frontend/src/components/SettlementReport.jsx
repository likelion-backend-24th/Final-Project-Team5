import { useEffect, useRef, useState } from 'react'
import { ArrowUpRight, CheckCircle2, Clock3, RefreshCw, Search, Wallet } from 'lucide-react'
import { listSettlements, settlementSummary, settlementDetail, settlementCommand } from '../api/settlementApi'
import SettlementDetail from './SettlementDetail'
import {
  formatMoney,
  SETTLEMENT_STATES,
  PRIMARY_BUTTON,
  SECONDARY_BUTTON,
  INPUT_CLASS,
} from './settlementPresentation'

const LIST_ERROR_MESSAGE = '정산 내역을 불러오지 못했습니다. 다시 시도해 주세요.'
const DETAIL_ERROR_MESSAGE = '정산 상세를 불러오지 못했습니다. 다시 시도해 주세요.'

const initialFilters = { festivalName: '', hostName: '', status: '', from: '', to: '' }
export default function SettlementReport({ host = false }) {
  const [draft, setDraft] = useState(initialFilters)
  const [filters, setFilters] = useState(initialFilters)
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [rows, setRows] = useState([])
  const [summary, setSummary] = useState(null)
  const [pagination, setPagination] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [detail, setDetail] = useState(null)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const commandRef = useRef(null)
  useEffect(() => {
    const controller = new AbortController()
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
    if (params.from) params.from = new Date(`${params.from}T00:00:00+09:00`).toISOString()
    if (params.to) {
      const end = new Date(`${params.to}T00:00:00+09:00`)
      end.setUTCDate(end.getUTCDate() + 1)
      params.to = end.toISOString()
    }
    if (host) delete params.hostName
    setLoading(true)
    setError('')
    Promise.all([
      listSettlements(host, { ...params, page, size: 20 }, controller.signal),
      settlementSummary(host, params, controller.signal),
    ])
      .then(([list, total]) => {
        if (!controller.signal.aborted) {
          setRows(list.data.data)
          setPagination(list.data.meta.pagination)
          setSummary(total.data.data)
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(LIST_ERROR_MESSAGE)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [filters, page, refresh, host])
  async function open(id) {
    if (busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setDetailError('')
    setError('')
    try {
      setDetail((await settlementDetail(host, id)).data.data)
    } catch {
      setError(DETAIL_ERROR_MESSAGE)
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }
  async function execute(action, body) {
    if (busyRef.current) return false
    busyRef.current = true
    setBusy(true)
    setDetailError('')
    const identity = JSON.stringify([detail.id, action, body])
    if (commandRef.current?.identity !== identity) commandRef.current = { identity, key: crypto.randomUUID() }
    try {
      setDetail((await settlementCommand(detail.id, action, body, commandRef.current.key)).data.data)
      commandRef.current = null
      setRefresh((v) => v + 1)
      return true
    } catch (e) {
      setDetailError(e.response?.data?.message || '처리에 실패했습니다. 최신 상태를 확인해 주세요.')
      return false
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }
  function search(e) {
    e.preventDefault()
    setFilters({ ...draft })
    setPage(0)
  }
  function status(value) {
    setDraft((old) => ({ ...old, status: value }))
    setFilters((old) => ({ ...old, status: value }))
    setPage(0)
  }
  function reset() {
    setDraft(initialFilters)
    setFilters(initialFilters)
    setPage(0)
  }
  const metrics = [
    {
      label: host ? '받을 예정인 금액' : '지급 예정액',
      value: formatMoney(summary?.scheduledAmount),
      hint: '검토 대기와 지급 대기 정산',
      Icon: Wallet,
      style: 'text-blue-600 bg-blue-50',
    },
    {
      label: host ? '지급받은 금액' : '지급 완료액',
      value: formatMoney(summary?.paidAmount),
      hint: '지급 완료로 기록한 금액',
      Icon: CheckCircle2,
      style: 'text-emerald-600 bg-emerald-50',
    },
    {
      label: host ? '확인 중인 정산' : '확인할 정산',
      value: `${summary?.reviewCount ?? 0}건`,
      hint: '검토 대기 · 보류 · 환불 조정',
      Icon: Clock3,
      style: 'text-amber-600 bg-amber-50',
    },
  ]
  return (
    <div className="space-y-5 text-gray-900">
      <p className="text-sm text-gray-500">
        {host
          ? '행사가 끝나면 정산을 준비해요. 받을 금액과 진행 상황을 확인하세요.'
          : '행사별 금액을 확인하고, 검토가 끝난 정산부터 지급을 준비하세요.'}
      </p>
      <SummaryCards
        metrics={metrics}
        loading={loading}
      />
      <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white">
        <SettlementFilters
          host={host}
          draft={draft}
          setDraft={setDraft}
          filters={filters}
          search={search}
          status={status}
          reset={reset}
          setRefresh={setRefresh}
        />
        {error && (
          <div
            role="alert"
            className="m-5 rounded-xl bg-red-50 p-4 text-sm text-red-700"
          >
            {error}
            <button
              type="button"
              className="ml-3 font-bold underline"
              onClick={() => setRefresh((v) => v + 1)}
            >
              다시 시도
            </button>
          </div>
        )}
        <SettlementTable
          loading={loading}
          error={error}
          rows={rows}
          host={host}
          busy={busy}
          open={open}
        />
        <SettlementPagination
          pagination={pagination}
          loading={loading}
          page={page}
          setPage={setPage}
        />
      </section>
      <StatusGuide />
      {detail && (
        <SettlementDetail
          key={detail.id}
          detail={detail}
          host={host}
          busy={busy}
          error={detailError}
          onClose={() => {
            if (!busyRef.current) setDetail(null)
          }}
          onCommand={execute}
        />
      )}
    </div>
  )
}

function SummaryCards({ metrics, loading }) {
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      {metrics.map(({ label, value, hint, Icon, style }) => (
        <div
          className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm"
          key={label}
        >
          <div className="flex items-center gap-2 text-sm font-medium text-gray-500">
            <span className={`rounded-lg p-2 ${style}`}>
              <Icon
                size={17}
                aria-hidden="true"
              />
            </span>
            {label}
          </div>
          <p className="mt-3 text-2xl font-extrabold tracking-tight text-brand-navy">
            {loading ? '—' : value}
          </p>
          <p className="mt-1 text-xs text-gray-400">{hint}</p>
        </div>
      ))}
    </div>
  )
}

function SettlementFilters({ host, draft, setDraft, filters, search, status, reset, setRefresh }) {
  return (
    <form
      onSubmit={search}
      className="space-y-3 border-b border-gray-100 p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-end gap-3">
        <label className="min-w-40 flex-1 space-y-1.5 text-xs font-semibold text-gray-500">
          페스티벌 이름
          <input
            className={INPUT_CLASS}
            maxLength={100}
            value={draft.festivalName}
            onChange={(e) => setDraft((old) => ({ ...old, festivalName: e.target.value }))}
            placeholder="페스티벌 이름을 입력하세요"
          />
        </label>
        {!host && (
          <label className="min-w-36 flex-1 space-y-1.5 text-xs font-semibold text-gray-500">
            주최자 이름
            <input
              className={INPUT_CLASS}
              maxLength={100}
              value={draft.hostName}
              onChange={(e) => setDraft((old) => ({ ...old, hostName: e.target.value }))}
              placeholder="주최자 이름을 입력하세요"
            />
          </label>
        )}
        <label className="min-w-32 space-y-1.5 text-xs font-semibold text-gray-500">
          정산 상태
          <select
            className={INPUT_CLASS}
            value={draft.status}
            onChange={(e) => status(e.target.value)}
          >
            <option value="">전체 상태</option>
            {Object.entries(SETTLEMENT_STATES).map(([key, state]) => (
              <option
                key={key}
                value={key}
              >
                {state.label}
              </option>
            ))}
          </select>
        </label>
        <button
          className={PRIMARY_BUTTON}
          type="submit"
        >
          <Search
            size={16}
            aria-hidden="true"
          />
          검색
        </button>
        <button
          aria-label="새로고침"
          title="새로고침"
          className={SECONDARY_BUTTON}
          type="button"
          onClick={() => setRefresh((v) => v + 1)}
        >
          <RefreshCw
            size={16}
            aria-hidden="true"
          />
        </button>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-gray-500">
        <details>
          <summary className="cursor-pointer py-1 font-medium">기간으로 좁히기</summary>
          <div className="mt-2 flex flex-wrap items-end gap-3">
            <label className="space-y-1">
              시작일
              <input
                className={INPUT_CLASS}
                type="date"
                value={draft.from}
                onChange={(e) => setDraft((old) => ({ ...old, from: e.target.value }))}
              />
            </label>
            <label className="space-y-1">
              종료일
              <input
                className={INPUT_CLASS}
                type="date"
                min={draft.from || undefined}
                value={draft.to}
                onChange={(e) => setDraft((old) => ({ ...old, to: e.target.value }))}
              />
            </label>
            <p className="max-w-48 pb-2 leading-relaxed">
              정산 계산일 기준이에요. 종료일도 포함하며 검색을 누르면 적용돼요.
            </p>
          </div>
        </details>
        {Object.values(filters).some(Boolean) && (
          <button
            type="button"
            onClick={reset}
            className="underline underline-offset-2"
          >
            검색 초기화
          </button>
        )}
      </div>
    </form>
  )
}

function SettlementTable({ loading, error, rows, host, busy, open }) {
  return loading ? (
    <p
      role="status"
      className="p-12 text-center text-sm text-gray-500"
    >
      정산 내역을 불러오는 중…
    </p>
  ) : (
    !error &&
      (rows.length === 0 ? (
        <div className="p-12 text-center">
          <Wallet
            size={32}
            className="mx-auto mb-3 text-gray-300"
            aria-hidden="true"
          />
          <p className="font-semibold">조회 조건에 해당하는 정산이 없습니다.</p>
          <p className="mt-2 text-sm text-gray-500">
            검색 조건을 바꾸거나, 행사 종료 후 24시간이 지났는지 확인해 주세요.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <caption className="sr-only">페스티벌별 정산</caption>
            <thead className="bg-gray-50 text-left text-xs text-gray-500">
              <tr>
                <th
                  scope="col"
                  className="px-5 py-3 font-medium"
                >
                  페스티벌
                </th>
                <th
                  scope="col"
                  className="px-3 py-3 font-medium"
                >
                  진행 상태
                </th>
                <th
                  scope="col"
                  className="px-3 py-3 text-right font-medium"
                >
                  지급액
                </th>
                <th
                  scope="col"
                  className="px-3 py-3"
                >
                  <span className="sr-only">상세</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((row) => {
                const state = SETTLEMENT_STATES[row.status]
                const unknown = row.status === 'PENDING' || (row.status === 'HELD' && !row.calculatedAt)
                const value = row.paidAt
                  ? (row.paidPayoutAmount ?? row.payoutAmount)
                  : (row.payableAmount ?? row.payoutAmount)
                return (
                  <tr
                    key={row.id}
                    className="transition hover:bg-blue-50/30"
                  >
                    <td className="px-5 py-4">
                      <p className="min-w-36 font-bold text-gray-800">{row.festivalName}</p>
                      {!host && (
                        <p className="mt-1 text-xs text-gray-500">{row.hostName || '주최자 이름 확인 중'}</p>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-3 py-4">
                      <span
                        title={state.description}
                        className={`inline-block rounded-full px-2.5 py-1 text-xs font-bold ${state.style}`}
                      >
                        {state.label}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-3 py-4 text-right">
                      <strong className="tabular-nums text-brand-navy">
                        {unknown ? '확인 중' : formatMoney(value)}
                      </strong>
                      {row.status === 'ADJUSTMENT_REQUIRED' && !row.paidAt && (
                        <p className="mt-1 text-xs text-gray-400">재승인 전 금액</p>
                      )}
                      {row.paidAt && <p className="mt-1 text-xs text-gray-400">지급한 금액</p>}
                    </td>
                    <td className="px-3 py-4">
                      <button
                        className="rounded-xl p-2 text-blue-600 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-600 disabled:opacity-40"
                        disabled={busy}
                        onClick={() => open(row.id)}
                        aria-label={`${row.festivalName} 상세`}
                      >
                        <ArrowUpRight
                          size={18}
                          aria-hidden="true"
                        />
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ))
  )
}

function SettlementPagination({ pagination, loading, page, setPage }) {
  return (
    <nav
      aria-label="정산 페이지"
      className="flex items-center justify-between border-t border-gray-100 px-5 py-3 text-xs text-gray-500"
    >
      <span>총 {pagination?.totalItems ?? 0}건</span>
      <div className="flex items-center gap-3">
        <button
          disabled={loading || !pagination?.hasPrev}
          onClick={() => setPage((p) => p - 1)}
          className="rounded-lg px-2 py-1.5 hover:bg-gray-50 disabled:opacity-30"
        >
          이전
        </button>
        <span>
          {page + 1} / {Math.max(1, pagination?.totalPages || 0)}
        </span>
        <button
          disabled={loading || !pagination?.hasNext}
          onClick={() => setPage((p) => p + 1)}
          className="rounded-lg px-2 py-1.5 hover:bg-gray-50 disabled:opacity-30"
        >
          다음
        </button>
      </div>
    </nav>
  )
}

function StatusGuide() {
  return (
    <details className="rounded-xl text-sm text-gray-500">
      <summary className="cursor-pointer font-medium">정산 상태가 궁금하신가요?</summary>
      <dl className="mt-4 grid gap-4 sm:grid-cols-2">
        {Object.values(SETTLEMENT_STATES).map((state) => (
          <div
            key={state.label}
            className="rounded-xl bg-white p-4"
          >
            <dt className="font-bold text-gray-700">{state.label}</dt>
            <dd className="mt-1 text-xs leading-relaxed">{state.description}</dd>
          </div>
        ))}
      </dl>
    </details>
  )
}
