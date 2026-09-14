import { useLayoutEffect, useRef, useState } from 'react'
import { ArrowRight, Check, Info, X } from 'lucide-react'
import {
  formatMoney,
  formatDate,
  SETTLEMENT_STATES,
  PRIMARY_BUTTON,
  SECONDARY_BUTTON,
  INPUT_CLASS,
} from './settlementPresentation'

const ACTION_LABELS = {
  confirm: '금액 확정',
  reapprove: '변경 금액 승인',
  'mark-paid': '지급 완료 기록',
  hold: '정산 보류',
  release: '보류 해제',
  recalculate: '다시 계산',
}
const ACTION_EXPLANATIONS = {
  confirm: '이 금액을 확정하면 일반 재계산이 중단돼요. 환불이 발생하면 별도 조정으로 처리해요.',
  reapprove: '환불을 반영한 아래 금액을 새 지급액으로 승인해요. 최초 확정 내역은 그대로 보존해요.',
  'mark-paid': '외부 송금을 마쳤을 때만 기록해 주세요. 이 버튼으로 은행 이체가 실행되지는 않아요.',
  hold: '지급을 멈추고 확인할 사항을 기록해요. 직접 보류를 해제할 때까지 자동으로 풀리지 않아요.',
  release: '보류를 해제하고 최신 결제·환불 내역으로 다시 확인해요. 문제가 남아 있으면 보류가 유지돼요.',
  recalculate: '최신 결제·환불 내역으로 금액을 다시 계산해요.',
}
const actionLabel = (action) =>
  ACTION_LABELS[action] || ({ CALCULATE: '자동 계산', ADJUSTMENT: '환불 조정 반영' }[action] ?? '정산 처리')
const METHOD_LABELS = { CARD: '카드', EASY_PAY: '간편결제', VIRTUAL_ACCOUNT: '가상계좌' }
const HOLD_REASON_MESSAGES = {
  MANUAL_REVIEW: '관리자가 확인을 위해 지급을 보류했어요.',
  ORGANIZER_REFUND_PENDING: '행사 취소 승인 또는 주최자 귀책 환불이 진행 중이에요.',
  UNKNOWN_METHOD: '결제수단을 확인하지 못해 수수료를 계산할 수 없어요.',
  CANCELLATION_PENDING: '처리 중인 환불이 완료될 때까지 기다리고 있어요.',
  UNKNOWN_REFUND_QUANTITY: '외부에서 취소된 티켓 수량을 확인해야 해요.',
  UNKNOWN_EXTERNAL_CANCELLATION: '결제사에서 발견한 취소 내역을 확인해야 해요.',
  RECONCILIATION_MISMATCH: '결제사 환불액과 예약 환불 수량을 다시 확인해야 해요.',
  RESERVATION_MISMATCH: '예약 수량 또는 티켓 금액을 확인해야 해요.',
  REFUND_MISMATCH: '환불 금액과 취소 티켓 액면가를 확인해야 해요.',
  PAYMENT_RESERVATION_MISMATCH: '결제사와 예약의 금액 또는 상태가 일치하지 않아요.',
  MISSING_PAYMENT: '예약에 대응하는 결제 내역을 확인해야 해요.',
  MISSING_REFUND_SNAPSHOT: '환불 계산에 필요한 이전 내역이 부족해요.',
}

