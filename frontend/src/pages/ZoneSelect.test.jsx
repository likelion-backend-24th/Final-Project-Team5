import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import ZoneSelect from './ZoneSelect'
import { fetchFestivalDetail } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'

vi.mock('../context/AuthContext.jsx')
vi.mock('../api/festivalApi')

function Destination() {
  const location = useLocation()
  return <p>{location.state?.from?.pathname}</p>
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/festivals/7/zones']}>
      <Routes>
        <Route path="/festivals/:id/zones" element={<ZoneSelect />} />
        <Route path="/login" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ZoneSelect', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ user: { id: 1, role: 'USER' }, isLoading: false })
    fetchFestivalDetail.mockResolvedValue({ data: { data: {
      id: 7, name: '티켓 단위 확인', festivalStatus: 'PUBLISHED', stageLayout: 'FRONT_STAGE',
      ticketTypes: [
        { id: 1, name: '입장권', ticketMode: 'STANDING', remainQuantity: 3, price: 1000 },
        { id: 2, name: '좌석권', ticketMode: 'SEATED', remainQuantity: 2, price: 2000, positionRow: 0, positionCol: 0 },
      ],
    } } })
  })

  it('uses ticket counts for standing and seats for seated tickets', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: '티켓 종류' })).toBeTruthy()
    expect(screen.getByText('잔여 3장')).toBeTruthy()
    expect(screen.getByText('잔여 2석')).toBeTruthy()
    expect(screen.getByRole('spinbutton', { name: '입장권 수량' })).toBeTruthy()
  })

  it('draws the hovered center-stage zone outline last so neighbouring zones do not cover it', async () => {
    fetchFestivalDetail.mockResolvedValue({ data: { data: {
      id: 7, name: '중앙형 확인', festivalStatus: 'PUBLISHED', stageLayout: 'CENTER_STAGE',
      ticketTypes: ['북측', '동측', '남측', '서측'].map((name, index) => ({
        id: index + 1, name, ticketMode: 'SEATED', remainQuantity: 10, price: 1000, positionAngle: index * 90,
      })),
    } } })
    const user = userEvent.setup()
    renderPage()

    const south = await screen.findByRole('button', { name: '남측 선택' })
    const svg = south.closest('svg')
    expect(svg.querySelectorAll('path')).toHaveLength(4)

    //남측은 서측보다 먼저 그려져, 서측과 맞닿은 경계의 강조선이 가려졌었다.
    await user.hover(south)
    const outline = svg.lastElementChild
    expect(outline.getAttribute('d')).toBe(south.getAttribute('d'))
    expect(outline.getAttribute('aria-hidden')).toBe('true')

    await user.unhover(south)
    expect(svg.querySelectorAll('path')).toHaveLength(4)
  })

  it('preserves the zone selection destination when login is required', async () => {
    useAuth.mockReturnValue({ user: null, isLoading: false })
    renderPage()

    await userEvent.click(await screen.findByRole('link', { name: '로그인하러 가기' }))
    expect(await screen.findByText('/festivals/7/zones')).toBeTruthy()
  })
})
