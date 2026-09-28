import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { useAuth } from '../../context/AuthContext.jsx'
import { fetchAdminUsers } from '../../api/adminApi'
import UserManagement from './UserManagement'

vi.mock('../../context/AuthContext.jsx')
vi.mock('../../api/adminApi', { spy: true })

function mockUsers(items) {
  fetchAdminUsers.mockResolvedValue({
    data: {
      data: items,
      meta: { pagination: { page: 0, size: 10, totalItems: items.length, totalPages: 1, hasNext: false, hasPrev: false } },
    },
  })
}

//데스크톱 테이블과 모바일 카드가 동시에 렌더링(반응형 클래스만 다름)되므로, 닉네임으로 각 행/카드를 찾아 그 안에서 검사한다.
function findRows(nickname) {
  return screen.getAllByText(nickname).map((el) => within(el.closest('tr') ?? el.closest('li')))
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuth.mockReturnValue({ user: { id: 999 } })
})

it('활동중인 STOREHOST 행에는 정지 버튼을, 정지된 STOREHOST 행에는 정지 해제 버튼을 보여준다', async () => {
  mockUsers([
    { id: 1, nickname: 'storehost-active', email: 'active@example.com', role: 'STOREHOST', status: 'ACTIVE', joinedAt: '2026-01-01', providers: [] },
    { id: 2, nickname: 'storehost-suspended', email: 'suspended@example.com', role: 'STOREHOST', status: 'SUSPENDED', joinedAt: '2026-01-02', providers: [] },
  ])
  render(<UserManagement />)
  await screen.findAllByText('storehost-active')

  const activeRows = findRows('storehost-active')
  expect(activeRows.length).toBeGreaterThan(0)
  activeRows.forEach((row) => expect(row.getByRole('button', { name: '정지' })).toBeTruthy())

  const suspendedRows = findRows('storehost-suspended')
  expect(suspendedRows.length).toBeGreaterThan(0)
  suspendedRows.forEach((row) => expect(row.getByRole('button', { name: '정지 해제' })).toBeTruthy())
})

it('HELPER 행은 여전히 관리 버튼 없이 —로 보인다', async () => {
  mockUsers([{ id: 3, nickname: 'helper1', email: 'helper1@example.com', role: 'HELPER', status: 'ACTIVE', joinedAt: '2026-01-03', providers: [] }])
  render(<UserManagement />)
  await screen.findAllByText('helper1')

  const rows = findRows('helper1')
  expect(rows.length).toBeGreaterThan(0)
  rows.forEach((row) => {
    expect(row.queryByRole('button', { name: '정지' })).toBeNull()
    expect(row.queryByRole('button', { name: '정지 해제' })).toBeNull()
    expect(row.getByText('—')).toBeTruthy()
  })
})

it('ADMIN이거나 본인인 행은 관리 버튼 없이 —로 보인다', async () => {
  mockUsers([
    { id: 4, nickname: 'admin1', email: 'admin1@example.com', role: 'ADMIN', status: 'ACTIVE', joinedAt: '2026-01-04', providers: [] },
    { id: 999, nickname: '나자신', email: 'me@example.com', role: 'STOREHOST', status: 'ACTIVE', joinedAt: '2026-01-05', providers: [] },
  ])
  render(<UserManagement />)
  await screen.findAllByText('admin1')

  const adminRows = findRows('admin1')
  expect(adminRows.length).toBeGreaterThan(0)
  adminRows.forEach((row) => expect(row.queryByRole('button', { name: '정지' })).toBeNull())

  const selfRows = findRows('나자신')
  expect(selfRows.length).toBeGreaterThan(0)
  selfRows.forEach((row) => expect(row.queryByRole('button', { name: '정지' })).toBeNull())
})
