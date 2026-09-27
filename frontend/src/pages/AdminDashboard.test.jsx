import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
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
  default: ({ sub, onViewFestivals, initialAccountFilter }) => (
    <div>
      <p>hostsSub:{sub}</p>
      <p>organizerAccountFilter:{initialAccountFilter ?? '(없음)'}</p>
      <button type="button" onClick={() => onViewFestivals('주최자닉네임')}>
        등록 페스티벌 3개
      </button>
    </div>
  ),
}))
vi.mock('../components/admin/FestivalManagement', () => ({
  default: ({ sub, initialQuery, initialOperationsFilter, initialCancellationFilter }) => (
    <div>
      <p>festivalQuery:{initialQuery || '(없음)'}</p>
      <p>festivalsSub:{sub}</p>
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

//AdminDashboard는 App.jsx에서 "/admin/*"로 마운트되므로, 현재 경로를 라우터가 실제로 매칭해 넘겨주도록
//감싼다(단순 <AdminDashboard/>만 렌더링하면 useLocation이 항상 "/"를 본다).
function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/*" element={<AdminDashboard />} />
      </Routes>
    </MemoryRouter>,
  )
}

it('주최자 목록에서 페스티벌 관리로 이동한 뒤 대시보드에서 등록 심사 대기로 이동하면 이전 검색어가 남지 않는다', async () => {
  const user = userEvent.setup()
  renderAt('/admin')

  //주최자 관리 > 주최자 목록에서 "등록 페스티벌 N개"를 눌러 검색어와 함께 페스티벌 관리로 이동한다.
  await user.click(screen.getByRole('link', { name: '주최자 관리' }))
  await user.click(screen.getByRole('button', { name: '등록 페스티벌 3개' }))
  expect(screen.getByText('festivalQuery:주최자닉네임')).toBeTruthy()

  //대시보드로 돌아가 "등록 심사 대기"로 이동하면, 남아있던 주최자 검색어가 등록 승인 목록에 새어 들어가면 안 된다.
  await user.click(screen.getByRole('link', { name: '대시보드' }))
  await user.click(screen.getByRole('button', { name: '등록 심사 대기' }))

  expect(screen.getByText('festivalQuery:(없음)')).toBeTruthy()
  expect(screen.getByText('festivalsSub:submissions')).toBeTruthy()
})

it('대시보드에서 활동 주최자로 이동하면 주최자 관리가 주최자 목록·활동중 필터로 시작한다', async () => {
  const user = userEvent.setup()
  renderAt('/admin')

  await user.click(screen.getByRole('button', { name: '활동 주최자' }))

  expect(screen.getByText('hostsSub:list')).toBeTruthy()
  expect(screen.getByText('organizerAccountFilter:ACTIVE')).toBeTruthy()
})

it('대시보드에서 환불 진행 중으로 이동하면 페스티벌 관리가 취소 승인·REFUNDING 필터로 시작한다', async () => {
  const user = userEvent.setup()
  renderAt('/admin')

  await user.click(screen.getByRole('button', { name: '환불 진행 중' }))

  expect(screen.getByText('festivalsSub:cancellations')).toBeTruthy()
  expect(screen.getByText('cancellationFilter:REFUNDING')).toBeTruthy()
})

it('탭을 직접 클릭하면 대시보드 이동 정보가 초기화된다', async () => {
  const user = userEvent.setup()
  renderAt('/admin')

  await user.click(screen.getByRole('button', { name: '환불 진행 중' }))
  expect(screen.getByText('cancellationFilter:REFUNDING')).toBeTruthy()

  await user.click(screen.getByRole('link', { name: '주최자 관리' }))
  await user.click(screen.getByRole('link', { name: '페스티벌 관리' }))

  expect(screen.getByText('festivalsSub:submissions')).toBeTruthy()
  expect(screen.getByText('cancellationFilter:(없음)')).toBeTruthy()
})

it('경로로 직접 진입하면 그 탭·서브탭이 렌더링된다', () => {
  renderAt('/admin/festivals/operations')

  expect(screen.getByText('festivalsSub:operations')).toBeTruthy()
})

it('회원 관리·정산 대시보드처럼 서브탭이 없는 탭도 경로로 렌더링된다', () => {
  renderAt('/admin/members')
  expect(screen.getByText('회원 관리 화면')).toBeTruthy()
})

it('쿼리의 status를 초기 필터로 반영한다', () => {
  renderAt('/admin/hosts/list?status=SUSPENDED')
  expect(screen.getByText('organizerAccountFilter:SUSPENDED')).toBeTruthy()
})

it('쿼리의 q를 초기 검색어로 반영한다', () => {
  renderAt('/admin/festivals/submissions?q=주최자닉네임')
  expect(screen.getByText('festivalQuery:주최자닉네임')).toBeTruthy()
})

it('유효하지 않은 status 값은 무시하고 기본값으로 렌더링한다', () => {
  renderAt('/admin/hosts/list?status=NOT_A_STATUS')
  expect(screen.getByText('organizerAccountFilter:(없음)')).toBeTruthy()
})

it('서브탭 없이 주최자 관리 경로로 들어오면 첫 서브탭으로 옮겨간다', () => {
  renderAt('/admin/hosts')
  expect(screen.getByText('hostsSub:applications')).toBeTruthy()
})

it('서브탭 없이 페스티벌 관리 경로로 들어오면 첫 서브탭으로 옮겨간다', () => {
  renderAt('/admin/festivals')
  expect(screen.getByText('festivalsSub:submissions')).toBeTruthy()
})

it('존재하지 않는 최상위 경로는 대시보드로 옮겨간다', () => {
  renderAt('/admin/abc')
  expect(screen.getByRole('link', { name: '대시보드' }).className).toContain('border-blue-600')
})

it('존재하지 않는 서브탭 경로는 대시보드로 옮겨간다', () => {
  renderAt('/admin/festivals/abc')
  expect(screen.getByRole('link', { name: '대시보드' }).className).toContain('border-blue-600')
})

it('주최자 목록 "등록 페스티벌 N개"를 누르면 페스티벌 등록 승인 화면으로 q와 함께 이동한다', async () => {
  const user = userEvent.setup()
  renderAt('/admin/hosts/list')

  await user.click(screen.getByRole('button', { name: '등록 페스티벌 3개' }))

  expect(screen.getByText('festivalsSub:submissions')).toBeTruthy()
  expect(screen.getByText('festivalQuery:주최자닉네임')).toBeTruthy()
})

it('ADMIN이 아니면 하위 경로에서도 안내 화면을 보여준다', () => {
  useAuth.mockReturnValue({ user: { role: 'USER' }, isLoading: false })
  renderAt('/admin/festivals/operations')

  expect(screen.getByText('운영자 권한이 필요합니다')).toBeTruthy()
})
