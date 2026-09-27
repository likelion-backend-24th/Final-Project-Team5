import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/adminApi', () => ({
  fetchPendingHostApplications: vi.fn(),
  reviewHostApplication: vi.fn(),
  fetchPendingFestivals: vi.fn(),
  fetchFestivalHostCounts: vi.fn(),
  fetchFestivalOperations: vi.fn(),
  reviewFestival: vi.fn(),
  fetchCancellationRequests: vi.fn(),
}))

import { fetchPendingFestivals } from '../api/adminApi'
import { fetchFestivalSubmissionsPage } from './admin'

const EMPTY_PAGINATION = { page: 0, size: 5, totalItems: 0, totalPages: 1, hasNext: false, hasPrev: false }

describe('fetchFestivalSubmissionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('이미지가 없는 행사에 실제 존재하는 기본 이미지를 사용한다', async () => {
    fetchPendingFestivals.mockResolvedValue({
      data: { data: [{ id: 1, thumbnailImageUrl: null }], meta: { pagination: EMPTY_PAGINATION } },
    })

    const { items } = await fetchFestivalSubmissionsPage({ status: 'ALL', page: 0, size: 5 })

    expect(items[0].image).toBe('/placeholder.jpg')
  })

  it('취소 진행과 취소 완료 행사는 공개 승인 이력으로 표시한다', async () => {
    fetchPendingFestivals.mockResolvedValue({
      data: {
        data: [
          { id: 25, festivalStatus: 'CANCELLATION_PENDING', ticketTypes: [] },
          { id: 31, festivalStatus: 'CANCELLED', ticketTypes: [] },
        ],
        meta: { pagination: EMPTY_PAGINATION },
      },
    })

    const { items } = await fetchFestivalSubmissionsPage({ status: 'ALL', page: 0, size: 5 })

    expect(items.map((festival) => festival.status)).toEqual(['APPROVED', 'APPROVED'])
  })

  it('서버 페이지네이션 메타를 그대로 반환한다', async () => {
    const pagination = { page: 1, size: 5, totalItems: 12, totalPages: 3, hasNext: true, hasPrev: true }
    fetchPendingFestivals.mockResolvedValue({ data: { data: [], meta: { pagination } } })

    const result = await fetchFestivalSubmissionsPage({ status: 'PENDING', page: 1, size: 5 })

    expect(result.pagination).toEqual(pagination)
  })
})
