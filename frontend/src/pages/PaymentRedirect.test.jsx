import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { completePayment } from '../api/paymentApi'
import { getPendingPaymentRedirect, savePendingPaymentRedirect } from '../api/paymentRedirectStore'
import { useAuth } from '../context/AuthContext.jsx'
import PaymentRedirect from './PaymentRedirect'

vi.mock('../api/paymentApi')
vi.mock('../context/AuthContext.jsx')

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  useAuth.mockReturnValue({ user: { id: 1, role: 'USER' }, isLoading: false })
})

it('verifies a virtual account return and clears the pending payment', async () => {
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })
  completePayment.mockResolvedValue({ data: { data: { status: 'VIRTUAL_ACCOUNT_ISSUED' } } })

  render(
    <MemoryRouter initialEntries={['/payments/redirect?paymentId=payment-1']}>
      <PaymentRedirect />
    </MemoryRouter>,
  )

  await screen.findByText('입금 계좌가 발급되었어요')
  expect(completePayment).toHaveBeenCalledWith('payment-1')
  expect(getPendingPaymentRedirect()).toBeNull()
})

it('does not verify a return started in another tab', async () => {
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })

  render(
    <MemoryRouter initialEntries={['/payments/redirect?paymentId=payment-2']}>
      <PaymentRedirect />
    </MemoryRouter>,
  )

  await screen.findByText('이 브라우저에서 시작한 결제 정보를 확인할 수 없어요.')
  expect(completePayment).not.toHaveBeenCalled()
})

it('shows the PortOne cancellation without calling the completion API', async () => {
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })

  render(
    <MemoryRouter
      initialEntries={['/payments/redirect?paymentId=payment-1&code=PAY_PROCESS_CANCELED&message=사용자가 취소했어요']}
    >
      <PaymentRedirect />
    </MemoryRouter>,
  )

  await screen.findByText('사용자가 취소했어요')
  expect(completePayment).not.toHaveBeenCalled()
  expect(getPendingPaymentRedirect()).toBeNull()
})

it('lets the user retry a temporary completion failure', async () => {
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })
  completePayment
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ data: { data: { status: 'VIRTUAL_ACCOUNT_ISSUED' } } })

  render(
    <MemoryRouter initialEntries={['/payments/redirect?paymentId=payment-1']}>
      <PaymentRedirect />
    </MemoryRouter>,
  )

  await screen.findByText('결제 결과를 확인하지 못했어요')
  await userEvent.click(screen.getByRole('button', { name: '다시 확인하기' }))
  await screen.findByText('입금 계좌가 발급되었어요')
  await waitFor(() => expect(completePayment).toHaveBeenCalledTimes(2))
})

it('explains the automatic refund when the reservation was already finalized', async () => {
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })
  completePayment.mockRejectedValue({ response: { data: { errorCode: 'RESERVATION_ALREADY_FINALIZED' } } })

  render(
    <MemoryRouter initialEntries={['/payments/redirect?paymentId=payment-1']}>
      <PaymentRedirect />
    </MemoryRouter>,
  )

  await screen.findByText(/자동으로 취소돼 환불돼요/)
})
