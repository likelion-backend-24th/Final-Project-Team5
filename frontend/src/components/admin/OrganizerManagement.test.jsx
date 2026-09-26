import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { fetchOrganizerApplications } from '../../data/admin'
import { fetchAdminHosts, fetchFestivalHostCounts } from '../../api/adminApi'
import OrganizerManagement from './OrganizerManagement'

//서브탭이 NavLink라 라우터 컨텍스트가 필요하다. sub는 이제 부모(AdminDashboard)가 내려주는 controlled
//prop이라, 기본값('applications')이 필요한 테스트는 명시적으로 넘긴다.
function renderOrganizerManagement(props) {
  return render(
    <MemoryRouter>
      <OrganizerManagement sub="applications" {...props} />
    </MemoryRouter>,
  )
}

vi.mock('../../data/admin', { spy: true })
vi.mock('../../api/adminApi', { spy: true })

beforeEach(() => {
  vi.clearAllMocks()
  fetchOrganizerApplications.mockResolvedValue([])
  //매 호출마다 새 배열을 반환해야 items(state)의 참조가 바뀌어 host-counts 재조회 이펙트가 트리거된다.
  fetchAdminHosts.mockImplementation(() =>
    Promise.resolve({
      data: {
        data: [
          { id: 1, nickname: '주최자1', email: 'host1@example.com', accountStatus: 'ACTIVE', joinedAt: '2026-01-01' },
          { id: 2, nickname: '주최자2', email: 'host2@example.com', accountStatus: 'ACTIVE', joinedAt: '2026-01-02' },
        ],
        meta: { pagination: { page: 0, size: 10, totalItems: 2, totalPages: 1, hasNext: false, hasPrev: false } },
      },
    }),
  )
  fetchFestivalHostCounts.mockResolvedValue({ data: { data: { 1: 3, 2: 0 } } })
})

it('shows nearby pages and ellipses instead of every page', async () => {
  fetchOrganizerApplications.mockResolvedValue(Array.from({ length: 300 }, (_, i) => ({
    id: i + 1, name: `신청자 ${i + 1}`, email: `host${i + 1}@example.com`,
    status: 'APPROVED', appliedAt: '2026.09.21',
  })))
  const user = userEvent.setup()
  renderOrganizerManagement()

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
  renderOrganizerManagement()

  const pendingFilter = await screen.findByRole('button', { name: '승인대기' })
  expect(pendingFilter.className).toContain('bg-blue-600')
  expect(await screen.findByText('pending@example.com')).toBeTruthy()
  expect(screen.queryByText('approved@example.com')).toBeNull()
})

it('주최자 목록 서브탭은 서버 파라미터(page/size/status)로 조회하고, 목록이 올 때마다 현재 페이지 주최자 id로 등록 페스티벌 개수를 조회한다', async () => {
  const user = userEvent.setup()
  renderOrganizerManagement({ sub: 'list' })

  await waitFor(() => {
    expect(fetchAdminHosts).toHaveBeenCalledWith({ page: 0, size: 10 }, expect.anything())
  })
  await waitFor(() => {
    expect(fetchFestivalHostCounts).toHaveBeenCalledWith([1, 2])
  })

  await user.click(screen.getByRole('button', { name: '정지됨' }))

  await waitFor(() => {
    expect(fetchAdminHosts).toHaveBeenLastCalledWith({ page: 0, size: 10, status: 'SUSPENDED' }, expect.anything())
  })
  //주최자 목록이 새로 올 때마다(필터가 바뀌어도) 현재 페이지 id로 다시 조회한다.
  await waitFor(() => {
    expect(fetchFestivalHostCounts).toHaveBeenCalledTimes(2)
  })
})

it('sub가 list면 주최자 목록 서브탭으로 시작한다', async () => {
  renderOrganizerManagement({ sub: 'list' })

  await waitFor(() => {
    expect(fetchAdminHosts).toHaveBeenCalledWith({ page: 0, size: 10 }, expect.anything())
  })
  expect(screen.queryByText('pending@example.com')).toBeNull()
})

it('sub가 applications면 주최자 신청 승인 서브탭을 보여준다', async () => {
  renderOrganizerManagement({ sub: 'applications' })

  const pendingFilter = await screen.findByRole('button', { name: '승인대기' })
  expect(pendingFilter).toBeTruthy()
  expect(fetchAdminHosts).not.toHaveBeenCalled()
})

it('initialAccountFilter가 있으면 주최자 목록이 그 상태 필터로 시작한다', async () => {
  renderOrganizerManagement({ sub: 'list', initialAccountFilter: 'ACTIVE' })

  await waitFor(() => {
    expect(fetchAdminHosts).toHaveBeenCalledWith({ page: 0, size: 10, status: 'ACTIVE' }, expect.anything())
  })
  const activeFilter = await screen.findByRole('button', { name: '활동중' })
  expect(activeFilter.className).toContain('bg-blue-600')
})

it('등록 페스티벌 개수 조회에 실패해도 주최자 목록은 정상 표시되고 개수는 —로 보인다', async () => {
  fetchFestivalHostCounts.mockRejectedValue(new Error('network error'))
  renderOrganizerManagement({ sub: 'list' })

  expect((await screen.findAllByText('주최자1')).length).toBeGreaterThan(0)
  await waitFor(() => {
    expect(fetchFestivalHostCounts).toHaveBeenCalledWith([1, 2])
  })
  expect(screen.getAllByText('—').length).toBeGreaterThan(0)
})
