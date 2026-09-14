import { expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HelperHome from './HelperHome'
import { useAuth } from '../context/AuthContext'
import { fetchFestivalDetail } from '../api/festivalApi'
vi.mock('../context/AuthContext')
vi.mock('../api/festivalApi')
it('shows the festival assigned to the activated helper', async () => {
  useAuth.mockReturnValue({ user: { role: 'HELPER', festivalId: 7, nickname: '도우미' } })
  fetchFestivalDetail.mockResolvedValue({ data: { data: { name: '초대된 행사', startAt: '2030-05-01T12:00', endAt: '2030-05-02T12:00' } } })
  render(<MemoryRouter><HelperHome /></MemoryRouter>)
  await screen.findByText('초대된 행사')
  expect(fetchFestivalDetail).toHaveBeenCalledWith(7)
})


it('shows the invitation snapshot while public festival details are unavailable', async () => {
  useAuth.mockReturnValue({ user: { role: 'HELPER', festivalId: 8, assignedFestival: { id: 8, name: '공개 전 초대 행사', startAt: '2030-05-01T12:00', endAt: '2030-05-02T12:00' } } })
  fetchFestivalDetail.mockRejectedValue(new Error('not published'))
  render(<MemoryRouter><HelperHome /></MemoryRouter>)
  expect(screen.getByText('공개 전 초대 행사')).toBeTruthy()
})
