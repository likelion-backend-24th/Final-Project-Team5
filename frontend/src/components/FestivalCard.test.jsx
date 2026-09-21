import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import FestivalCard from './FestivalCard'
import { prefetchFestivalDetail } from '../api/festivalApi'

vi.mock('../api/festivalApi', () => ({ prefetchFestivalDetail: vi.fn() }))

const festival = {
  id: 3,
  title: '부산 록 페스티벌',
  category: 'MUSIC',
  location: '부산 해운대',
  date: '2030.10.01',
  price: '30,000원~',
  image: '/thumb.jpg',
}

function renderCard() {
  return render(
    <MemoryRouter>
      <FestivalCard festival={festival} categoryLabel="음악" />
    </MemoryRouter>,
  )
}

describe('FestivalCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('prefetches the detail only on pointerdown, not on hover (the detail GET counts a view)', () => {
    renderCard()
    const link = screen.getByRole('link', { name: /부산 록 페스티벌/ })

    fireEvent.mouseEnter(link)
    fireEvent.pointerOver(link)
    expect(prefetchFestivalDetail).not.toHaveBeenCalled()

    fireEvent.pointerDown(link)
    expect(prefetchFestivalDetail).toHaveBeenCalledWith(3)
  })

  it('lazy-loads the thumbnail and fades it in once loaded', () => {
    renderCard()
    const img = screen.getByAltText('부산 록 페스티벌')
    expect(img.getAttribute('loading')).toBe('lazy')
    expect(img.className).toContain('opacity-0')
    fireEvent.load(img)
    expect(img.className).not.toContain('opacity-0')
  })
})
