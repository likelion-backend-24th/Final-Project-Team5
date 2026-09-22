import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Home from './Home'
import { fetchFestivals } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'

vi.mock('../context/AuthContext.jsx')
vi.mock('../api/festivalApi', async (importOriginal) => ({
  ...(await importOriginal()),
  fetchFestivals: vi.fn(),
}))

describe('Home', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ user: null })
  })

  it('distinguishes an API failure from an empty category', async () => {
    fetchFestivals.mockRejectedValue(new Error('gateway unavailable'))
    render(<MemoryRouter><Home /></MemoryRouter>)

    expect(await screen.findByText('페스티벌 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')).toBeTruthy()
    expect(screen.queryByText(/해당 카테고리에 등록된 페스티벌/)).toBeNull()
  })

  it('keeps the empty category notice for a successful empty response', async () => {
    fetchFestivals.mockResolvedValue({ data: { data: [] } })
    render(<MemoryRouter><Home /></MemoryRouter>)

    expect(await screen.findByText('해당 카테고리에 등록된 페스티벌이 없어요.')).toBeTruthy()
    expect(screen.queryByText(/목록을 불러오지 못했어요/)).toBeNull()
  })
})
