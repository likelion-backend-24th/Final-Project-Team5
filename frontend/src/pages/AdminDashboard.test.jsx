import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useAuth } from '../context/AuthContext'
import AdminDashboard from './AdminDashboard'

vi.mock('../context/AuthContext')

vi.mock('../components/admin/AdminOverviewDashboard', () => ({
  default: ({ onNavigate }) => (
    <div>
      <button type="button" onClick={() => onNavigate({ tab: 'festival', festivalSub: 'festival' })}>
        등록 심사 대기
      </button>
      <button
        type="button"
        onClick={() => onNavigate({ tab: 'festival', festivalSub: 'cancellation', cancellationFilter: 'REFUNDING' })}
      >
        환불 진행 중
      </button>
      <button
        type="button"
        onClick={() => onNavigate({ tab: 'organizer', organizerSub: 'list', organizerAccountFilter: 'ACTIVE' })}
      >
        활동 주최자
      </button>
    </div>
  ),
}))
vi.mock('../components/admin/OrganizerManagement', () => ({
  default: ({ onViewFestivals, initialSub, initialAccountFilter }) => (
    <div>
      <p>organizerSub:{initialSub ?? '(기본)'}</p>
      <p>organizerAccountFilter:{initialAccountFilter ?? '(없음)'}</p>
      <button type="button" onClick={() => onViewFestivals('주최자닉네임')}>
        등록 페스티벌 3개
      </button>
    </div>
  ),
}))
vi.mock('../components/admin/FestivalManagement', () => ({
  default: ({ initialQuery, initialSub, initialOperationsFilter, initialCancellationFilter }) => (
    <div>
      <p>festivalQuery:{initialQuery || '(없음)'}</p>
      <p>festivalSub:{initialSub ?? '(기본)'}</p>
      <p>operationsFilter:{initialOperationsFilter ?? '(없음)'}</p>
      <p>cancellationFilter:{initialCancellationFilter ?? '(없음)'}</p>
    </div>
  ),
}))
vi.mock('../components/admin/UserManagement', () => ({ default: () => <div>회원 관리 화면</div> }))
vi.mock('../components/admin/SettlementDashboard', () => ({ default: () => <div>정산 대시보드 화면</div> }))

beforeEach(() => {
  useAuth.mockReturnValue({ user: { role: 'ADMIN' }, isLoading: false })
})

it('주최자 목록에서 페스티벌 관리로 이동한 뒤 대시보드에서 등록 심사 대기로 이동하면 이전 검색어가 남지 않는다', async () => {
  const user = userEvent.setup()
  render(<AdminDashboard />)

  //주최자 관리 > 주최자 목록에서 "등록 페스티벌 N개"를 눌러 검색어와 함께 페스티벌 관리로 이동한다.
  await user.click(screen.getByRole('button', { name: '주최자 관리' }))
  await user.click(screen.getByRole('button', { name: '등록 페스티벌 3개' }))
  expect(screen.getByText('festivalQuery:주최자닉네임')).toBeTruthy()

  //대시보드로 돌아가 "등록 심사 대기"로 이동하면, 남아있던 주최자 검색어가 등록 승인 목록에 새어 들어가면 안 된다.
  await user.click(screen.getByRole('button', { name: '대시보드' }))
  await user.click(screen.getByRole('button', { name: '등록 심사 대기' }))

  expect(screen.getByText('festivalQuery:(없음)')).toBeTruthy()
  expect(screen.getByText('festivalSub:festival')).toBeTruthy()
})

it('대시보드에서 활동 주최자로 이동하면 주최자 관리가 주최자 목록·활동중 필터로 시작한다', async () => {
  const user = userEvent.setup()
  render(<AdminDashboard />)

  await user.click(screen.getByRole('button', { name: '활동 주최자' }))

  expect(screen.getByText('organizerSub:list')).toBeTruthy()
  expect(screen.getByText('organizerAccountFilter:ACTIVE')).toBeTruthy()
})

it('대시보드에서 환불 진행 중으로 이동하면 페스티벌 관리가 취소 승인·REFUNDING 필터로 시작한다', async () => {
  const user = userEvent.setup()
  render(<AdminDashboard />)

  await user.click(screen.getByRole('button', { name: '환불 진행 중' }))

  expect(screen.getByText('festivalSub:cancellation')).toBeTruthy()
  expect(screen.getByText('cancellationFilter:REFUNDING')).toBeTruthy()
})

it('탭을 직접 클릭하면 대시보드 이동 정보가 초기화된다', async () => {
  const user = userEvent.setup()
  render(<AdminDashboard />)

  await user.click(screen.getByRole('button', { name: '환불 진행 중' }))
  expect(screen.getByText('cancellationFilter:REFUNDING')).toBeTruthy()

  await user.click(screen.getByRole('button', { name: '주최자 관리' }))
  await user.click(screen.getByRole('button', { name: '페스티벌 관리' }))

  expect(screen.getByText('festivalSub:(기본)')).toBeTruthy()
  expect(screen.getByText('cancellationFilter:(없음)')).toBeTruthy()
})
