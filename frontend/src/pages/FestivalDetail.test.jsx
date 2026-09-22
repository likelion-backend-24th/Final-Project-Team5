import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import FestivalDetail from './FestivalDetail'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchFestivalDetail } from '../api/festivalApi'

vi.mock('../context/AuthContext.jsx')
vi.mock('../api/boothApi')
vi.mock('../api/festivalApi', async (importOriginal) => ({
  ...(await importOriginal()),
  fetchFestivalDetail: vi.fn(),
}))

const detail = {
  id: 7,
  name: '서울 재즈 페스티벌',
  description: '재즈 소개',
  region: 'SEOUL',
  locationDetail: '서울숲',
  festivalCategory: 'MUSIC',
  festivalStatus: 'PUBLISHED',
  startAt: '2030-10-01T10:00:00',
  endAt: '2030-10-02T22:00:00',
  ticketTypes: [],
  detailImageUrls: [],
  thumbnailImageUrl: null,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/festivals/7']}>
      <Routes>
        <Route path="/festivals/:id" element={<FestivalDetail />} />
      </Routes>
    </MemoryRouter>,
  )
}

function LoginDestination() {
  const location = useLocation()
  return <p>{location.state?.from?.pathname}</p>
}

describe('FestivalDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ user: null })
  })

  it('shows upcoming before the published festival starts', async () => {
    fetchFestivalDetail.mockResolvedValue({ data: { data: detail } })
    renderPage()
    expect(await screen.findByText('진행 예정')).toBeTruthy()
    expect(screen.queryByText('진행중')).toBeNull()
  })

  it('passes the zone selection destination when booking requires login', async () => {
    fetchFestivalDetail.mockResolvedValue({ data: { data: detail } })
    render(
      <MemoryRouter initialEntries={['/festivals/7']}>
        <Routes>
          <Route path="/festivals/:id" element={<FestivalDetail />} />
          <Route path="/login" element={<LoginDestination />} />
        </Routes>
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /예매하기/ }))
    expect(await screen.findByText('/festivals/7/zones')).toBeTruthy()
  })

  it('shows a skeleton first and then the festival', async () => {
    fetchFestivalDetail.mockResolvedValue({ data: { data: detail } })
    renderPage()

    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeTruthy()
    expect(await screen.findByRole('heading', { name: '서울 재즈 페스티벌' })).toBeTruthy()
    expect(screen.queryByRole('status', { name: '불러오는 중' })).toBeNull()
  })

  it('opens instantly from a warm cache (e.g. after the card pointerdown prefetch)', async () => {
    fetchFestivalDetail.mockResolvedValue({ data: { data: detail } })
    const first = renderPage()
    await screen.findByRole('heading', { name: '서울 재즈 페스티벌' })
    first.unmount()

    renderPage()
    expect(screen.queryByRole('status', { name: '불러오는 중' })).toBeNull()
    expect(screen.getByRole('heading', { name: '서울 재즈 페스티벌' })).toBeTruthy()
    expect(fetchFestivalDetail).toHaveBeenCalledTimes(1)
  })

  it('shows the not-found state for a 404', async () => {
    fetchFestivalDetail.mockRejectedValue({ response: { status: 404 } })
    renderPage()
    expect(await screen.findByText('존재하지 않는 페스티벌이에요')).toBeTruthy()
  })

  it('shows a retry message for other failures', async () => {
    fetchFestivalDetail.mockRejectedValue({ response: { status: 500 } })
    renderPage()
    expect(await screen.findByText(/페스티벌 정보를 불러오지 못했어요/)).toBeTruthy()
  })
})