export default function SettlementDetail({ detail, host, busy, error, onClose, onCommand }) {
  const dialog = useRef(null)
  const [action, setAction] = useState('')
  const [memo, setMemo] = useState('')
  const [reference, setReference] = useState('')
  const [paidAt, setPaidAt] = useState('')
  // 내용이 화면에 노출되기 전에 모달을 열어 포커스와 접근성 트리가 같은 상태를 보게 한다.
  useLayoutEffect(() => {
    const element = dialog.current
    const previous = document.activeElement
    if (element.showModal) element.showModal()
    else element.setAttribute('open', '')
    return () => {
      element.close?.()
      previous?.focus?.()
    }
  }, [])
  const status = SETTLEMENT_STATES[detail.status]
  const paid = Boolean(detail.paidAt)
  const proposed = detail.status === 'ADJUSTMENT_REQUIRED' && !paid
  const unsettled = detail.status === 'PENDING' || (detail.status === 'HELD' && !detail.lines?.length)
  const value = paid
    ? (detail.paidPayoutAmount ?? detail.payoutAmount)
    : proposed
      ? detail.proposedPayoutAmount
      : (detail.payableAmount ?? detail.payoutAmount)
  const correction = proposed
    ? detail.proposedPayoutAmount - detail.payoutAmount
    : (detail.confirmedAdjustmentAmount ?? 0)
  const debt = (detail.adjustments ?? []).reduce((sum, a) => sum + Math.min(0, a.remainingAmount), 0)
  const methods = Object.values(
    (detail.lines ?? []).reduce((groups, line) => {
      const group = (groups[line.paymentMethod] ??= { method: line.paymentMethod, gross: 0, fee: 0 })
      group.gross += line.grossAmount
      group.fee += line.finalFeeAmount
      return groups
    }, {}),
  )
  const mainAction =
    { CALCULATED: 'confirm', CONFIRMED: 'mark-paid' }[detail.status] ||
    (proposed ? 'reapprove' : detail.status === 'HELD' && detail.manualHold ? 'release' : null)
  const canRecalculate = ['CALCULATED', 'HELD'].includes(detail.status) && !detail.manualHold
  const begin = (next) => {
    setAction(next)
    setMemo('')
    setReference('')
    setPaidAt('')
  }
  async function submit(e) {
    e.preventDefault()
    const success = await onCommand(action, {
      memo,
      paymentReference: reference || null,
      paidAt: paidAt ? new Date(paidAt).toISOString() : null,
    })
    if (success) setAction('')
  }
  return (
    <dialog
      ref={dialog}
      aria-labelledby="settlement-detail-title"
      onCancel={(e) => {
        e.preventDefault()
        if (!busy) onClose()
      }}
      className="fixed inset-0 m-auto max-h-[92dvh] w-[calc(100%_-_2rem)] max-w-2xl overflow-y-auto rounded-3xl border-0 bg-white p-0 text-gray-900 shadow-2xl backdrop:bg-slate-900/45"
    >
      <header className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-gray-100 bg-white px-6 py-5">
        <div>
          <p className="mb-1 text-xs font-bold tracking-wide text-blue-600">정산 상세</p>
          <h2
            id="settlement-detail-title"
            className="text-lg font-extrabold tracking-tight"
          >
            {detail.festivalName}
          </h2>
          <p className="mt-1 text-sm text-gray-500">{detail.hostName || '주최자 이름 확인 중'}</p>
        </div>
        <button
          aria-label="상세 닫기"
          disabled={busy}
          onClick={onClose}
          className="rounded-full p-2 text-gray-500 hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-blue-600"
        >
          <X size={20} />
        </button>
      </header>
      <div className="space-y-6 p-6">
        <PayoutSummary
          paid={paid}
          proposed={proposed}
          status={status}
          unsettled={unsettled}
          value={value}
        />
        <HoldNotice
          host={host}
          detail={detail}
          paid={paid}
          debt={debt}
          proposed={proposed}
          correction={correction}
        />
        <PayoutBreakdown
          unsettled={unsettled}
          paid={paid}
          detail={detail}
          correction={correction}
          value={value}
        />
        <ProgressSteps detail={detail} />
        {error && (
          <p
            role="alert"
            className="rounded-xl bg-red-50 p-3 text-sm text-red-700"
          >
            {error}
          </p>
        )}
        <ActionPanel
          host={host}
          action={action}
          mainAction={mainAction}
          busy={busy}
          begin={begin}
          canRecalculate={canRecalculate}
          detail={detail}
          submit={submit}
          value={value}
          reference={reference}
          setReference={setReference}
          paidAt={paidAt}
          setPaidAt={setPaidAt}
          memo={memo}
          setMemo={setMemo}
          setAction={setAction}
        />
        <div className="divide-y divide-gray-100 border-t border-gray-100">
          <FeeBreakdown
            detail={detail}
            methods={methods}
          />
          <AdjustmentList detail={detail} />
          <AuditTrail
            host={host}
            detail={detail}
          />
        </div>
      </div>
    </dialog>
  )
}

