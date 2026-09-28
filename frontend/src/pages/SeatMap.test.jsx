import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import SeatMap from './SeatMap'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchFestivalDetail } from '../api/festivalApi'
import { fetchSeats } from '../api/seatApi'
vi.mock('../context/AuthContext.jsx')
vi.mock('../api/festivalApi')
vi.mock('../api/seatApi')

//STOMP 클라이언트는 연결 즉시 onConnect를 부르고, 구독 콜백을 잡아 두어 테스트가 좌석 상태 메시지를 흘려보낼 수 있게 한다.
const stomp = vi.hoisted(() => ({ onSeatMessage: null }))
vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(config) {
      this.config = config
    }
    activate() {
      this.config.onConnect()
    }
    subscribe(_destination, callback) {
      stomp.onSeatMessage = callback
    }
    deactivate() {}
  },
}))

const SEAT_ID = 1

beforeEach(() => {
  vi.clearAllMocks()
  stomp.onSeatMessage = null
  vi.spyOn(console, 'log').mockImplementation(() => {})
  useAuth.mockReturnValue({ user: { id: 1 }, isLoading: false })
  fetchFestivalDetail.mockResolvedValue({
    data: { data: { ticketTypes: [{ id: 272, name: 'R석 2일권', price: 132000, seatLayout: null }] } },
  })
  fetchSeats.mockResolvedValue({
    data: {
      data: [
        { id: SEAT_ID, zone: 'R', rowLabel: '2열', seatNumber: 5, seatStatus: 'AVAILABLE' },
        { id: 2, zone: 'R', rowLabel: '2열', seatNumber: 6, seatStatus: 'AVAILABLE' },
      ],
    },
  })
})

function renderSeatMap() {
  return render(
    <MemoryRouter initialEntries={['/festivals/196/seats?ticketTypeId=272']}>
      <Routes>
        <Route path="/festivals/:id/seats" element={<SeatMap />} />
      </Routes>
    </MemoryRouter>,
  )
}

function sendSeatStatus(status) {
  act(() => stomp.onSeatMessage({ body: JSON.stringify({ seatId: SEAT_ID, status }) }))
}

describe('SeatMap', () => {
  it('releases my selection and shows a notice when another user holds the seat', async () => {
    const user = userEvent.setup()
    renderSeatMap()
    const seat = await screen.findByRole('button', { name: 'R 2열 5번' })
    await user.click(seat)
    expect(seat.getAttribute('aria-pressed')).toBe('true')

    sendSeatStatus('HELD')

    expect(screen.getByRole('alert').textContent).toContain('다른 사용자에게 선점되어')
    expect(seat.getAttribute('aria-pressed')).toBe('false')
    expect(seat.disabled).toBe(true)
  })

  it('clears the held notice once I pick a seat again after it is released', async () => {
    const user = userEvent.setup()
    renderSeatMap()
    const seat = await screen.findByRole('button', { name: 'R 2열 5번' })
    await user.click(seat)
    sendSeatStatus('HELD')
    expect(screen.getByRole('alert')).toBeTruthy()

    //상대가 예매를 취소해 좌석이 다시 풀린 뒤 같은 좌석을 다시 고른다.
    sendSeatStatus('AVAILABLE')
    await user.click(seat)

    expect(seat.getAttribute('aria-pressed')).toBe('true')
    expect(screen.queryByRole('alert')).toBeNull()
  })
})
