import { expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { useAuth } from './context/AuthContext'
import { fetchHelperInvitation } from './api/authApi'
vi.mock('./context/AuthContext')
vi.mock('./api/authApi')
it.each([{ role: 'HELPER' }, { role: 'USER', profileSetupRequired: true }])('keeps invitation route accessible to an existing restricted session', async user => {
  useAuth.mockReturnValue({ user, isLoading: false, applyTokenLogin: vi.fn() })
  fetchHelperInvitation.mockResolvedValue({ data: { data: { username: 'helper-new@helper.local', festivalName: '새 초대 행사' } } })
  render(<MemoryRouter initialEntries={['/helper-invite/token']}><App /></MemoryRouter>)
  await screen.findByText('새 초대 행사')
  expect(screen.getByLabelText('새 비밀번호')).toBeTruthy()
})