function MoneyRow({ label, amount, strong }) {
  return (
    <div
      className={`flex items-center justify-between gap-3 py-3 ${strong ? 'font-extrabold text-blue-700' : 'text-gray-600'}`}
    >
      <dt>{label}</dt>
      <dd className="shrink-0 tabular-nums">{formatMoney(amount)}</dd>
    </div>
  )
}

function PayoutSummary({ paid, proposed, status, unsettled, value }) {
  return (
    <>
      <section className="rounded-2xl bg-blue-50/80 p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-semibold text-gray-600">
            {paid ? '지급한 금액' : proposed ? '환불 반영 후 지급할 금액' : '주최자에게 지급할 금액'}
          </p>
          <span className={`rounded-full px-3 py-1 text-xs font-bold ${status.style}`}>{status.label}</span>
        </div>
        <p className="mt-3 text-3xl font-extrabold tracking-tight text-brand-navy">
          {unsettled ? '금액 확인 중' : formatMoney(value)}
        </p>
        <p className="mt-3 text-sm leading-relaxed text-gray-600">{status.description}</p>
      </section>
    </>
  )
}

function HoldNotice({ host, detail, paid, debt, proposed, correction }) {
  return (
    <>
      {!host && detail.status === 'HELD' && (
        <p className="rounded-xl bg-amber-50 p-4 text-sm leading-relaxed text-amber-900">
          {HOLD_REASON_MESSAGES[detail.holdReason] ||
            '결제·환불 자료가 완전한지 확인한 뒤 다시 계산해 주세요.'}
          {detail.manualHold && ' 확인 후 보류 해제를 눌러 주세요.'}
        </p>
      )}
      {paid && debt < 0 && (
        <section className="rounded-2xl border border-orange-100 bg-orange-50 p-4">
          <h3 className="font-bold text-orange-900">다음 정산에서 차감할 금액 {formatMoney(-debt)}</h3>
          <p className="mt-1 text-sm leading-relaxed text-orange-800">
            {host
              ? '이미 지급받은 금액 중 환불로 반환해야 하는 금액이에요.'
              : '지급 후 환불된 금액 중 아직 돌려받지 못한 금액이에요.'}{' '}
            다음 정산금에서 차감하며, 지급한 기록은 바뀌지 않아요.
          </p>
        </section>
      )}
      {proposed && (
        <p className="flex gap-2 text-sm leading-relaxed text-orange-800">
          <Info
            size={18}
            className="mt-0.5 shrink-0"
          />
          최초 확정액 {formatMoney(detail.payoutAmount)}에서 환불 조정 {formatMoney(correction)}을 반영했어요.{' '}
          {host ? '관리자의 재승인을 기다리고 있어요.' : '변경된 금액을 승인한 뒤 지급해 주세요.'}
        </p>
      )}
    </>
  )
}

function PayoutBreakdown({ unsettled, paid, detail, correction, value }) {
  return (
    <>
      {!unsettled && (
        <section>
          <h3 className="mb-4 font-bold">이 금액은 어떻게 계산됐나요?</h3>
          {paid && (
            <p className="mb-3 text-xs text-gray-500">
              아래 내역은 지급 당시 기준이에요. 이후 환불은 별도 조정에 표시해요.
            </p>
          )}
          <dl className="divide-y divide-gray-100 text-sm">
            <MoneyRow
              label="티켓 결제액"
              amount={detail.grossPaymentAmount}
            />
            <MoneyRow
              label="구매자에게 돌려준 금액"
              amount={-detail.customerRefundAmount}
            />
            <MoneyRow
              label="플랫폼 수수료"
              amount={-detail.platformFeeAmount}
            />
            {detail.adjustmentAmount !== 0 && (
              <MoneyRow
                label="이전 정산에서 반영한 금액"
                amount={detail.adjustmentAmount}
              />
            )}
            {correction !== 0 && (
              <MoneyRow
                label="지급 전 환불 조정"
                amount={correction}
              />
            )}
            <MoneyRow
              label={paid ? '지급한 금액' : '최종 지급액'}
              amount={value}
              strong
            />
          </dl>
          {detail.cancellationPenaltyAmount > 0 && (
            <p className="mt-3 rounded-xl bg-gray-50 p-3 text-xs leading-relaxed text-gray-600">
              취소 위약금 {formatMoney(detail.cancellationPenaltyAmount)}은 주최자에게 돌아가며, 위 지급액에
              이미 포함되어 있어요.
            </p>
          )}
        </section>
      )}
    </>
  )
}

