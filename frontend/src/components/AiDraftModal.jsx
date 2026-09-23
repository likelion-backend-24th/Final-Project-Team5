import { useLayoutEffect, useRef, useState } from 'react'
import { SparklesIcon, XIcon } from 'lucide-react'
import { generateFestivalAiDraft } from '../api/hostFestivalApi'

const PROMPT_MAX_LENGTH = 500

const ERROR_MESSAGES = {
  CHATBOT_UNAVAILABLE: 'AI 초안 생성 서비스를 잠시 이용할 수 없어요. 잠시 후 다시 시도해주세요.',
}

/**
 * 페스티벌 등록 폼의 "AI로 초안 채우기" 팝업. "자동 기입"/"직접 기입" 중 하나를 고르게 하고,
 * 자동 기입을 고르면 자유 텍스트를 받아 Gemini로 소개글·티켓 종류 제안을 생성해 onApply로 넘긴다.
 * 생성된 값은 여기서 폼에 바로 반영하지 않는다 — 호출부(HostFestivalNew)가 채운 뒤 호스트가 검토·수정한다.
 */
function AiDraftModal({ onClose, onApply }) {
  const dialog = useRef(null)
  const [mode, setMode] = useState('choose') // 'choose' | 'prompt'
  const [prompt, setPrompt] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useLayoutEffect(() => {
    const element = dialog.current
    const previous = document.activeElement
    if (element.showModal) element.showModal()
    else element.setAttribute('open', '')
    return () => {
      element.close?.()
      previous?.focus?.()
    }
  }, [])

  function handleDirectInput() {
    onClose()
  }

  async function handleGenerate(event) {
    event.preventDefault()
    if (!prompt.trim() || submitting) return

    setSubmitting(true)
    setError('')
    try {
      const response = await generateFestivalAiDraft(prompt.trim())
      onApply(response.data.data)
    } catch (submitError) {
      const errorCode = submitError.response?.data?.errorCode
      setError(ERROR_MESSAGES[errorCode] || 'AI 초안 생성에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="ai-draft-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className="fixed inset-0 m-auto max-h-[85dvh] w-[calc(100%_-_2rem)] max-w-md overflow-y-auto rounded-3xl border-0 bg-white p-0 text-gray-900 shadow-2xl backdrop:bg-slate-900/45"
    >
      <header className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-gray-100 bg-white px-6 py-5">
        <h2 id="ai-draft-title" className="flex items-center gap-2 text-lg font-extrabold tracking-tight">
          <SparklesIcon size={20} aria-hidden="true" />
          AI로 초안 채우기
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 focus-visible:outline-2 focus-visible:outline-blue-600"
        >
          <XIcon size={20} />
        </button>
      </header>

      {mode === 'choose' ? (
        <div className="space-y-3 p-6">
          <p className="text-sm text-gray-600">
            소개글과 티켓 종류를 AI가 초안으로 채워드릴까요? 생성된 내용은 폼에서 자유롭게 수정할 수 있어요.
          </p>
          <button
            type="button"
            onClick={() => setMode('prompt')}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white transition hover:bg-blue-700"
          >
            <SparklesIcon size={16} aria-hidden="true" />
            자동 기입 (AI 사용)
          </button>
          <button
            type="button"
            onClick={handleDirectInput}
            className="w-full rounded-xl border border-gray-200 py-3 text-sm font-bold text-gray-700 transition hover:bg-gray-50"
          >
            직접 기입할게요
          </button>
        </div>
      ) : (
        <form className="space-y-4 p-6" onSubmit={handleGenerate} noValidate>
          {error && (
            <p className="rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600" role="alert">
              {error}
            </p>
          )}

          <div>
            <label htmlFor="ai-draft-prompt" className="mb-1.5 block text-sm font-bold text-gray-900">
              어떤 페스티벌인지 짧게 설명해주세요
            </label>
            <textarea
              id="ai-draft-prompt"
              rows={4}
              maxLength={PROMPT_MAX_LENGTH}
              className="w-full resize-none rounded-xl border border-gray-200 px-4 py-2.5 text-sm outline-none focus:border-blue-500"
              placeholder="예: 부산시청에서 2주 뒤 여는 대학 축제, 인디밴드 공연과 푸드트럭 존, 이틀간 진행"
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              disabled={submitting}
              autoFocus
            />
            <p className="mt-1 text-right text-xs text-gray-400">{prompt.length}/{PROMPT_MAX_LENGTH}</p>
          </div>

          <p className="text-xs text-gray-500">
            소개글·일정·위치·티켓 종류(최대 3개) 초안을 만들어드려요. "2주 뒤", "다음 달 초" 같은 표현도 오늘
            날짜 기준으로 계산하고, 장소를 언급하면 지도 위치도 같이 찾아드려요. 언급하지 않은 정보(날짜·가격
            등)는 기본값으로 채워지니 생성 후 꼭 확인해주세요.
          </p>

          <button
            type="submit"
            disabled={submitting || !prompt.trim()}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? '생성 중… (최대 20초 정도 걸려요)' : '생성하기'}
          </button>
        </form>
      )}
    </dialog>
  )
}

export default AiDraftModal
