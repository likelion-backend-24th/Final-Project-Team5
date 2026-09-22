import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import Login from './Login'

vi.mock('../context/AuthContext.jsx')

beforeEach(() => {
  vi.clearAllMocks()
  useAuth.mockReturnValue({ login: vi.fn().mockResolvedValue(undefined) })
})

function Destination() {
  const location = useLocation()
  return <p>도착: {location.pathname}{location.search}{location.hash}</p>
}

it.each([
  ['/festivals/7/zones', '/festivals/7/zones'],
  [{ pathname: '/festivals/7/zones', search: '?ticket=2', hash: '#tickets' }, '/festivals/7/zones?ticket=2#tickets'],
  [undefined, '/'],
  ['https://example.com', '/'],
  ['//example.com', '/'],
  ['/\\example.com', '/'],
  ['/\n/example.com', '/'],
  [{ pathname: '/', search: '/example.com' }, '/'],
])('returns only to an internal destination: %j', async (from, expected) => {
  const user = userEvent.setup()
  render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state: { from } }]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  )
  await user.type(screen.getByLabelText('이메일'), 'buyer@example.com')
  await user.type(screen.getByLabelText('비밀번호', { exact: true }), 'test-password')
  await user.click(screen.getByRole('button', { name: '로그인', exact: true }))
  expect(await screen.findByText(`도착: ${expected}`)).toBeTruthy()
})
