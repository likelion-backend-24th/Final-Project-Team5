import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Festivals from './Festivals'
import { fetchFestivals } from '../api/festivalApi'

vi.mock('../api/festivalApi', async (importOriginal) => ({
  ...(await importOriginal()),
  fetchFestivals: vi.fn(),
  prefetchFestivalDetail: vi.fn(),
}))

const festival = (id, name) => ({
  id,
  name,
  festivalCategory: 'MUSIC',
  region: 'SEOUL',
  locationDetail: '서울숲',
  startAt: '2030-10-01T10:00:00',
  endAt: '2030-10-02T22:00:00',
  ticketTypes: [{ price: 30000 }],
  thumbnailImageUrl: null,
  festivalStatus: 'PUBLISHED',
})

function renderPage() {
  return render(
    <MemoryRouter>
      <Festivals />
    </MemoryRouter>,
  )
}

describe('Festivals', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows a skeleton while loading, then the festival cards', async () => {
    fetchFestivals.mockResolvedValue({ data: { data: [festival(1, '서울 재즈 페스티벌')] } })
    renderPage()

    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeTruthy()
    expect(await screen.findByText('서울 재즈 페스티벌')).toBeTruthy()
    expect(screen.queryByRole('status', { name: '불러오는 중' })).toBeNull()
  })

  it('renders straight from the cache on a revisit without a loading skeleton', async () => {
    fetchFestivals.mockResolvedValue({ data: { data: [festival(1, '서울 재즈 페스티벌')] } })
    const first = renderPage()
    await screen.findByText('서울 재즈 페스티벌')
    first.unmount()

    renderPage()
    expect(screen.queryByRole('status', { name: '불러오는 중' })).toBeNull()
    expect(screen.getByText('서울 재즈 페스티벌')).toBeTruthy()
    expect(fetchFestivals).toHaveBeenCalledTimes(1)
  })

  it('shows an error message when the list cannot be loaded', async () => {
    fetchFestivals.mockRejectedValue(new Error('network'))
    renderPage()
    expect(await screen.findByText(/페스티벌 목록을 불러오지 못했어요/)).toBeTruthy()
  })
})
