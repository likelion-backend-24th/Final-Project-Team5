import { useCallback, useEffect, useRef, useState } from 'react'
import { CircleAlertIcon, RefreshCwIcon, UserPlusIcon, UsersIcon, UserXIcon } from 'lucide-react'
import {
  convertLegacyHelper,
  createHelperAccount,
  fetchHelperAccounts,
  resendHelperInvitation,
  revokeHelperAccount,
} from '../api/hostFestivalApi'

const HELPER_ERROR_MESSAGES = {
  INVITATION_DUPLICATE: '이미 초대한 이메일이에요. 목록의 재발송을 이용해주세요.',
  INVITATION_COOLDOWN: '최근 발송 후 60초 뒤 다시 시도해주세요.',
  INVITATION_SEND_LIMIT: '24시간 동안 최대 10회 발송할 수 있어요.',
  INVITATION_SEND_FAILED: '메일 발송에 실패했어요. 계정은 보존되었으니 목록에서 재발송해주세요.',
  HELPER_FESTIVAL_ENDED: '종료된 행사에는 초대할 수 없어요.',
}

const STATUS_LABELS = {
  PENDING: '초대 대기',
  ACCEPTED: '활성화 완료',
  ACTIVE: '활성화 완료',
  EXPIRED: '만료',
  REVOKED: '해지',
}

const STATUS_CLASSES = {
  PENDING: 'bg-amber-50 text-amber-700',
  ACCEPTED: 'bg-emerald-50 text-emerald-700',
  ACTIVE: 'bg-emerald-50 text-emerald-700',
  EXPIRED: 'bg-gray-100 text-gray-600',
  REVOKED: 'bg-gray-100 text-gray-600',
  SEND_FAILED: 'bg-red-50 text-red-600',
}

const INPUT_CLASS = 'mt-2 w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-normal text-gray-900 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100'
const PRIMARY_BUTTON_CLASS = 'inline-flex items-center gap-2 rounded-2xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'
const SECONDARY_BUTTON_CLASS = 'inline-flex items-center gap-2 rounded-2xl border border-gray-200 px-4 py-2.5 text-sm font-bold text-gray-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-50'
const PILL_BUTTON_CLASS = 'inline-flex shrink-0 items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-50'

