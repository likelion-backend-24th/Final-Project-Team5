import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import StoreBoothDetail from './StoreBoothDetail'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchMyBoothDetail, fetchBoothQueueStatus } from '../api/boothApi'

vi.mock('../context/AuthContext.jsx')
vi.mock('../api/boothApi')

describe('StoreBoothDetail', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each([null, 'USER', 'ADMIN', 'HOST'])('shows the permission notice for %s without loading', (role) => {
    useAuth.mockReturnValue({ user: role ? { role } : null, isLoading: false })
    render(<MemoryRouter initialEntries={['/store/booths/0']}><StoreBoothDetail /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: '부스 운영자만 이용 가능한 페이지입니다' })).toBeTruthy()
    expect(screen.queryByText('불러오는 중…')).toBeNull()
    expect(fetchMyBoothDetail).not.toHaveBeenCalled()
    expect(fetchBoothQueueStatus).not.toHaveBeenCalled()
  })

  it('waits for authentication before showing the permission notice', () => {
    useAuth.mockReturnValue({ user: null, isLoading: true })
    render(<MemoryRouter><StoreBoothDetail /></MemoryRouter>)

    expect(screen.getByText('불러오는 중…')).toBeTruthy()
    expect(screen.queryByRole('heading')).toBeNull()
  })
})
