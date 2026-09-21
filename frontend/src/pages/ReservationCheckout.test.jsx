import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import * as PortOne from '@portone/browser-sdk/v2'
import { fetchFestivalDetail } from '../api/festivalApi'
import { createReservation } from '../api/reservationApi'
import { completePayment, preparePayment } from '../api/paymentApi'
import { useAuth } from '../context/AuthContext.jsx'
import ReservationCheckout from './ReservationCheckout'

vi.mock('@portone/browser-sdk/v2', () => ({ requestPayment: vi.fn() }))
vi.mock('../api/festivalApi')
vi.mock('../api/reservationApi')
vi.mock('../api/paymentApi')
vi.mock('../context/AuthContext.jsx')

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  useAuth.mockReturnValue({
    user: { id: 1, role: 'USER', name: '테스트 사용자', username: 'user@example.com' },
    isLoading: false,
  })
  fetchFestivalDetail.mockResolvedValue({
    data: {
      data: {
        name: '테스트 축제',
        ticketTypes: [{ id: 3, name: '일반권', description: '', price: 10_000 }],
      },
    },
  })
  createReservation.mockResolvedValue({
    data: { data: { id: 7, expiresAt: '2026-09-21T12:00:00Z' } },
  })
  preparePayment.mockResolvedValue({
    data: {
      data: {
        paymentId: 'payment-1',
        storeId: 'store-1',
        channelKey: 'channel-1',
        totalAmount: 10_000,
      },
    },
  })
  PortOne.requestPayment.mockResolvedValue({ paymentId: 'payment-1' })
  completePayment.mockResolvedValue({ data: { data: { status: 'VIRTUAL_ACCOUNT_ISSUED' } } })
})

it('passes a same-origin redirect URL to the mobile virtual account request', async () => {
  render(
    <MemoryRouter initialEntries={['/festivals/1/reserve?ticketTypeId=3&quantity=1']}>
      <Routes>
        <Route path="/festivals/:id/reserve" element={<ReservationCheckout />} />
      </Routes>
    </MemoryRouter>,
  )

  const paymentButton = await screen.findByRole('button', { name: '10,000원 결제하기' })
  await waitFor(() => expect(paymentButton.disabled).toBe(false))
  await userEvent.click(screen.getByRole('radio', { name: '무통장입금' }))
  await userEvent.click(paymentButton)

  await waitFor(() => {
    expect(PortOne.requestPayment).toHaveBeenCalledWith(expect.objectContaining({
      payMethod: 'VIRTUAL_ACCOUNT',
      redirectUrl: `${window.location.origin}/payments/redirect`,
    }))
  })
  await screen.findByText('입금 계좌가 발급되었어요')
})
