import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HeroCarousel, { AUTO_ADVANCE_MS } from './HeroCarousel'

const slides = [
  { id: 1, title: '첫째', location: '서울', date: '2026.10.01.' },
  { id: 2, title: '둘째', location: '부산', date: '2026.10.02.' },
  { id: 3, title: '셋째', location: '대구', date: '2026.10.03.' },
]

function activeTitle() {
  return screen.getAllByRole('link', { hidden: true }).find((link) => link.getAttribute('aria-hidden') === 'false')
    ?.getAttribute('aria-label')
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

describe('HeroCarousel', () => {
  it('advances automatically and wraps around', () => {
    render(<MemoryRouter><HeroCarousel slides={slides} /></MemoryRouter>)
    expect(activeTitle()).toBe('첫째 상세 보기')
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS))
    expect(activeTitle()).toBe('둘째 상세 보기')
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS * 2))
    expect(activeTitle()).toBe('첫째 상세 보기')
  })

  it('restarts the timer after a manual move', () => {
    render(<MemoryRouter><HeroCarousel slides={slides} /></MemoryRouter>)
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS - 1000))
    act(() => screen.getByRole('button', { name: '다음 슬라이드' }).click())
    expect(activeTitle()).toBe('둘째 상세 보기')
    //수동으로 넘긴 직후 1초 뒤(원래 타이머 만료 시점)에는 넘어가지 않고, 새 간격이 다 차야 넘어간다
    act(() => vi.advanceTimersByTime(1000))
    expect(activeTitle()).toBe('둘째 상세 보기')
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS - 1000))
    expect(activeTitle()).toBe('셋째 상세 보기')
  })

  it('does not auto-advance with a single slide', () => {
    render(<MemoryRouter><HeroCarousel slides={slides.slice(0, 1)} /></MemoryRouter>)
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS * 3))
    expect(activeTitle()).toBe('첫째 상세 보기')
  })
})
