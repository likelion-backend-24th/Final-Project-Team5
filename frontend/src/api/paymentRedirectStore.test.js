import { afterEach, beforeEach, expect, it } from 'vitest'
import {
  clearPendingPaymentRedirect,
  getPaymentRedirectUrl,
  getPendingPaymentRedirect,
  isExpectedPaymentRedirectReturn,
  savePendingPaymentRedirect,
} from './paymentRedirectStore'
import { wasSessionContinuous } from './tokenStore'

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  window.history.replaceState(null, '', '/')
})

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  window.history.replaceState(null, '', '/')
})

it('keeps the session only for the matching mobile payment return', () => {
  localStorage.setItem('tabHeartbeat', String(Date.now() - 60_000))
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })
  window.history.replaceState(null, '', '/payments/redirect?paymentId=payment-1')

  expect(isExpectedPaymentRedirectReturn()).toBe(true)
  expect(wasSessionContinuous()).toBe(true)
  expect(getPendingPaymentRedirect()).toMatchObject({ paymentId: 'payment-1', reservationId: 7 })
})

it('does not keep the session for a different payment id', () => {
  localStorage.setItem('tabHeartbeat', String(Date.now() - 60_000))
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })
  window.history.replaceState(null, '', '/payments/redirect?paymentId=payment-2')

  expect(isExpectedPaymentRedirectReturn()).toBe(false)
  expect(wasSessionContinuous()).toBe(false)
})

it('builds the same-origin redirect URL and clears only the expected payment', () => {
  savePendingPaymentRedirect({ paymentId: 'payment-1', reservationId: 7 })

  expect(getPaymentRedirectUrl()).toBe(`${window.location.origin}/payments/redirect`)
  clearPendingPaymentRedirect('payment-2')
  expect(getPendingPaymentRedirect()?.paymentId).toBe('payment-1')
  clearPendingPaymentRedirect('payment-1')
  expect(getPendingPaymentRedirect()).toBeNull()
})