function toErrorMessage(error) {
  return HELPER_ERROR_MESSAGES[error.response?.data?.errorCode]
    || error.response?.data?.message
    || '요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.'
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function HostHelperAccounts({ festivalId }) {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [email, setEmail] = useState('')
  const [legacyEmails, setLegacyEmails] = useState({})
  const [working, setWorking] = useState(false)
  const busy = useRef(false)

  const load = useCallback(async (isCancelled = () => false) => {
    try {
      const response = await fetchHelperAccounts(festivalId)
      if (!isCancelled()) setSummary(response.data.data)
    } catch (err) {
      if (!isCancelled()) setError(toErrorMessage(err))
    } finally {
      if (!isCancelled()) setLoading(false)
    }
  }, [festivalId])

  useEffect(() => {
    let cancelled = false
    load(() => cancelled)
    return () => { cancelled = true }
  }, [load])

  async function act(action, success) {
    if (busy.current) return
    busy.current = true
    setWorking(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      await load()
      busy.current = false
      setWorking(false)
    }
  }

  function invite(event) {
    event.preventDefault()
    act(async () => {
      await createHelperAccount(festivalId, email.trim().toLowerCase())
      setEmail('')
    }, '초대 메일을 발송했어요.')
  }

  return (
    <section className="mt-10 rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-extrabold text-gray-900">
          <UsersIcon className="h-5 w-5 text-blue-600" />
          도우미 이메일 초대
        </h2>
      </div>

      <p className="mt-2 text-sm text-gray-500">
        수신 이메일로 비밀번호 설정 링크를 보내요. 로그인에는 별도로 발급된 아이디를 사용해요. 행사 종료 시 이용이 중지되고 24시간 뒤 계정과 연락처가 삭제돼요.
      </p>

      <form onSubmit={invite} className="mt-4 flex flex-wrap items-end gap-2">
        <label className="min-w-0 flex-1 text-sm font-bold text-gray-900">
          초대 이메일
          <input
            aria-label="초대 이메일"
            className={INPUT_CLASS}
            type="email"
            required
            maxLength={254}
            value={email}
            onChange={event => setEmail(event.target.value)}
            autoComplete="email"
          />
        </label>
        <button
          className={PRIMARY_BUTTON_CLASS}
          disabled={working}
          type="submit"
        >
          <UserPlusIcon className="h-4 w-4" />
          이메일 초대
        </button>
      </form>

      {error && (
        <p role="alert" className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
          <CircleAlertIcon className="h-4 w-4 shrink-0" />
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="mt-4 rounded-2xl bg-blue-50 px-3.5 py-3 text-[13px] font-semibold text-blue-600">
          {notice}
        </p>
      )}
      {loading && <p className="mt-4 text-sm text-gray-400">불러오는 중…</p>}
      {!loading && !summary && (
        <button className={SECONDARY_BUTTON_CLASS} onClick={() => load()}>
          다시 불러오기
        </button>
      )}

      {summary && (
        <>
          <p className="mt-4 text-sm font-bold text-gray-900">도우미 {summary.totalCount}명</p>
          {summary.totalCount === 0 && (
            <p className="mt-2 text-sm text-gray-500">아직 초대한 도우미가 없어요.</p>
          )}
          <ul className="mt-3 space-y-2">
            {summary.helpers.map(helper => {
              const canResend = !helper.legacy && ['PENDING', 'EXPIRED'].includes(helper.status)
              const sendFailed = canResend && helper.deliveryStatus === 'SEND_FAILED'
              const status = sendFailed ? '발송 실패' : STATUS_LABELS[helper.status] || helper.status
              const statusClass = STATUS_CLASSES[sendFailed ? 'SEND_FAILED' : helper.status]
                || STATUS_CLASSES.EXPIRED

              return (
                <li
                  key={helper.helperUserId}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-100 px-4 py-3"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="break-all font-mono text-sm font-bold text-gray-900">
                        {helper.username}
                      </p>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${statusClass}`}>
                        {status}
                      </span>
                    </div>
                    <p className="mt-1 break-all text-sm text-gray-600">
                      {helper.email || '연락 이메일 없는 기존 계정'}
                    </p>
                    <p className="mt-2 text-xs text-gray-400">
                      최초 발송: {formatDate(helper.sentAt)}<br />
                      최근 발송: {formatDate(helper.lastSentAt)}<br />
                      초대 만료: {formatDate(helper.expiresAt)}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    {canResend && (
                      <button
                        className={PILL_BUTTON_CLASS}
                        disabled={working}
                        onClick={() => act(
                          () => resendHelperInvitation(festivalId, helper.helperUserId),
                          '초대를 재발송했어요. 이전 링크는 만료됐어요.',
                        )}
                      >
                        <RefreshCwIcon className="h-3.5 w-3.5" />
                        재발송
                      </button>
                    )}
                    {helper.status !== 'REVOKED' && (
                      <button
                        className={PILL_BUTTON_CLASS}
                        disabled={working}
                        onClick={() => {
                          if (window.confirm('초대와 계정을 해지할까요? 기존 로그인도 즉시 사용할 수 없게 됩니다.')) {
                            act(
                              () => revokeHelperAccount(festivalId, helper.helperUserId),
                              '도우미 계정을 해지했어요.',
                            )
                          }
                        }}
                      >
                        <UserXIcon className="h-3.5 w-3.5" />
                        해지
                      </button>
                    )}
                  </div>
                  {helper.legacy && helper.status !== 'REVOKED' && (
                    <form
                      className="mt-1 flex w-full flex-wrap items-end gap-2"
                      onSubmit={event => {
                        event.preventDefault()
                        act(
                          () => convertLegacyHelper(
                            festivalId,
                            helper.helperUserId,
                            (legacyEmails[helper.helperUserId] || '').trim().toLowerCase(),
                          ),
                          '전환 초대를 보냈어요. 수락 전까지 기존 로그인을 유지해요.',
                        )
                      }}
                    >
                      <label className="min-w-0 flex-1 text-sm font-bold text-gray-900">
                        전환 이메일
                        <input
                          className={INPUT_CLASS}
                          type="email"
                          required
                          maxLength={254}
                          value={legacyEmails[helper.helperUserId] || ''}
                          onChange={event => setLegacyEmails(prev => ({
                            ...prev,
                            [helper.helperUserId]: event.target.value,
                          }))}
                        />
                      </label>
                      <button className={SECONDARY_BUTTON_CLASS} disabled={working}>
                        이메일 초대로 전환
                      </button>
                    </form>
                  )}
                </li>
              )
            })}
          </ul>
        </>
      )}
    </section>
  )
}

export default HostHelperAccounts
