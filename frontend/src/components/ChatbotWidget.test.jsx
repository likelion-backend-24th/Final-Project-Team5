import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ChatbotWidget from './ChatbotWidget'
import { useAuth } from '../context/AuthContext.jsx'
import { requestRecommendations } from '../api/chatbotApi'
import { fetchMyActiveBoothWaitlists } from '../api/boothApi'
vi.mock('../context/AuthContext.jsx')
vi.mock('../api/chatbotApi')
vi.mock('../api/boothApi')

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  useAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, user: { id: 1 } })
  //대부분의 테스트는 부스 알림 폴링과 무관하므로 기본값은 빈 목록으로 둔다.
  fetchMyActiveBoothWaitlists.mockResolvedValue({ data: { data: [] } })
})

function renderWidget() {
  return render(<MemoryRouter><ChatbotWidget /></MemoryRouter>)
}

async function openRecommendPanel(user) {
  await user.click(screen.getByRole('button', { name: '챗봇 메뉴 열기' }))
  await user.click(screen.getByRole('button', { name: '추천 챗봇' }))
}

describe('ChatbotWidget', () => {
  it('opens the speed-dial menu, then opens/closes the recommend panel', async () => {
    const user = userEvent.setup()
    renderWidget()
    expect(screen.queryByRole('dialog')).toBeNull()

    await user.click(screen.getByRole('button', { name: '챗봇 메뉴 열기' }))
    expect(screen.getByRole('button', { name: '추천 챗봇' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '부스 알림' })).toBeTruthy()

    await user.click(screen.getByRole('button', { name: '추천 챗봇' }))
    expect(screen.getByRole('dialog', { name: 'AI 페스티벌 추천 챗봇' })).toBeTruthy()
    //패널이 열리면 미니 버튼은 접힌다
    expect(screen.queryByRole('button', { name: '추천 챗봇' })).toBeNull()

    await user.click(screen.getByRole('button', { name: '챗봇 패널 닫기' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('sends the message with history and renders reply and recommendation cards', async () => {
    requestRecommendations.mockResolvedValue({ data: { data: {
      reply: '서울 재즈 어떠세요?',
      recommendations: [{ festivalId: 7, name: '서울 재즈 페스티벌', link: '/festivals/7', reason: '서울이고 저렴해요' }],
    } } })
    const user = userEvent.setup()
    renderWidget()
    await openRecommendPanel(user)
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
    await openRecommendPanel(user)
    expect(screen.queryByLabelText('추천 요청 메시지')).toBeNull()
    expect(screen.getByRole('link', { name: '로그인하기' }).getAttribute('href')).toBe('/login')
  })

  it('shows a retry message when the service is unavailable', async () => {
    requestRecommendations.mockRejectedValue({ response: { status: 503, data: { errorCode: 'CHATBOT_UNAVAILABLE' } } })
    const user = userEvent.setup()
    renderWidget()
    await openRecommendPanel(user)
    await user.type(screen.getByLabelText('추천 요청 메시지'), '아무거나')
    await user.click(screen.getByRole('button', { name: '보내기' }))
    expect((await screen.findByRole('alert')).textContent).toContain('잠시 후 다시 시도')
  })

  it('shows an empty state in the booth alert feed when nothing has been called yet', async () => {
    const user = userEvent.setup()
    renderWidget()
    await user.click(screen.getByRole('button', { name: '챗봇 메뉴 열기' }))
    await user.click(screen.getByRole('button', { name: '부스 알림' }))
    expect(screen.getByRole('dialog', { name: '부스 대기 알림' })).toBeTruthy()
    expect(screen.getByText(/아직 알림이 없어요/)).toBeTruthy()
  })

  it('polls booth waitlists and shows a called-number alert with a link to the festival', async () => {
    fetchMyActiveBoothWaitlists.mockResolvedValue({
      data: { data: [{ boothId: 1, festivalId: 9, queueNumber: 3, calledNumber: 3, myTurn: true }] },
    })
    const user = userEvent.setup()
    renderWidget()

    await user.click(screen.getByRole('button', { name: '챗봇 메뉴 열기' }))
    await user.click(screen.getByRole('button', { name: '부스 알림' }))
    await screen.findByText(/대기번호 3번/)
    const link = screen.getByRole('link', { name: '부스로 이동하기 →' })
    expect(link.getAttribute('href')).toBe('/festivals/9')
  })

  it('does not re-show the unread badge for an already-seen alert after remounting (e.g. page refresh)', async () => {
    fetchMyActiveBoothWaitlists.mockResolvedValue({
      data: { data: [{ boothId: 1, festivalId: 9, queueNumber: 3, calledNumber: 3, myTurn: true }] },
    })

    const first = renderWidget()
    await waitFor(() => expect(first.container.querySelector('.bg-red-500')).toBeTruthy())
    first.unmount()

    //새로고침을 흉내낸 재마운트 — 백엔드는 여전히 같은 항목을 myTurn:true로 내려주지만
    //localStorage에 남은 "이미 확인함" 기록 덕분에 안 읽음 배지가 다시 뜨면 안 된다.
    const second = renderWidget()
    await waitFor(() => expect(fetchMyActiveBoothWaitlists).toHaveBeenCalledTimes(2))
    expect(second.container.querySelector('.bg-red-500')).toBeNull()
  })
})
