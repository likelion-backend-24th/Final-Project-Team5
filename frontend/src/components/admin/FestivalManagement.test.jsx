import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fetchFestivalSubmissionsPage } from '../../data/admin'
import { fetchCancellationRequests, fetchFestivalOperations, fetchPendingFestivals } from '../../api/adminApi'
import FestivalManagement from './FestivalManagement'

vi.mock('../../data/admin', { spy: true })
vi.mock('../../api/adminApi', { spy: true })

const EMPTY_PAGINATION = { page: 0, size: 5, totalItems: 0, totalPages: 1, hasNext: false, hasPrev: false }

beforeEach(() => {
  vi.clearAllMocks()
  fetchFestivalSubmissionsPage.mockResolvedValue({ items: [], pagination: EMPTY_PAGINATION })
  fetchPendingFestivals.mockResolvedValue({ data: { data: [], meta: { pagination: EMPTY_PAGINATION } } })
  fetchCancellationRequests.mockResolvedValue({ data: { data: [] } })
  fetchFestivalOperations.mockResolvedValue({ data: { data: [], meta: { pagination: EMPTY_PAGINATION } } })
})

it('uses the existing fallback image for a festival without an image', async () => {
  fetchFestivalSubmissionsPage.mockResolvedValue({
    items: [{
      id: 1, name: '이미지 없는 행사', host: '주최자', image: '', status: 'APPROVED',
      appliedAt: '2026.09.21', category: '음악', description: '', tickets: [],
    }],
    pagination: { ...EMPTY_PAGINATION, totalItems: 1 },
  })
  render(<FestivalManagement />)
  expect((await screen.findByRole('img', { name: '이미지 없는 행사' })).getAttribute('src')).toBe('/placeholder.jpg')
})

it('initialQuery가 없으면 등록 승인 목록은 승인대기 상태로 조회한다', async () => {
  render(<FestivalManagement />)
  await waitFor(() => {
    expect(fetchFestivalSubmissionsPage).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'PENDING' }),
      expect.anything(),
    )
  })
})

it('initialQuery가 있으면 등록 승인 목록은 전체 상태로 그 값을 키워드로 조회한다', async () => {
  render(<FestivalManagement initialQuery="주최자닉네임" />)
  await waitFor(() => {
    expect(fetchFestivalSubmissionsPage).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ALL', keyword: '주최자닉네임' }),
      expect.anything(),
    )
  })
})

it('운영 현황 서브탭은 필터·페이지 파라미터로 조회한다', async () => {
  const user = userEvent.setup()
  render(<FestivalManagement />)

  await user.click(screen.getByRole('button', { name: '운영 현황' }))

  await waitFor(() => {
    expect(fetchFestivalOperations).toHaveBeenCalledWith(
      expect.objectContaining({ operationStatus: 'ALL', page: 0, size: 10 }),
      expect.anything(),
    )
  })
})

it('initialSub가 있으면 그 서브탭으로 시작한다', async () => {
  render(<FestivalManagement initialSub="operations" />)

  await waitFor(() => {
    expect(fetchFestivalOperations).toHaveBeenCalledWith(
      expect.objectContaining({ operationStatus: 'ALL', page: 0, size: 10 }),
      expect.anything(),
    )
  })
  expect(screen.getByRole('button', { name: '운영 현황' }).className).toContain('bg-white text-blue-600')
})

it('initialSub가 없으면 기존과 동일하게 페스티벌 등록 승인 서브탭으로 시작한다', async () => {
  render(<FestivalManagement />)
  await waitFor(() => expect(fetchFestivalSubmissionsPage).toHaveBeenCalled())
  expect(fetchFestivalOperations).not.toHaveBeenCalled()
})

it('initialOperationsFilter가 있으면 운영 현황이 그 필터로 시작한다', async () => {
  render(<FestivalManagement initialSub="operations" initialOperationsFilter="ONGOING" />)

  await waitFor(() => {
    expect(fetchFestivalOperations).toHaveBeenCalledWith(
      expect.objectContaining({ operationStatus: 'ONGOING' }),
      expect.anything(),
    )
  })
})

it('initialCancellationFilter가 있으면 행사 취소 승인이 그 필터로 시작한다', async () => {
  render(<FestivalManagement initialSub="cancellation" initialCancellationFilter="REFUNDING" />)

  await waitFor(() => {
    expect(fetchCancellationRequests).toHaveBeenCalledWith('REFUNDING')
  })
})

it('행사 취소 승인 대기 목록은 예상 환불 금액을 확인할 수 없으면 안내 문구를 보여준다', async () => {
  fetchCancellationRequests.mockImplementation((status) =>
    Promise.resolve({
      data: {
        data: status === 'PENDING'
          ? [{
              festivalId: 10,
              name: '취소 대상 행사',
              reason: '주최자 사정',
              approved: false,
              status: 'PENDING',
              hostUserId: 1,
              hostNickname: '주최자',
              startAt: null,
              endAt: null,
              approvedAt: null,
              cancelledAt: null,
              rejectedAt: null,
              soldQuantity: 5,
              refundTargetPaymentCount: 3,
              expectedRefundAmount: null,
              unresolvedPaymentCount: 2,
            }]
          : [],
      },
    }),
  )
  const user = userEvent.setup()
  render(<FestivalManagement />)

  await user.click(screen.getByRole('button', { name: '행사 취소 승인' }))

  expect(await screen.findByText('환불 금액을 확인할 수 없어요', { exact: false })).toBeTruthy()
  expect(screen.getByText('2건은 금액 계산이 어려워 제외됐어요.')).toBeTruthy()
})
