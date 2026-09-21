import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { MegaphoneIcon, MessageCircleIcon, SendIcon, SparklesIcon, XIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { requestRecommendations } from '../api/chatbotApi'
import { fetchMyActiveBoothWaitlists } from '../api/boothApi'

const GREETING = {
  id: 'greeting',
  role: 'assistant',
  content: '안녕하세요! 어떤 페스티벌을 찾으세요? 지역·날짜·예산·분위기를 말해주시면 마음에 드실 만한 페스티벌을 골라드릴게요.',
  //인사말은 서버로 보내는 대화 이력에서 뺀다
  greeting: true,
}

const ERROR_MESSAGES = {
  CHATBOT_UNAVAILABLE: '지금은 추천 서비스가 잠시 바빠요. 잠시 후 다시 시도해 주세요.',
  INVALID_REQUEST: '메시지는 500자 이내로 입력해 주세요.',
}

//부스 대기 호출 알림 폴링 주기 — 마이페이지 예약 탭 자동 갱신과 같은 주기로 맞춘다.
const BOOTH_ALERT_POLL_INTERVAL_MS = 15_000

//백엔드에는 "이미 확인함" 개념이 없어(호출된 대기 항목은 myTurn=true로 영구히 남는다), 새로고침해도
//같은 알림을 또 새 알림으로 착각하지 않도록 "이미 알림을 보낸 boothId"를 로그인 계정별로 브라우저에 남겨둔다.
function notifiedBoothIdsStorageKey(userId) {
  return `fevalgo:notified-booth-alerts:${userId ?? 'anonymous'}`
}

function loadNotifiedBoothIds(storageKey) {
  try {
    const raw = localStorage.getItem(storageKey)
    return raw ? new Set(JSON.parse(raw)) : new Set()
  } catch {
    // 프라이빗 모드 등으로 localStorage를 못 쓰면 메모리 상태로만 동작한다.
    return new Set()
  }
}

function saveNotifiedBoothIds(storageKey, boothIds) {
  try {
    localStorage.setItem(storageKey, JSON.stringify([...boothIds]))
  } catch {
    // 저장 실패는 조용히 무시 — 다음 폴링에서 다시 시도된다.
  }
}

/**
 * 우하단 고정 챗봇 FAB. 평소엔 버튼 1개만 보이다가 누르면 "추천 챗봇"·"부스 알림" 미니 버튼
 * 2개로 펼쳐지는 스피드다이얼이다. 두 기능은 패널과 데이터를 완전히 분리한다 —
 * 추천 대화(chatMessages)와 부스 대기 호출 알림 피드(boothAlerts)는 서로 섞이지 않는다.
 * 대화·알림 피드는 이 컴포넌트 state에만 두고 서버에는 저장하지 않는다(새로고침하면 초기화).
 * 단, "이미 알림을 보낸 boothId" 자체는 localStorage에 남겨서, 새로고침해도 이미 확인한 호출을
 * 또 새 알림으로 착각해 안 읽음 배지를 다시 띄우지 않게 한다(백엔드에 확인 처리 개념이 없어서다).
 */
function ChatbotWidget() {
  const { isAuthenticated, isLoading, user } = useAuth()
  const [dialOpen, setDialOpen] = useState(false)
  //null | 'recommend' | 'alerts'
  const [activePanel, setActivePanel] = useState(null)
  const [chatMessages, setChatMessages] = useState([GREETING])
  const [boothAlerts, setBoothAlerts] = useState([])
  const [input, setInput] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [hasUnreadAlert, setHasUnreadAlert] = useState(false)
  const listRef = useRef(null)
  //이미 알림을 보낸 boothId — setInterval 클로저 안에서도 최신 값을 보려고 ref로 둔다.
  const notifiedBoothIdsRef = useRef(new Set())
  //폴링 시점에 "부스 알림" 패널이 열려 있는지 확인하려고 ref로도 들고 있는다(effect 재시작 없이 최신값 참조).
  const activePanelRef = useRef(activePanel)

  useEffect(() => {
    activePanelRef.current = activePanel
  }, [activePanel])

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [chatMessages, boothAlerts, isSending, activePanel])

  //로그인 상태면 15초마다 내 부스 대기 현황을 폴링해, 새로 "내 차례"가 된 부스가 있으면
  //알림 피드에 밀어 넣는다(서버에는 저장하지 않음).
  useEffect(() => {
    if (!isAuthenticated) return

    const storageKey = notifiedBoothIdsStorageKey(user?.id)
    notifiedBoothIdsRef.current = loadNotifiedBoothIds(storageKey)

    let cancelled = false

    function poll() {
      fetchMyActiveBoothWaitlists()
        .then((response) => {
          if (cancelled) return
          const newlyCalled = response.data.data.filter(
            (item) => item.myTurn && !notifiedBoothIdsRef.current.has(item.boothId),
          )
          if (newlyCalled.length === 0) return

          newlyCalled.forEach((item) => notifiedBoothIdsRef.current.add(item.boothId))
          saveNotifiedBoothIds(storageKey, notifiedBoothIdsRef.current)
          setBoothAlerts((prev) => [
            ...prev,
            ...newlyCalled.map((item) => ({
              id: `booth-alert-${item.boothId}-${item.calledNumber}`,
              role: 'assistant',
              boothAlert: true,
              content: `대기번호 ${item.queueNumber}번, 지금 부스에 입장할 차례예요!`,
              link: `/festivals/${item.festivalId}`,
            })),
          ])
          //이미 "부스 알림" 패널을 보고 있는 중이면 굳이 안 읽음 배지를 띄우지 않는다.
          if (activePanelRef.current !== 'alerts') setHasUnreadAlert(true)
        })
        .catch(() => {
          // 폴링 실패는 조용히 넘어가고 다음 주기에 다시 시도한다.
        })
    }

    poll()
    const interval = setInterval(poll, BOOTH_ALERT_POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [isAuthenticated, user?.id])

  async function handleSubmit(event) {
    event.preventDefault()
    const message = input.trim()
    if (!message || isSending) return

    const history = chatMessages
      .filter((item) => !item.greeting && !item.error)
      .map((item) => ({ role: item.role, content: item.content }))
    setChatMessages((prev) => [...prev, { id: `u-${Date.now()}`, role: 'user', content: message }])
    setInput('')
    setIsSending(true)
    try {
      const response = await requestRecommendations(message, history)
      const { reply, recommendations } = response.data.data
      setChatMessages((prev) => [...prev, { id: `a-${Date.now()}`, role: 'assistant', content: reply, recommendations }])
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      const content = error.response?.status === 401
        ? '로그인이 필요해요. 로그인 후 다시 시도해 주세요.'
        : ERROR_MESSAGES[errorCode] ?? '추천을 가져오지 못했어요. 잠시 후 다시 시도해 주세요.'
      setChatMessages((prev) => [...prev, { id: `e-${Date.now()}`, role: 'assistant', content, error: true }])
    } finally {
      setIsSending(false)
    }
  }

  function openPanel(panel) {
    setActivePanel(panel)
    setDialOpen(false)
    if (panel === 'alerts') setHasUnreadAlert(false)
  }

  function handleMainButtonClick() {
    if (activePanel) {
      setActivePanel(null)
      return
    }
    setDialOpen((prev) => !prev)
  }

  const mainButtonLabel = activePanel ? '챗봇 패널 닫기' : dialOpen ? '챗봇 메뉴 닫기' : '챗봇 메뉴 열기'
  const showBadge = hasUnreadAlert && activePanel !== 'alerts'

  return (
    <>
      {dialOpen && !activePanel && (
        <div className="fixed bottom-24 right-6 z-50 flex flex-col items-end gap-2">
          <MiniFabButton icon={SparklesIcon} label="추천 챗봇" onClick={() => openPanel('recommend')} />
          <MiniFabButton icon={MegaphoneIcon} label="부스 알림" onClick={() => openPanel('alerts')} showBadge={hasUnreadAlert} />
        </div>
      )}

      <button
        type="button"
        onClick={handleMainButtonClick}
        aria-label={mainButtonLabel}
        aria-expanded={dialOpen || Boolean(activePanel)}
        className="fixed bottom-6 right-6 z-50 flex h-14 w-14 cursor-pointer items-center justify-center rounded-full bg-blue-600 text-white shadow-lg transition hover:bg-blue-700"
      >
        {activePanel || dialOpen ? <XIcon className="h-6 w-6" /> : <MessageCircleIcon className="h-6 w-6" />}
        {showBadge && (
          <span
            aria-hidden="true"
            className="absolute right-0 top-0 h-3.5 w-3.5 rounded-full border-2 border-white bg-red-500"
          />
        )}
      </button>

      {activePanel === 'recommend' && (
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
            {chatMessages.map((item) => (
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
              <Link to="/login" onClick={() => setActivePanel(null)} className="font-bold text-blue-600 hover:underline">
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

      {activePanel === 'alerts' && (
        <section
          role="dialog"
          aria-label="부스 대기 알림"
          className="fixed bottom-24 right-6 z-50 flex h-[520px] max-h-[calc(100vh-7rem)] w-[360px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-3xl border border-gray-200 bg-white shadow-2xl"
        >
          <header className="flex items-center gap-2 border-b border-gray-100 bg-amber-500 px-4 py-3 text-white">
            <MegaphoneIcon className="h-5 w-5" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold">부스 대기 알림</p>
              <p className="text-xs text-amber-50">내 순번이 호출되면 여기로 알려드려요</p>
            </div>
          </header>

          <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto bg-gray-50 px-4 py-4">
            {boothAlerts.length === 0 ? (
              <p className="pt-10 text-center text-sm text-gray-500">
                아직 알림이 없어요.
                <br />
                대기 신청한 부스의 순번이 호출되면 알려드려요.
              </p>
            ) : (
              boothAlerts.map((item) => <ChatMessage key={item.id} message={item} />)
            )}
          </div>
        </section>
      )}
    </>
  )
}

//스피드다이얼 미니 버튼 — 아이콘 옆에 라벨 텍스트를 붙이되, 모바일에서도 크게 보이지 않도록 작게 유지한다.
function MiniFabButton({ icon: Icon, label, onClick, showBadge }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="relative flex items-center gap-1.5 rounded-full bg-white py-1.5 pl-3 pr-3.5 text-xs font-bold text-gray-700 shadow-lg ring-1 ring-gray-200 transition hover:bg-gray-50"
    >
      <Icon className="h-3.5 w-3.5 text-blue-600" aria-hidden="true" />
      <span>{label}</span>
      {showBadge && (
        <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-red-500" />
      )}
    </button>
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
                : message.boothAlert
                  ? 'flex items-start gap-2 whitespace-pre-wrap rounded-2xl rounded-bl-md bg-amber-50 px-4 py-2.5 text-sm text-amber-800 shadow-sm'
                  : 'whitespace-pre-wrap rounded-2xl rounded-bl-md bg-white px-4 py-2.5 text-sm text-gray-800 shadow-sm'
          }
          role={message.error ? 'alert' : undefined}
        >
          {message.boothAlert && <MegaphoneIcon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
          <span>{message.content}</span>
        </div>
        {message.boothAlert && (
          <Link
            to={message.link}
            className="block rounded-2xl border border-amber-100 bg-white px-4 py-3 shadow-sm transition hover:border-amber-300 hover:bg-amber-50"
          >
            <p className="text-xs font-bold text-amber-700">부스로 이동하기 →</p>
          </Link>
        )}
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
