import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ResetPassword from './ResetPassword'
import * as api from '../api/authApi'

vi.mock('../api/authApi')

function view() {
  return render(<MemoryRouter><ResetPassword /></MemoryRouter>)
}

async function verifyCode(user) {
  await user.type(screen.getByLabelText('이메일'), 'user@test.com')
  await user.click(screen.getByRole('button', { name: '인증코드 받기' }))
  await user.type(await screen.findByLabelText('인증코드'), '123456')
  await user.click(screen.getByRole('button', { name: '인증하기' }))
}

beforeEach(() => {
  vi.clearAllMocks()
  api.sendEmailVerificationCode.mockResolvedValue({ data: { data: null } })
  api.verifyEmailVerificationCode.mockResolvedValue({ data: { data: { verificationToken: 'token-from-verify' } } })
  api.resetPassword.mockResolvedValue({ data: { data: null } })
})

it('sends the token received from code verification with the new password', async () => {
  const user = userEvent.setup(); view()
  await verifyCode(user)

  await user.type(await screen.findByLabelText('새 비밀번호'), 'newpassword1234')
  await user.type(screen.getByLabelText('새 비밀번호 확인'), 'newpassword1234')
  await user.click(screen.getByRole('button', { name: '비밀번호 변경' }))

  await screen.findByText('비밀번호가 변경되었어요')
  expect(api.resetPassword).toHaveBeenCalledWith({
    username: 'user@test.com',
    verificationToken: 'token-from-verify',
    newPassword: 'newpassword1234',
  })
})

it('stays on the code step and shows the server guidance after too many wrong codes', async () => {
  api.verifyEmailVerificationCode.mockRejectedValue({
    response: { data: { errorCode: 'TOO_MANY_VERIFY_ATTEMPTS', message: '인증코드를 5회 잘못 입력했습니다. 인증코드를 다시 요청해주세요.' } },
  })
  const user = userEvent.setup(); view()
  await verifyCode(user)

  expect(await screen.findByText(/5회 잘못 입력했습니다/)).toBeTruthy()
  expect(screen.queryByLabelText('새 비밀번호')).toBeNull()
  expect(api.resetPassword).not.toHaveBeenCalled()
})

it('returns to the email step when the server no longer accepts the verification', async () => {
  api.resetPassword.mockRejectedValue({ response: { data: { errorCode: 'EMAIL_NOT_VERIFIED', message: '이메일 인증이 완료되지 않았습니다.' } } })
  const user = userEvent.setup(); view()
  await verifyCode(user)

  await user.type(await screen.findByLabelText('새 비밀번호'), 'newpassword1234')
  await user.type(screen.getByLabelText('새 비밀번호 확인'), 'newpassword1234')
  await user.click(screen.getByRole('button', { name: '비밀번호 변경' }))

  expect((await screen.findByRole('alert')).textContent).toContain('인증코드를 다시 받아주세요')
  expect(screen.getByRole('button', { name: '인증코드 받기' })).toBeTruthy()
})
