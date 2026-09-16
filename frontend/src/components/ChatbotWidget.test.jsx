import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ChatbotWidget from './ChatbotWidget'
import { useAuth } from '../context/AuthContext.jsx'
import { requestRecommendations } from '../api/chatbotApi'
vi.mock('../context/AuthContext.jsx')
vi.mock('../api/chatbotApi')

beforeEach(() => {
  vi.clearAllMocks()
  useAuth.mockReturnValue({ isAuthenticated: true, isLoading: false })
})

function renderWidget() {
  return render(<MemoryRouter><ChatbotWidget /></MemoryRouter>)
}

describe('ChatbotWidget', () => {
  it('opens and closes the panel from the floating button', async () => {
    const user = userEvent.setup()
    renderWidget()
    expect(screen.queryByRole('dialog')).toBeNull()
    await user.click(screen.getByRole('button', { name: '추천 챗봇 열기' }))
    expect(screen.getByRole('dialog', { name: 'AI 페스티벌 추천 챗봇' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '추천 챗봇 닫기' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('sends the message with history and renders reply and recommendation cards', async () => {
    requestRecommendations.mockResolvedValue({ data: { data: {
      reply: '서울 재즈 어떠세요?',
      recommendations: [{ festivalId: 7, name: '서울 재즈 페스티벌', link: '/festivals/7', reason: '서울이고 저렴해요' }],
    } } })
    const user = userEvent.setup()
    renderWidget()
    await user.click(screen.getByRole('button', { name: '추천 챗봇 열기' }))
    await user.type(screen.getByLabelText('추천 요청 메시지'), '서울 재즈')
    await user.click(screen.getByRole('button', { name: '보내기' }))

    await screen.findByText('서울 재즈 어떠세요?')
    //첫 요청이라 인사말은 빠지고 history는 비어 있다
    expect(requestRecommendations).toHaveBeenCalledWith('서울 재즈', [])
    const card = screen.getByRole('link', { name: /서울 재즈 페스티벌/ })
    expect(card.getAttribute('href')).toBe('/festivals/7')
    expect(card.textContent).toContain('서울이고 저렴해요')

    //후속 질문에는 직전 대화가 history로 실린다
    await user.type(screen.getByLabelText('추천 요청 메시지'), '더 싼 건?')
    await user.click(screen.getByRole('button', { name: '보내기' }))
    await waitFor(() => expect(requestRecommendations).toHaveBeenCalledTimes(2))
    expect(requestRecommendations.mock.calls[1][1]).toEqual([
      { role: 'user', content: '서울 재즈' },
      { role: 'assistant', content: '서울 재즈 어떠세요?' },
    ])
  })

  it('shows a login prompt instead of the input when logged out', async () => {
    useAuth.mockReturnValue({ isAuthenticated: false, isLoading: false })
    const user = userEvent.setup()
    renderWidget()
    await user.click(screen.getByRole('button', { name: '추천 챗봇 열기' }))
    expect(screen.queryByLabelText('추천 요청 메시지')).toBeNull()
    expect(screen.getByRole('link', { name: '로그인하기' }).getAttribute('href')).toBe('/login')
  })

  it('shows a retry message when the service is unavailable', async () => {
    requestRecommendations.mockRejectedValue({ response: { status: 503, data: { errorCode: 'CHATBOT_UNAVAILABLE' } } })
    const user = userEvent.setup()
    renderWidget()
    await user.click(screen.getByRole('button', { name: '추천 챗봇 열기' }))
    await user.type(screen.getByLabelText('추천 요청 메시지'), '아무거나')
    await user.click(screen.getByRole('button', { name: '보내기' }))
    expect((await screen.findByRole('alert')).textContent).toContain('잠시 후 다시 시도')
  })
})
