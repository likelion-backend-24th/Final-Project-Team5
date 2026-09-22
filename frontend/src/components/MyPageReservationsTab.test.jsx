import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { fetchFestivalDetail } from '../api/festivalApi'
import { fetchMyReservations, fetchReservationQr } from '../api/reservationApi'
import MyPageReservationsTab from './MyPageReservationsTab'

vi.mock('../api/festivalApi', { spy: true })
vi.mock('../api/reservationApi')

const qrResponse = {
  qrToken: 'local-ticket-test-token', checkInCode: '12345678', checkedInAt: null,
  qrImageUrl: 'https://api.qrserver.com/v1/create-qr-code/?data=local-ticket-test-token',
}

beforeEach(() => {
  vi.clearAllMocks()
  fetchMyReservations.mockResolvedValue({ data: { data: [{
    id: 1, festivalId: 7, ticketTypeId: 2, quantity: 1, reservationStatus: 'CONFIRMED',
    createdAt: '2026-09-21T10:00:00',
  }] } })
  fetchFestivalDetail.mockResolvedValue({ data: { data: {
    name: '테스트 행사', startAt: '2099-10-01', endAt: '2099-10-02', ticketTypes: [],
  } } })
  fetchReservationQr.mockResolvedValue({ data: { data: qrResponse } })
})

async function openQr() {
  const user = userEvent.setup()
  const view = render(<MemoryRouter><MyPageReservationsTab /></MemoryRouter>)
  await user.click(await screen.findByRole('button', { name: 'QR 보기' }))
  return view
}

it('renders the ticket as a local SVG without loading the external QR image', async () => {
  const { container } = await openQr()
  expect((await screen.findByRole('img', { name: '입장용 QR 코드' })).tagName.toLowerCase()).toBe('svg')
  expect(container.querySelector('img[src*="qrserver.com"]')).toBeNull()
  expect(screen.getByText('12345678')).toBeTruthy()
})

it('keeps the fixed used-ticket image after check-in', async () => {
  fetchReservationQr.mockResolvedValue({ data: { data: { ...qrResponse, checkedInAt: '2026-09-21T10:00:00Z' } } })
  await openQr()
  expect((await screen.findByRole('img', { name: '입장 완료된 티켓' })).getAttribute('src')).toMatch(/^data:image\/svg/)
  expect(screen.queryByRole('img', { name: '입장용 QR 코드' })).toBeNull()
})

it.each([
  ['RESERVATION_NOT_CONFIRMED', '결제가 확정되고 남은 티켓이 있는 예매만 QR을 볼 수 있어요.'],
  ['RESERVATION_NOT_FOUND', '예매 정보를 찾을 수 없어요.'],
  ['FORBIDDEN_NOT_OWNER', '본인의 예매만 QR을 볼 수 있어요.'],
  ['UNKNOWN', 'QR을 불러오지 못했어요. 잠시 후 다시 시도해주세요.'],
])('explains a QR failure: %s', async (errorCode, message) => {
  fetchReservationQr.mockRejectedValue({ response: { data: { errorCode } } })
  await openQr()
  expect(await screen.findByText(message)).toBeTruthy()
  expect(screen.queryByRole('img', { name: '입장용 QR 코드' })).toBeNull()
})
