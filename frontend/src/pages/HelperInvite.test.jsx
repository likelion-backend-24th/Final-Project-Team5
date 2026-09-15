import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigationType } from 'react-router-dom'
import HelperInvite from './HelperInvite'
import * as api from '../api/authApi'
import { useAuth } from '../context/AuthContext'
vi.mock('../api/authApi')
vi.mock('../context/AuthContext')
const applyTokenLogin = vi.fn()
const info = { username: 'helper-test@helper.local', festivalId: 7, festivalName: '테스트 페스티벌', festivalStartAt: '2030-05-01T12:00', festivalEndAt: '2030-05-03T12:00', maskedEmail: 'p***@example.com', expiresAt: '2030-05-02T12:00' }
function Home() { const type = useNavigationType(); return <p>홈 {type}</p> }
function view() { return render(<MemoryRouter initialEntries={['/helper-invite/token']}><Routes><Route path="/helper-invite/:token" element={<HelperInvite />} /><Route path="/" element={<Home />} /></Routes></MemoryRouter>) }
beforeEach(() => {
  vi.clearAllMocks()
  useAuth.mockReturnValue({ user: null, isLoading: false, applyTokenLogin })
  api.fetchHelperInvitation.mockResolvedValue({ data: { data: info } })
  api.acceptHelperInvitation.mockResolvedValue({ data: { data: { accessToken: 'new-access' } } })
  applyTokenLogin.mockResolvedValue({ role: 'HELPER', festivalId: 7 })
})
it('loads invitation, validates matching password, applies login then replaces history', async () => {
  const user = userEvent.setup(); view()
  await screen.findByText('테스트 페스티벌')
  expect(screen.getByText('helper-test@helper.local')).toBeTruthy()
  await user.type(screen.getByLabelText('새 비밀번호'), 'password123')
  await user.type(screen.getByLabelText('새 비밀번호 확인'), 'different123')
  await user.click(screen.getByRole('button', { name: '비밀번호 설정하고 시작하기' }))
  expect(screen.getByRole('alert').textContent).toContain('일치하지')
  expect(api.acceptHelperInvitation).not.toHaveBeenCalled()
  await user.clear(screen.getByLabelText('새 비밀번호 확인'))
  await user.type(screen.getByLabelText('새 비밀번호 확인'), 'password123')
  await user.click(screen.getByRole('button', { name: '비밀번호 표시' }))
  expect(screen.getByLabelText('새 비밀번호').type).toBe('text')
  await user.click(screen.getByRole('button', { name: '비밀번호 설정하고 시작하기' }))
  await screen.findByText('홈 REPLACE')
  expect(api.acceptHelperInvitation).toHaveBeenCalledWith('token', 'password123', 'password123')
  expect(applyTokenLogin).toHaveBeenCalledWith('new-access')
})
it.each([['INVITATION_EXPIRED', '만료'], ['INVITATION_ACCEPTED', '이미 사용'], ['INVITATION_REVOKED', '해지'], ['INVITATION_INVALID', '올바르지'], ['HELPER_FESTIVAL_ENDED', '종료']])('shows %s with host recovery guidance', async (code, text) => {
  api.fetchHelperInvitation.mockRejectedValue({ response: { data: { errorCode: code } } })
  view()
  expect((await screen.findByRole('alert')).textContent).toContain(text)
  expect(screen.getByText(/주최자에게 재발송/)).toBeTruthy()
  expect(screen.queryByLabelText('새 비밀번호')).toBeNull()
})
it('explains session replacement and prevents duplicate submissions', async () => {
  useAuth.mockReturnValue({ user: { nickname: '기존 사용자' }, isLoading: false, applyTokenLogin })
  let finish
  api.acceptHelperInvitation.mockReturnValue(new Promise(resolve => { finish = resolve }))
  const user = userEvent.setup(); view()
  await screen.findByText(/기존 사용자/)
  await user.type(screen.getByLabelText('새 비밀번호'), 'password123')
  await user.type(screen.getByLabelText('새 비밀번호 확인'), 'password123')
  await user.dblClick(screen.getByRole('button', { name: '비밀번호 설정하고 시작하기' }))
  expect(api.acceptHelperInvitation).toHaveBeenCalledTimes(1)
  finish({ data: { data: { accessToken: 'new-access' } } })
  await waitFor(() => expect(applyTokenLogin).toHaveBeenCalledTimes(1))
})
