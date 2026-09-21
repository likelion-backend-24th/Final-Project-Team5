import { afterEach, describe, expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import FadeImage from './FadeImage'

describe('FadeImage', () => {
  afterEach(() => {
    delete HTMLImageElement.prototype.complete
    delete HTMLImageElement.prototype.naturalWidth
  })

  it('is hidden until the image loads, then fades in', () => {
    render(<FadeImage src="/a.jpg" alt="포스터" className="h-10" />)
    const img = screen.getByAltText('포스터')
    expect(img.className).toContain('opacity-0')
    expect(img.getAttribute('loading')).toBe('lazy')
    expect(img.getAttribute('decoding')).toBe('async')

    fireEvent.load(img)
    expect(img.className).not.toContain('opacity-0')
    expect(img.className).toContain('animate-fade-in')
    expect(img.className).toContain('h-10')
  })

  it('shows an already-cached image immediately without replaying the fade', () => {
    Object.defineProperty(HTMLImageElement.prototype, 'complete', { configurable: true, get: () => true })
    Object.defineProperty(HTMLImageElement.prototype, 'naturalWidth', { configurable: true, get: () => 100 })
    render(<FadeImage src="/cached.jpg" alt="캐시됨" />)
    const img = screen.getByAltText('캐시됨')
    expect(img.className).not.toContain('opacity-0')
    expect(img.className).not.toContain('animate-fade-in')
  })

  it('reveals the element on error so the alt text is not invisible', () => {
    render(<FadeImage src="/broken.jpg" alt="깨짐" />)
    const img = screen.getByAltText('깨짐')
    fireEvent.error(img)
    expect(img.className).not.toContain('opacity-0')
  })

  it('starts hidden again when src changes', () => {
    const { rerender } = render(<FadeImage src="/a.jpg" alt="이미지" />)
    const img = screen.getByAltText('이미지')
    fireEvent.load(img)
    expect(img.className).not.toContain('opacity-0')

    rerender(<FadeImage src="/b.jpg" alt="이미지" />)
    expect(screen.getByAltText('이미지').className).toContain('opacity-0')
  })

  it('lets callers opt into eager loading with fetch priority', () => {
    render(<FadeImage src="/hero.jpg" alt="히어로" loading="eager" fetchPriority="high" />)
    const img = screen.getByAltText('히어로')
    expect(img.getAttribute('loading')).toBe('eager')
    expect(img.getAttribute('fetchpriority')).toBe('high')
  })
})
