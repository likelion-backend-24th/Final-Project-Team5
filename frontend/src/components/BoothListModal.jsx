import { useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ImageIcon, StoreIcon, XIcon } from 'lucide-react'
import { fetchBoothsForFestival, fetchMyBoothWaitlist, requestBoothWaitlist } from '../api/boothApi'
import { toAbsoluteImageUrl } from '../api/festivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import Badge from './Badge'

const BOOTH_STATUS_LABELS = {
  OPEN: '운영중',
  CLOSED: '마감',
}

const WAITLIST_ERROR_MESSAGES = {
  TICKET_NOT_FOUND: '이 페스티벌의 예매 내역이 있어야 대기 신청할 수 있어요.',
  BOOTH_NOT_OPEN: '지금은 대기 신청을 받고 있지 않은 부스예요.',
  BOOTH_NOT_FOUND: '부스 정보를 다시 불러와주세요.',
}

/**
 * 페스티벌 상세의 "운영중인 부스 보기" 버튼으로 여는 부스 목록 모달.
 * 필드가 제목·소개·호스트명·이미지뿐이라 별도 상세 화면 없이 이 카드 자체가 상세를 겸한다.
 * WAITING 상태 부스는 백엔드가 애초에 목록에 내려주지 않는다(관람자에게 숨김).
 */
function BoothListModal({ festivalId, onClose }) {
  const dialog = useRef(null)
  const { user } = useAuth()
  const [booths, setBooths] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  //부스별 대기 신청 진행 상태 — { [boothId]: { status: 'idle'|'submitting'|'requested'|'error', queueNumber, message } }
  const [waitlistState, setWaitlistState] = useState({})

  // 내용이 화면에 노출되기 전에 모달을 열어 포커스와 접근성 트리가 같은 상태를 보게 한다.
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

  useLayoutEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')
    fetchBoothsForFestival(festivalId)
      .then((response) => {
        if (!cancelled) setBooths(response.data.data)
      })
      .catch(() => {
        if (!cancelled) setLoadError('부스 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [festivalId])

  async function handleRequestWaitlist(boothId) {
    setWaitlistState((prev) => ({ ...prev, [boothId]: { status: 'submitting' } }))
    try {
      const response = await requestBoothWaitlist(boothId)
      setWaitlistState((prev) => ({
        ...prev,
        [boothId]: { status: 'requested', queueNumber: response.data.data.queueNumber },
      }))
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      if (errorCode === 'ALREADY_REQUESTED') {
        // 이미 신청한 적이 있으면 그때 내 순번을 다시 조회해서 보여준다.
        try {
          const mine = await fetchMyBoothWaitlist(boothId)
          setWaitlistState((prev) => ({
            ...prev,
            [boothId]: { status: 'requested', queueNumber: mine.data.data.queueNumber },
          }))
          return
        } catch {
          // 조회도 실패하면 아래 일반 에러 메시지로 넘어간다.
        }
      }
      setWaitlistState((prev) => ({
        ...prev,
        [boothId]: {
          status: 'error',
          message: WAITLIST_ERROR_MESSAGES[errorCode] || '대기 신청 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.',
        },
      }))
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="booth-list-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className="fixed inset-0 m-auto max-h-[85dvh] w-[calc(100%_-_2rem)] max-w-md overflow-y-auto rounded-3xl border-0 bg-white p-0 text-gray-900 shadow-2xl backdrop:bg-slate-900/45"
    >
      <header className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-gray-100 bg-white px-6 py-5">
        <h2 id="booth-list-title" className="flex items-center gap-2 text-lg font-extrabold tracking-tight">
          <StoreIcon size={20} aria-hidden="true" />
          부스
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

      <div className="p-6">
        {loading && <p className="py-10 text-center text-sm text-gray-500">불러오는 중…</p>}

        {!loading && loadError && (
          <p className="py-10 text-center text-sm text-red-600" role="alert">
            {loadError}
          </p>
        )}

        {!loading && !loadError && booths.length === 0 && (
          <p className="py-10 text-center text-sm text-gray-500">아직 개설된 부스가 없어요.</p>
        )}

        {!loading && !loadError && booths.length > 0 && (
          <ul className="space-y-4">
            {booths.map((booth) => (
              <BoothCard
                key={booth.id}
                booth={booth}
                user={user}
                waitlist={waitlistState[booth.id]}
                onRequestWaitlist={() => handleRequestWaitlist(booth.id)}
              />
            ))}
          </ul>
        )}
      </div>
    </dialog>
  )
}

function BoothCard({ booth, user, waitlist, onRequestWaitlist }) {
  const closed = booth.boothStatus === 'CLOSED'
  const status = waitlist?.status ?? 'idle'

  return (
    <li className="overflow-hidden rounded-2xl border border-gray-200">
      <div className="flex gap-3 p-4">
        {booth.imageUrl ? (
          <img
            src={toAbsoluteImageUrl(booth.imageUrl)}
            alt=""
            className="h-16 w-16 shrink-0 rounded-xl object-cover"
          />
        ) : (
          <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-xl bg-gray-100 text-gray-400">
            <ImageIcon size={24} aria-hidden="true" />
          </div>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-bold text-gray-900">{booth.title}</p>
            <Badge variant={closed ? 'secondary' : 'accent'}>{BOOTH_STATUS_LABELS[booth.boothStatus]}</Badge>
          </div>
          <p className="mt-0.5 text-xs text-gray-500">{booth.boothHostName}</p>
          {booth.description && <p className="mt-1 text-sm text-gray-600">{booth.description}</p>}
        </div>
      </div>

      <div className="border-t border-gray-100 bg-gray-50 px-4 py-3">
        {closed ? (
          <p className="text-center text-sm font-semibold text-gray-500">마감입니다</p>
        ) : !user ? (
          <Link
            to="/login"
            className="block rounded-xl bg-gray-100 py-2 text-center text-sm font-bold text-gray-600 hover:bg-gray-200"
          >
            로그인 후 대기 신청
          </Link>
        ) : status === 'requested' ? (
          <p className="text-center text-sm font-bold text-blue-600">내 대기번호 {waitlist.queueNumber}번</p>
        ) : (
          <>
            <button
              type="button"
              disabled={status === 'submitting'}
              onClick={onRequestWaitlist}
              className="w-full rounded-xl bg-blue-600 py-2 text-sm font-bold text-white transition hover:bg-blue-700 disabled:opacity-60"
            >
              {status === 'submitting' ? '신청 중…' : '대기 신청'}
            </button>
            {status === 'error' && (
              <p className="mt-2 text-center text-xs text-red-600" role="alert">
                {waitlist.message}
              </p>
            )}
          </>
        )}
      </div>
    </li>
  )
}

export default BoothListModal
