import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fetchAdminSummary, fetchAdminUsers, fetchAdminHosts } from '../../api/adminApi'
import { fetchFestivalSubmissionsPage, fetchOrganizerApplications, fetchFestivalOperationsPage } from '../../data/admin'
import AdminOverviewDashboard from './AdminOverviewDashboard'

vi.mock('../../api/adminApi', { spy: true })
vi.mock('../../data/admin', { spy: true })

const DEFAULT_SUMMARY = {
  ongoingFestivalCount: 3,
  scheduledFestivalCount: 2,
  hostApplicationPendingCount: 1,
  festivalReviewPendingCount: 2,
  cancellationPendingCount: 1,
  refundingCount: 4,
}

function operationItem(overrides) {
  return {
    id: 1,
    name: `테스트 페스티벌 ${overrides?.id ?? 1}`,
    host: '주최자',
    category: '음악',
    dateRange: '2026.09.01 – 2026.09.03',
    festivalStatus: 'PUBLISHED',
    operationStatus: 'ONGOING',
    image: '/placeholder.jpg',
    totalQuantity: 100,
    soldQuantity: 50,
    saleRate: 50,
    ...overrides,
  }
}

//appliedAt(data/admin.js의 formatDate 출력 형식 'YYYY.MM.DD')과 맞춰, 오늘로부터 n일 전 날짜 문자열을 만든다.
function daysAgoStr(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}.${m}.${day}`
}

beforeEach(() => {
  vi.clearAllMocks()
  fetchAdminSummary.mockResolvedValue({ data: { data: DEFAULT_SUMMARY } })
  fetchAdminUsers.mockResolvedValue({ data: { meta: { pagination: { totalItems: 120 } } } })
  fetchAdminHosts.mockResolvedValue({ data: { meta: { pagination: { totalItems: 30 } } } })
  fetchFestivalSubmissionsPage.mockResolvedValue({ items: [], pagination: {} })
  fetchOrganizerApplications.mockResolvedValue([])
  fetchFestivalOperationsPage.mockImplementation((params) => {
    if (params.operationStatus === 'ONGOING') {
      return Promise.resolve({
        items: [operationItem({ id: 1 }), operationItem({ id: 2 }), operationItem({ id: 3 }), operationItem({ id: 4 })],
        pagination: {},
      })
    }
    return Promise.resolve({ items: [], pagination: {} })
  })
})

it('요약 숫자를 보여주고, 처리 대기 숫자가 0이면 흐리게 표시한다', async () => {
  fetchAdminSummary.mockResolvedValue({ data: { data: { ...DEFAULT_SUMMARY, hostApplicationPendingCount: 0 } } })
  render(<AdminOverviewDashboard onNavigate={vi.fn()} />)

  const zeroCard = (await screen.findByText('주최자 신청 대기')).closest('button')
  expect(within(zeroCard).getByText('0').className).toContain('text-gray-300')
  expect(within(zeroCard).queryByText('처리 필요')).toBeNull()

  const pendingCard = screen.getByText('취소 요청 대기').closest('button')
  expect(within(pendingCard).getByText('1').className).toContain('text-amber-700')
  expect(within(pendingCard).getByText('처리 필요')).toBeTruthy()

  //환불 진행 중은 처리할 일이 아니라 모니터링 대상이라 amber 대신 blue, "진행 중"으로 표시한다.
  const refundingCard = screen.getByText('환불 진행 중').closest('button')
  expect(within(refundingCard).getByText('4').className).toContain('text-blue-600')
  expect(within(refundingCard).getByText('진행 중')).toBeTruthy()
})

it('한 API가 실패해도 나머지 영역은 정상 표시된다', async () => {
  fetchAdminSummary.mockRejectedValue(new Error('network error'))
  render(<AdminOverviewDashboard onNavigate={vi.fn()} />)

  const membersCard = (await screen.findByText('총 회원')).closest('button')
  expect(within(membersCard).getByText('120')).toBeTruthy()

  const ongoingCard = screen.getByText('진행 중 페스티벌').closest('button')
  await waitFor(() => expect(within(ongoingCard).getByText('—')).toBeTruthy())
})

it('심사 대기 페스티벌 목록을 불러오지 못하면 그 영역에만 실패 문구를 보여준다', async () => {
  fetchFestivalSubmissionsPage.mockRejectedValue(new Error('network error'))
  render(<AdminOverviewDashboard onNavigate={vi.fn()} />)

  expect(await screen.findByText('불러오지 못했어요. 새로고침해주세요.')).toBeTruthy()
  expect((await screen.findByText('총 회원')).closest('button')).toBeTruthy()
})

it('진행 중인 페스티벌이 4개 미만이면 예정 페스티벌로 채운다', async () => {
  fetchFestivalOperationsPage.mockImplementation((params) => {
    if (params.operationStatus === 'ONGOING') {
      return Promise.resolve({ items: [operationItem({ id: 1 })], pagination: {} })
    }
    if (params.operationStatus === 'SCHEDULED') {
      return Promise.resolve({
        items: [
          operationItem({ id: 2, operationStatus: 'SCHEDULED', name: '예정 행사 1' }),
          operationItem({ id: 3, operationStatus: 'SCHEDULED', name: '예정 행사 2' }),
          operationItem({ id: 4, operationStatus: 'SCHEDULED', name: '예정 행사 3' }),
        ],
        pagination: {},
      })
    }
    return Promise.resolve({ items: [], pagination: {} })
  })
  render(<AdminOverviewDashboard onNavigate={vi.fn()} />)

  await screen.findByText('예정 행사 1')
  await waitFor(() => {
    expect(fetchFestivalOperationsPage).toHaveBeenCalledWith(
      expect.objectContaining({ operationStatus: 'SCHEDULED', sort: 'startAt,asc', size: 3 }),
      expect.anything(),
    )
  })
})

it('심사 대기 페스티벌의 대기일수를 계산한다', async () => {
  fetchFestivalSubmissionsPage.mockResolvedValue({
    items: [
      { id: 1, name: '오래된 신청', host: '주최자A', appliedAt: daysAgoStr(3) },
      { id: 2, name: '당일 신청 행사', host: '주최자B', appliedAt: daysAgoStr(0) },
    ],
    pagination: {},
  })
  render(<AdminOverviewDashboard onNavigate={vi.fn()} />)

  const waitingBadge = await screen.findByText('3일째 대기')
  expect(waitingBadge.className).toContain('text-amber-700')
  expect(await screen.findByText('오늘 신청')).toBeTruthy()
})

it('카드와 목록 줄 클릭 시 onNavigate에 탭·서브탭·필터를 전달한다', async () => {
  const onNavigate = vi.fn()
  fetchFestivalSubmissionsPage.mockResolvedValue({
    items: [{ id: 1, name: '심사 대상', host: '주최자', appliedAt: daysAgoStr(1) }],
    pagination: {},
  })
  const user = userEvent.setup()
  render(<AdminOverviewDashboard onNavigate={onNavigate} />)

  await user.click(await screen.findByText('활동 주최자'))
  expect(onNavigate).toHaveBeenCalledWith({ tab: 'organizer', organizerSub: 'list', organizerAccountFilter: 'ACTIVE' })

  await user.click(screen.getByText('심사 대상'))
  expect(onNavigate).toHaveBeenCalledWith({ tab: 'festival', festivalSub: 'festival' })

  const salesCard = (await screen.findByText('테스트 페스티벌 1')).closest('button')
  await user.click(salesCard)
  expect(onNavigate).toHaveBeenCalledWith({ tab: 'festival', festivalSub: 'operations', operationsFilter: 'ONGOING' })
})

it('새로고침 버튼을 누르면 전체를 다시 조회하고 마지막 갱신 시각을 보여준다', async () => {
  render(<AdminOverviewDashboard onNavigate={vi.fn()} />)
  await screen.findByText('총 회원')

  fetchAdminUsers.mockClear()
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: '새로고침' }))

  await waitFor(() => expect(fetchAdminUsers).toHaveBeenCalledTimes(1))
  expect(await screen.findByText('마지막 갱신', { exact: false })).toBeTruthy()
})
