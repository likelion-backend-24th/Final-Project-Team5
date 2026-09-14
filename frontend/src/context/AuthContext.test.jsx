import { expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from './AuthContext'
import * as api from '../api/authApi'
import { getAccessToken, clearAccessToken } from '../api/tokenStore'
vi.mock('../api/authApi')
function Consumer() {
  const { user, isLoading, applyTokenLogin } = useAuth()
  return <><p>{isLoading ? 'loading' : user?.role || 'anonymous'}</p><button onClick={() => applyTokenLogin('helper-token')}>apply</button><p>{user?.festivalId}</p></>
}
beforeEach(() => { vi.clearAllMocks(); clearAccessToken() })
it('sets access token before fetching the new helper user', async () => {
  api.fetchMyInfo.mockResolvedValueOnce({ data: { data: { role: 'HOST' } } })
  render(<AuthProvider><Consumer /></AuthProvider>)
  await screen.findByText('HOST')
  api.fetchMyInfo.mockImplementationOnce(async () => {
    expect(getAccessToken()).toBe('helper-token')
    return { data: { data: { role: 'HELPER', festivalId: 7 } } }
  })
  await userEvent.click(screen.getByText('apply'))
  await screen.findByText('HELPER')
  expect(screen.getByText('7')).toBeTruthy()
  await waitFor(() => expect(api.fetchMyInfo).toHaveBeenCalledTimes(2))
})
