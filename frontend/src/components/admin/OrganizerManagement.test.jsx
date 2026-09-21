import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fetchFestivalSubmissions, fetchOrganizerApplications } from '../../data/admin'
import OrganizerManagement from './OrganizerManagement'

vi.mock('../../data/admin', { spy: true })

beforeEach(() => {
  vi.clearAllMocks()
  fetchOrganizerApplications.mockResolvedValue([])
})

it('shows nearby pages and ellipses instead of every page', async () => {
  fetchOrganizerApplications.mockResolvedValue(Array.from({ length: 300 }, (_, i) => ({
    id: i + 1, name: `신청자 ${i + 1}`, email: `host${i + 1}@example.com`,
    status: 'APPROVED', appliedAt: '2026.09.21',
  })))
  const user = userEvent.setup()
  render(<OrganizerManagement />)

  const first = await screen.findByRole('button', { name: '1', exact: true })
  expect(first.getAttribute('aria-current')).toBe('page')
  expect(first.className).toContain('h-11 w-11')
  expect(screen.queryByRole('button', { name: '100', exact: true })).toBeNull()
  expect(screen.getByRole('button', { name: '이전 페이지' }).disabled).toBe(true)
  await user.click(screen.getByRole('button', { name: '3', exact: true }))
  await user.click(screen.getByRole('button', { name: '5', exact: true }))
  expect(screen.queryByRole('button', { name: '2', exact: true })).toBeNull()
  expect(screen.getByRole('button', { name: '7', exact: true })).toBeTruthy()
  expect(screen.getAllByText('…')).toHaveLength(2)
  await user.click(screen.getByRole('button', { name: '다음 페이지' }))
  expect(screen.getByRole('button', { name: '6', exact: true }).getAttribute('aria-current')).toBe('page')
})

it('uses the existing fallback image for a festival without an image', async () => {
  fetchFestivalSubmissions.mockResolvedValue([{
    id: 1, name: '이미지 없는 행사', host: '주최자', image: '', status: 'APPROVED',
    appliedAt: '2026.09.21', category: '음악', description: '', tickets: [],
  }])
  const user = userEvent.setup()
  render(<OrganizerManagement />)
  await user.click(screen.getByRole('button', { name: '페스티벌 등록 승인' }))
  expect((await screen.findByRole('img', { name: '이미지 없는 행사' })).getAttribute('src')).toBe('/placeholder.jpg')
})
