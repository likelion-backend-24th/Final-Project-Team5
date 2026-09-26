import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { fetchFestivalSubmissions } from '../../data/admin'
import FestivalManagement from './FestivalManagement'

vi.mock('../../data/admin', { spy: true })

beforeEach(() => {
  vi.clearAllMocks()
})

it('uses the existing fallback image for a festival without an image', async () => {
  fetchFestivalSubmissions.mockResolvedValue([{
    id: 1, name: '이미지 없는 행사', host: '주최자', image: '', status: 'APPROVED',
    appliedAt: '2026.09.21', category: '음악', description: '', tickets: [],
  }])
  render(<FestivalManagement />)
  expect((await screen.findByRole('img', { name: '이미지 없는 행사' })).getAttribute('src')).toBe('/placeholder.jpg')
})