function ProgressSteps({ detail }) {
  return (
    <>
      <ol
        aria-label="정산 진행 단계"
        className="grid grid-cols-3 gap-2 rounded-2xl border border-gray-100 p-4 text-xs"
      >
        {[
          ['계산', detail.calculatedAt],
          ['확정', detail.reapprovedAt || detail.confirmedAt],
          ['지급', detail.paidAt],
        ].map(([label, at]) => (
          <li key={label}>
            <span
              className={`mb-2 flex h-6 w-6 items-center justify-center rounded-full ${at ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-400'}`}
            >
              {at ? <Check size={14} /> : '·'}
            </span>
            <strong>{label}</strong>
            <p className="mt-1 leading-relaxed text-gray-500">{at ? formatDate(at) : '대기 중'}</p>
          </li>
        ))}
      </ol>
      {detail.status === 'PENDING' && (
        <p className="text-sm text-gray-600">정산 가능 시각: {formatDate(detail.eligibleAt)}</p>
      )}
    </>
  )
}

function ActionPanel({
  host,
  action,
  mainAction,
  busy,
  begin,
  canRecalculate,
  detail,
  submit,
  value,
  reference,
  setReference,
  paidAt,
  setPaidAt,
  memo,
  setMemo,
  setAction,
}) {
  return (
    <>
      {!host && (
        <>
          {!action && (
            <div className="flex flex-wrap gap-2">
              {mainAction && (
                <button
                  disabled={busy}
                  className={PRIMARY_BUTTON}
                  onClick={() => begin(mainAction)}
                >
                  {ACTION_LABELS[mainAction]}
                  <ArrowRight size={16} />
                </button>
              )}
              {canRecalculate && (
                <button
                  disabled={busy}
                  className={SECONDARY_BUTTON}
                  onClick={() => begin('recalculate')}
                >
                  다시 계산
                </button>
              )}
              {detail.status === 'CALCULATED' && (
                <button
                  disabled={busy}
                  className={SECONDARY_BUTTON}
                  onClick={() => begin('hold')}
                >
                  정산 보류
                </button>
              )}
            </div>
          )}
          {action && (
            <form
              onSubmit={submit}
              className="space-y-4 rounded-2xl border border-blue-200 bg-blue-50/50 p-4"
            >
              <h3 className="font-bold">{ACTION_LABELS[action]}할까요?</h3>
              <p className="text-sm leading-relaxed text-gray-600">{ACTION_EXPLANATIONS[action]}</p>
              {['confirm', 'reapprove', 'mark-paid'].includes(action) && (
                <p className="text-xl font-extrabold text-brand-navy">{formatMoney(value)}</p>
              )}
              {action === 'mark-paid' && (
                <>
                  <label className="block space-y-1 text-sm font-medium">
                    송금 확인 번호
                    <input
                      className={INPUT_CLASS}
                      required
                      maxLength={200}
                      value={reference}
                      onChange={(e) => setReference(e.target.value)}
                      placeholder="은행 거래번호 또는 지급 식별자"
                    />
                  </label>
                  <label className="block space-y-1 text-sm font-medium">
                    실제 지급 시각
                    <input
                      className={INPUT_CLASS}
                      required
                      type="datetime-local"
                      value={paidAt}
                      onChange={(e) => setPaidAt(e.target.value)}
                    />
                  </label>
                </>
              )}
              <label className="block space-y-1 text-sm font-medium">
                관리자 메모 (선택)
                <input
                  className={INPUT_CLASS}
                  maxLength={1000}
                  value={memo}
                  onChange={(e) => setMemo(e.target.value)}
                  placeholder="확인한 내용을 남겨 주세요"
                />
              </label>
              <div className="flex gap-2">
                <button
                  disabled={busy}
                  className={PRIMARY_BUTTON}
                  type="submit"
                >
                  {busy ? '처리 중…' : '확인하고 실행'}
                </button>
                <button
                  disabled={busy}
                  className={SECONDARY_BUTTON}
                  type="button"
                  onClick={() => setAction('')}
                >
                  취소
                </button>
              </div>
            </form>
          )}
        </>
      )}
    </>
  )
}

