import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/adminApi', () => ({
  fetchAdminHosts: vi.fn(),
  fetchPendingHostApplications: vi.fn(),
  reviewHostApplication: vi.fn(),
  fetchPendingFestivals: vi.fn(),
  reviewFestival: vi.fn(),
}))

import { fetchAdminHosts, fetchPendingFestivals } from '../api/adminApi'
import { fetchFestivalSubmissions, fetchOrganizers } from './admin'

describe('fetchOrganizers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('이미지가 없는 행사에 실제 존재하는 기본 이미지를 사용한다', async () => {
    fetchPendingFestivals.mockResolvedValue({ data: { data: [{ id: 1, thumbnailImageUrl: null }] } })

    const festivals = await fetchFestivalSubmissions()

    expect(festivals[0].image).toBe('/placeholder.jpg')
  })

  it('실제 HOST 계정과 페스티벌을 사용자 ID로 집계한다', async () => {
    fetchAdminHosts.mockResolvedValue({
      data: {
        data: [
          {
            id: 36,
            nickname: 'feval-host-01',
            email: 'host01@fevalgo.test',
            accountStatus: 'ACTIVE',
            joinedAt: '2026-09-15T18:07:00',
          },
          {
            id: 37,
            nickname: 'feval-host-02',
            email: 'host02@fevalgo.test',
            accountStatus: 'ACTIVE',
            joinedAt: '2026-09-15T18:07:00',
          },
        ],
      },
    })
    fetchPendingFestivals.mockResolvedValue({
      data: { data: [{ hostUserId: 36 }, { hostUserId: 36 }, { hostUserId: 37 }] },
    })

    await expect(fetchOrganizers()).resolves.toEqual([
      {
        id: '36',
        nickname: 'feval-host-01',
        email: 'host01@fevalgo.test',
        accountStatus: 'ACTIVE',
        joinedAt: '2026.09.15',
        festivalCount: 2,
      },
      {
        id: '37',
        nickname: 'feval-host-02',
        email: 'host02@fevalgo.test',
        accountStatus: 'ACTIVE',
        joinedAt: '2026.09.15',
        festivalCount: 1,
      },
    ])
  })

  it('운영자 권한 오류를 사용자에게 안내한다', async () => {
    fetchAdminHosts.mockRejectedValue({
      response: { data: { errorCode: 'FORBIDDEN_ADMIN_ROLE' } },
    })
    fetchPendingFestivals.mockResolvedValue({ data: { data: [] } })

    await expect(fetchOrganizers()).rejects.toThrow('운영자 권한이 없습니다.')
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
