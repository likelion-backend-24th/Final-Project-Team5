import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/adminApi', () => ({
  fetchPendingHostApplications: vi.fn(),
  reviewHostApplication: vi.fn(),
  fetchPendingFestivals: vi.fn(),
  reviewFestival: vi.fn(),
}))

import { fetchPendingFestivals } from '../api/adminApi'
import { fetchFestivalSubmissions } from './admin'

describe('fetchFestivalSubmissions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('이미지가 없는 행사에 실제 존재하는 기본 이미지를 사용한다', async () => {
    fetchPendingFestivals.mockResolvedValue({ data: { data: [{ id: 1, thumbnailImageUrl: null }] } })

    const festivals = await fetchFestivalSubmissions()

    expect(festivals[0].image).toBe('/placeholder.jpg')
  })

  it('취소 진행과 취소 완료 행사는 공개 승인 이력으로 표시한다', async () => {
    fetchPendingFestivals.mockResolvedValue({
      data: {
        data: [
          { id: 25, festivalStatus: 'CANCELLATION_PENDING', ticketTypes: [] },
          { id: 31, festivalStatus: 'CANCELLED', ticketTypes: [] },
        ],
      },
    })

    const festivals = await fetchFestivalSubmissions()

    expect(festivals.map((festival) => festival.status)).toEqual(['APPROVED', 'APPROVED'])
  })
})