function FeeBreakdown({ detail, methods }) {
  return (
    <>
      <details className="py-4">
        <summary className="cursor-pointer text-sm font-semibold text-gray-600">
          수수료와 환불 계산 근거
        </summary>
        <p className="my-3 text-xs leading-relaxed text-gray-500">
          카드·간편결제 7.5%, 가상계좌 5% (VAT 포함). 환불한 티켓 금액을 제외하고 결제별로 원 단위 내림해요.
          구매자에게 수수료를 추가 청구하지 않아요.
        </p>
        <p className="mb-3 text-xs text-gray-600">
          환불한 티켓 금액 {formatMoney(detail.grossRefundedFaceAmount)} · 실제 환급{' '}
          {formatMoney(detail.customerRefundAmount)} · 위약금 {formatMoney(detail.cancellationPenaltyAmount)}
        </p>
        <ul
          aria-label="결제수단별 합계"
          className="mb-3 space-y-2"
        >
          {methods.map((group) => (
            <li
              key={group.method}
              className="rounded-lg bg-gray-50 p-3 text-xs text-gray-600"
            >
              <strong>{METHOD_LABELS[group.method] || '확인 중'}</strong> · 결제액 {formatMoney(group.gross)}{' '}
              · 수수료 {formatMoney(group.fee)}
            </li>
          ))}
        </ul>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[440px] text-right text-xs">
            <caption className="sr-only">결제별 수수료 계산 근거</caption>
            <thead className="bg-gray-50 text-gray-500">
              <tr>
                {['결제수단', '결제액', '최초 수수료', '환불로 돌려받은 수수료', '최종 수수료'].map((t) => (
                  <th
                    scope="col"
                    key={t}
                    className="p-2 font-medium"
                  >
                    {t}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {detail.lines?.map((line, i) => (
                <tr
                  key={i}
                  className="border-b border-gray-50"
                >
                  <td className="p-2">{METHOD_LABELS[line.paymentMethod] || '확인 중'}</td>
                  {['grossAmount', 'initialFeeAmount', 'feeReversalAmount', 'finalFeeAmount'].map((k) => (
                    <td
                      key={k}
                      className="p-2 tabular-nums"
                    >
                      {formatMoney(line[k])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </>
  )
}

function AdjustmentList({ detail }) {
  return (
    <>
      {!!detail.adjustments?.length && (
        <details className="py-4">
          <summary className="cursor-pointer text-sm font-semibold text-gray-600">환불 조정 내역</summary>
          {detail.adjustments.map((a, i) => (
            <div
              key={i}
              className="mt-3 flex justify-between gap-4 text-sm"
            >
              <div>
                <p>{a.kind === 'PRE_PAYMENT' ? '지급 전 금액 변경' : '지급 후 정산 조정'}</p>
                <p className="text-xs text-gray-500">{formatDate(a.createdAt)}</p>
              </div>
              <strong>{formatMoney(a.amount)}</strong>
            </div>
          ))}
        </details>
      )}
    </>
  )
}

function AuditTrail({ host, detail }) {
  return (
    <>
      {!host && (
        <details className="py-4">
          <summary className="cursor-pointer text-sm font-semibold text-gray-600">처리 기록</summary>
          <p className="my-3 text-xs text-gray-500">
            담당자와 처리 시각을 남겨 중복 지급이나 잘못된 변경을 확인할 때 사용해요. 주최자에게는 공개되지
            않아요.
          </p>
          <ol className="space-y-3">
            {detail.auditLogs?.map((log) => (
              <li
                key={log.id}
                className="border-l-2 border-blue-100 pl-3 text-sm"
              >
                <p className="font-semibold">{actionLabel(log.action)}</p>
                <p className="mt-1 text-xs text-gray-500">
                  {formatDate(log.createdAt)} · {log.actorUserId ? `담당자 #${log.actorUserId}` : '자동 처리'}
                </p>
                {log.memo && <p className="mt-1 break-words text-gray-600">{log.memo}</p>}
              </li>
            ))}
          </ol>
          {detail.paymentReference && (
            <p className="mt-3 break-words text-xs text-gray-500">
              송금 확인 번호: {detail.paymentReference}
            </p>
          )}
        </details>
      )}
    </>
  )
}
