import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import HostHelperAccounts from './HostHelperAccounts'
import * as api from '../api/hostFestivalApi'
vi.mock('../api/hostFestivalApi')
const row = { helperUserId: 20, username: 'helper-test@helper.local', email: 'person@example.com', status: 'PENDING', deliveryStatus: 'SENT', legacy: false }
beforeEach(() => {
  vi.clearAllMocks()
  api.fetchHelperAccounts.mockResolvedValue({ data: { data: { totalCount: 1, helpers: [row] } } })
  api.createHelperAccount.mockResolvedValue({})
  api.resendHelperInvitation.mockResolvedValue({})
  api.revokeHelperAccount.mockResolvedValue({})
  api.convertLegacyHelper.mockResolvedValue({})
})
describe('HOST helper invitations', () => {
  it('submits normalized email and exposes no password controls', async () => {
    const user = userEvent.setup()
    render(<HostHelperAccounts festivalId="7" />)
    await screen.findByText('초대 대기')
    await user.type(screen.getByLabelText('초대 이메일'), 'Person@Example.COM')
    await user.click(screen.getByRole('button', { name: '이메일 초대' }))
    await waitFor(() => expect(api.createHelperAccount).toHaveBeenCalledWith('7', 'person@example.com'))
    expect(document.querySelector('input[type="password"]')).toBeNull()
    expect(screen.queryByText('비밀번호는 지금만 확인할 수 있어요')).toBeNull()
    expect(screen.queryByRole('button', { name: '비밀번호 재발급' })).toBeNull()
  })
  it('renders statuses, resends and revokes', async () => {
    api.fetchHelperAccounts.mockResolvedValue({ data: { data: { totalCount: 5, helpers: [row,
      { ...row, helperUserId: 21, status: 'ACCEPTED' }, { ...row, helperUserId: 22, status: 'EXPIRED' },
      { ...row, helperUserId: 23, deliveryStatus: 'SEND_FAILED' }, { ...row, helperUserId: 24, status: 'REVOKED' }] } } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    render(<HostHelperAccounts festivalId="7" />)
    for (const label of ['초대 대기', '활성화 완료', '만료', '발송 실패']) await screen.findByText(label)
    await user.click(screen.getAllByRole('button', { name: '재발송' })[0])
    await waitFor(() => expect(api.resendHelperInvitation).toHaveBeenCalledWith('7', 20))
    await user.click(screen.getAllByRole('button', { name: '해지' })[0])
    await waitFor(() => expect(api.revokeHelperAccount).toHaveBeenCalledWith('7', 20))
  })
  it('reloads saved failed invitation after SMTP failure', async () => {
    api.createHelperAccount.mockRejectedValue({ response: { data: { errorCode: 'INVITATION_SEND_FAILED' } } })
    const user = userEvent.setup()
    render(<HostHelperAccounts festivalId="7" />)
    await user.type(screen.getByLabelText('초대 이메일'), 'person@example.com')
    await user.click(screen.getByRole('button', { name: '이메일 초대' }))
    expect((await screen.findByRole('alert')).textContent).toContain('계정은 보존')
    expect(api.fetchHelperAccounts.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
  it('converts legacy account with contact email', async () => {
    api.fetchHelperAccounts.mockResolvedValue({ data: { data: { totalCount: 1, helpers: [{ ...row, legacy: true, email: null, status: 'ACTIVE' }] } } })
    const user = userEvent.setup()
    render(<HostHelperAccounts festivalId="7" />)
    await user.type(await screen.findByLabelText('전환 이메일'), 'legacy@example.com')
    await user.click(screen.getByRole('button', { name: '이메일 초대로 전환' }))
    await waitFor(() => expect(api.convertLegacyHelper).toHaveBeenCalledWith('7', 20, 'legacy@example.com'))
  })
})
