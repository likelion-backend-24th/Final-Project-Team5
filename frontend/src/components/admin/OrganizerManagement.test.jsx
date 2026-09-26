import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fetchOrganizerApplications } from '../../data/admin'
import { fetchAdminHosts, fetchPendingFestivals } from '../../api/adminApi'
import OrganizerManagement from './OrganizerManagement'

vi.mock('../../data/admin', { spy: true })
vi.mock('../../api/adminApi', { spy: true })

beforeEach(() => {
  vi.clearAllMocks()
  fetchOrganizerApplications.mockResolvedValue([])
  fetchAdminHosts.mockResolvedValue({
    data: {
      data: [],
      meta: { pagination: { page: 0, size: 10, totalItems: 0, totalPages: 0, hasNext: false, hasPrev: false } },
    },
  })
  fetchPendingFestivals.mockResolvedValue({ data: { data: [] } })
})

it('shows nearby pages and ellipses instead of every page', async () => {
  fetchOrganizerApplications.mockResolvedValue(Array.from({ length: 300 }, (_, i) => ({
    id: i + 1, name: `신청자 ${i + 1}`, email: `host${i + 1}@example.com`,
    status: 'APPROVED', appliedAt: '2026.09.21',
  })))
  const user = userEvent.setup()
  render(<OrganizerManagement />)

  //기본 필터는 '승인대기'라 이 테스트의 APPROVED 신청들을 보려면 '전체'로 바꿔야 한다.
  await user.click(await screen.findByRole('button', { name: '전체' }))

  const first = await screen.findByRole('button', { name: '1', exact: true })
  expect(first.getAttribute('aria-current')).toBe('page')
  expect(first.className).toContain('h-11 w-11')
  expect(screen.queryByRole('button', { name: '100', exact: true })).toBeNull()
  expect(screen.getByRole('button', { name: '이전 페이지' }).disabled).toBe(true)
  await user.click(screen.getByRole('button', { name: '3', exact: true }))
  await user.click(screen.getByRole('button', { name: '5', exact: true }))
  expect(screen.queryByRole('button', { name: '2', exact: true })).toBeNull()
  expect(screen.getByRole('button', { name: '7', exact: true })).toBeTruthy()
  expect(screen.getAllByText('…')).toHaveLength(2)
  await user.click(screen.getByRole('button', { name: '다음 페이지' }))
  expect(screen.getByRole('button', { name: '6', exact: true }).getAttribute('aria-current')).toBe('page')
})

it('주최자 신청 승인 서브탭은 승인대기 상태 필터로 시작한다', async () => {
  fetchOrganizerApplications.mockResolvedValue([
    { id: '1', name: '신청자 1', email: 'pending@example.com', status: 'PENDING', appliedAt: '2026.09.20' },
    { id: '2', name: '신청자 2', email: 'approved@example.com', status: 'APPROVED', appliedAt: '2026.09.21' },
  ])
  render(<OrganizerManagement />)

  const pendingFilter = await screen.findByRole('button', { name: '승인대기' })
  expect(pendingFilter.className).toContain('bg-blue-600')
  expect(await screen.findByText('pending@example.com')).toBeTruthy()
  expect(screen.queryByText('approved@example.com')).toBeNull()
})

it('주최자 목록 서브탭은 서버 파라미터(page/size/status)로 조회한다', async () => {
  const user = userEvent.setup()
  render(<OrganizerManagement />)

  await user.click(screen.getByRole('button', { name: '주최자 목록' }))

  await waitFor(() => {
    expect(fetchAdminHosts).toHaveBeenCalledWith({ page: 0, size: 10 }, expect.anything())
  })
  expect(fetchPendingFestivals).toHaveBeenCalledTimes(1)

  await user.click(screen.getByRole('button', { name: '정지됨' }))

  await waitFor(() => {
    expect(fetchAdminHosts).toHaveBeenLastCalledWith({ page: 0, size: 10, status: 'SUSPENDED' }, expect.anything())
  })
  //필터를 바꿔도 페스티벌 개수 집계는 다시 부르지 않는다.
  expect(fetchPendingFestivals).toHaveBeenCalledTimes(1)
})
