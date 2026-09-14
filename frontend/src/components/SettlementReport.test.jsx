// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import SettlementReport from './SettlementReport'
import { listSettlements, settlementSummary, settlementDetail, settlementCommand } from '../api/settlementApi'
vi.mock('../api/settlementApi', () => ({ listSettlements: vi.fn(), settlementSummary: vi.fn(), settlementDetail: vi.fn(), settlementCommand: vi.fn() }))
const row = { id: 1, festivalId: 42, festivalName: '검증 행사', status: 'CALCULATED', grossPaymentAmount: 100000,
  grossRefundedFaceAmount: 0, customerRefundAmount: 0, cancellationPenaltyAmount: 0, platformFeeAmount: 7500, adjustmentAmount: 0, payoutAmount: 92500, lines: [], auditLogs: [] }
beforeEach(() => {
  vi.resetAllMocks()
  listSettlements.mockResolvedValue({ data: { data: [], meta: { pagination: { totalPages: 0, hasNext: false, hasPrev: false } } } })
  settlementSummary.mockResolvedValue({ data: { data: row } })
  settlementDetail.mockResolvedValue({ data: { data: row } })
})
afterEach(cleanup)
describe('SettlementReport', () => {
  it('shows loading before receiving a response', () => {
    listSettlements.mockReturnValue(new Promise(() => {}))
    render(<SettlementReport />)
    expect(screen.getByRole('status').textContent).toContain('불러오는 중')
  })
  it('shows empty state and disables pagination', async () => {
    render(<SettlementReport />)
    expect(await screen.findByText('조회 조건에 해당하는 정산이 없습니다.')).toBeTruthy()
    expect(screen.getByRole('button', { name: '다음' }).disabled).toBe(true)
  })
  it('shows a retryable error', async () => {
    listSettlements.mockRejectedValue(new Error('offline'))
    render(<SettlementReport />)
    expect((await screen.findByRole('alert')).textContent).toContain('불러오지 못했습니다')
    expect(screen.getByRole('button', { name: '새로고침' })).toBeTruthy()
  })
  it('never exposes admin commands or audit data to HOST', async () => {
    listSettlements.mockResolvedValue({ data: { data: [row], meta: { pagination: { totalPages: 1 } } } })
    render(<SettlementReport host />)
    fireEvent.click(await screen.findByRole('button', { name: '상세' }))
    await screen.findByText('결제수단별 집계')
    expect(screen.queryByRole('button', { name: '확정' })).toBeNull()
    expect(screen.queryByText('내부 감사 이력')).toBeNull()
    expect(screen.queryByLabelText('주최자 ID')).toBeNull()
    expect(settlementDetail).toHaveBeenCalledWith(true, 1)
  })
  it('requires confirmation and prevents duplicate command clicks', async () => {
    listSettlements.mockResolvedValue({ data: { data: [row], meta: { pagination: { totalPages: 1 } } } })
    settlementCommand.mockReturnValue(new Promise(() => {}))
    render(<SettlementReport />)
    fireEvent.click(await screen.findByRole('button', { name: '상세' }))
    fireEvent.click(await screen.findByRole('button', { name: '확정' }))
    expect(settlementCommand).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '확인하고 실행' }))
    fireEvent.click(screen.getByRole('button', { name: '처리 중…' }))
    expect(settlementCommand).toHaveBeenCalledTimes(1)
  })
  it('offers reapproval only before payout', async () => {
    listSettlements.mockResolvedValue({ data: { data: [row], meta: { pagination: { totalPages: 1 } } } })
    settlementDetail.mockResolvedValue({ data: { data: { ...row, status: 'ADJUSTMENT_REQUIRED', paidAt: null, proposedPayoutAmount: 46250 } } })
    render(<SettlementReport />)
    fireEvent.click(await screen.findByRole('button', { name: '상세' }))
    expect(await screen.findByRole('button', { name: '조정 지급액 재승인' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '지급 완료 기록' })).toBeNull()
  })
})
