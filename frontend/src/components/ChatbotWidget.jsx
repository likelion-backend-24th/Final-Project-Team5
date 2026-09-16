import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { MessageCircleIcon, SendIcon, SparklesIcon, XIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { requestRecommendations } from '../api/chatbotApi'

const GREETING = {
  id: 'greeting',
  role: 'assistant',
  content: '안녕하세요! 어떤 페스티벌을 찾으세요? 지역·날짜·예산·분위기를 말해주시면 맞는 걸 골라드릴게요.',
  //인사말은 서버로 보내는 대화 이력에서 뺀다
  greeting: true,
}

const ERROR_MESSAGES = {
  CHATBOT_UNAVAILABLE: '지금은 추천 서비스가 잠시 바빠요. 잠시 후 다시 시도해 주세요.',
  INVALID_REQUEST: '메시지는 500자 이내로 입력해 주세요.',
}

/**
 * 우하단 고정 AI 추천 챗봇. 대화는 이 컴포넌트 state에만 두고(새로고침하면 초기화) 최근 대화를 함께 보내
 * 후속 질문이 이어지게 한다. 메시지는 role(user/assistant)로만 구분해 그리므로, 나중에 부스 대기 순번 같은
 * 알림을 assistant 메시지로 밀어 넣는 식으로 같은 창을 재사용할 수 있다.
 */
function ChatbotWidget() {
  const { isAuthenticated, isLoading } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState([GREETING])
  const [input, setInput] = useState('')
  const [isSending, setIsSending] = useState(false)
  const listRef = useRef(null)

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages, isSending, isOpen])

  async function handleSubmit(event) {
    event.preventDefault()
    const message = input.trim()
    if (!message || isSending) return

    const history = messages
      .filter((item) => !item.greeting && !item.error)
      .map((item) => ({ role: item.role, content: item.content }))
    setMessages((prev) => [...prev, { id: `u-${Date.now()}`, role: 'user', content: message }])
    setInput('')
    setIsSending(true)
    try {
      const response = await requestRecommendations(message, history)
      const { reply, recommendations } = response.data.data
      setMessages((prev) => [...prev, { id: `a-${Date.now()}`, role: 'assistant', content: reply, recommendations }])
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      const content = error.response?.status === 401
        ? '로그인이 필요해요. 로그인 후 다시 시도해 주세요.'
        : ERROR_MESSAGES[errorCode] ?? '추천을 가져오지 못했어요. 잠시 후 다시 시도해 주세요.'
      setMessages((prev) => [...prev, { id: `e-${Date.now()}`, role: 'assistant', content, error: true }])
    } finally {
      setIsSending(false)
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label={isOpen ? '추천 챗봇 닫기' : '추천 챗봇 열기'}
        aria-expanded={isOpen}
        className="fixed bottom-6 right-6 z-50 flex h-14 w-14 cursor-pointer items-center justify-center rounded-full bg-blue-600 text-white shadow-lg transition hover:bg-blue-700"
      >
        {isOpen ? <XIcon className="h-6 w-6" /> : <MessageCircleIcon className="h-6 w-6" />}
      </button>

      {isOpen && (
        <section
          role="dialog"
          aria-label="AI 페스티벌 추천 챗봇"
          className="fixed bottom-24 right-6 z-50 flex h-[520px] max-h-[calc(100vh-7rem)] w-[360px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-3xl border border-gray-200 bg-white shadow-2xl"
        >
          <header className="flex items-center gap-2 border-b border-gray-100 bg-blue-600 px-4 py-3 text-white">
            <SparklesIcon className="h-5 w-5" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold">FevalGo 추천 도우미</p>
              <p className="text-xs text-blue-100">취향에 맞는 페스티벌을 찾아드려요</p>
            </div>
          </header>

          <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto bg-gray-50 px-4 py-4">
            {messages.map((item) => (
              <ChatMessage key={item.id} message={item} />
            ))}
            {isSending && (
              <div className="flex justify-start">
                <div className="rounded-2xl rounded-bl-md bg-white px-4 py-2.5 text-sm text-gray-500 shadow-sm" role="status">
                  추천을 찾고 있어요…
                </div>
              </div>
            )}
          </div>

          {!isLoading && !isAuthenticated ? (
            <div className="border-t border-gray-100 px-4 py-4 text-center text-sm text-gray-600">
              로그인 후 이용할 수 있어요.{' '}
              <Link to="/login" onClick={() => setIsOpen(false)} className="font-bold text-blue-600 hover:underline">
                로그인하기
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="flex items-center gap-2 border-t border-gray-100 px-3 py-3">
              <input
                type="text"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                maxLength={500}
                placeholder="예: 이번 주말 서울 재즈 페스티벌"
                aria-label="추천 요청 메시지"
                disabled={isSending}
                className="min-w-0 flex-1 rounded-full border border-gray-200 bg-white px-4 py-2.5 text-sm text-gray-900 outline-none transition placeholder:text-gray-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:bg-gray-50"
              />
              <button
                type="submit"
                aria-label="보내기"
                disabled={isSending || !input.trim()}
                className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full bg-blue-600 text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400"
              >
                <SendIcon className="h-4 w-4" />
              </button>
            </form>
          )}
        </section>
      )}
    </>
  )
}

function ChatMessage({ message }) {
  const isUser = message.role === 'user'
  return (
    <div className={isUser ? 'flex justify-end' : 'flex justify-start'}>
      <div className={isUser ? 'max-w-[85%]' : 'max-w-[92%] space-y-2'}>
        <div
          className={
            isUser
              ? 'whitespace-pre-wrap rounded-2xl rounded-br-md bg-blue-600 px-4 py-2.5 text-sm text-white'
              : message.error
                ? 'whitespace-pre-wrap rounded-2xl rounded-bl-md bg-red-50 px-4 py-2.5 text-sm text-red-700'
                : 'whitespace-pre-wrap rounded-2xl rounded-bl-md bg-white px-4 py-2.5 text-sm text-gray-800 shadow-sm'
          }
          role={message.error ? 'alert' : undefined}
        >
          {message.content}
        </div>
        {message.recommendations?.map((item) => (
          <Link
            key={item.festivalId}
            to={item.link}
            className="block rounded-2xl border border-blue-100 bg-white px-4 py-3 shadow-sm transition hover:border-blue-300 hover:bg-blue-50"
          >
            <p className="text-sm font-bold text-gray-900">{item.name}</p>
            <p className="mt-1 text-xs text-gray-600">{item.reason}</p>
            <p className="mt-1.5 text-xs font-bold text-blue-600">자세히 보기 →</p>
          </Link>
        ))}
      </div>
    </div>
  )
}

export default ChatbotWidget
