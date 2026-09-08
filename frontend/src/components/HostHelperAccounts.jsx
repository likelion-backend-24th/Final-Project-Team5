import { useCallback, useEffect, useState } from 'react'
import { CircleAlertIcon, KeyRoundIcon, TriangleAlertIcon, UserPlusIcon, UsersIcon } from 'lucide-react'
import { createHelperAccount, fetchHelperAccounts, reissueHelperPassword } from '../api/hostFestivalApi'

const HELPER_ERROR_MESSAGES = {
  FORBIDDEN_HOST_ROLE: '주최자만 도우미 계정을 관리할 수 있어요.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌이에요.',
  HELPER_ACCOUNT_NOT_FOUND: '존재하지 않는 도우미 계정이에요.',
  AUTH_SERVICE_UNAVAILABLE: '계정 서버와 통신하지 못했어요. 잠시 후 다시 시도해주세요.',
}

function toErrorMessage(error) {
  const errorCode = error.response?.data?.errorCode
  return HELPER_ERROR_MESSAGES[errorCode] || '요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.'
}

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/**
 * 주최자가 현장 입장 검증을 맡길 도우미 계정을 발급·조회·비밀번호 재발급하는 영역.
 * 평문 비밀번호는 발급·재발급 응답에서만 볼 수 있고 서버에 남지 않으므로(해시만 저장),
 * 목록에는 개수와 아이디만 나오고 비밀번호를 놓치면 재발급을 받아야 한다.
 */
function HostHelperAccounts({ festivalId }) {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [credential, setCredential] = useState(null)
  const [working, setWorking] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    return fetchHelperAccounts(festivalId)
      .then((response) => {
        setSummary(response.data.data)
        setLoadError('')
      })
      .catch((error) => setLoadError(toErrorMessage(error)))
      .finally(() => setLoading(false))
  }, [festivalId])

  useEffect(() => {
    load()
  }, [load])

  async function handleCreate() {
    if (!window.confirm('새 도우미 계정을 생성하시겠습니까?')) return
    setWorking(true)
    setActionError('')
    try {
      const response = await createHelperAccount(festivalId)
      setCredential(response.data.data)
      await load()
    } catch (error) {
      setActionError(toErrorMessage(error))
    } finally {
      setWorking(false)
    }
  }

  async function handleReissue(helperUserId, username) {
    if (!window.confirm(`${username} 계정의 비밀번호를 새로 발급할까요? 기존 비밀번호는 사용할 수 없게 됩니다.`)) return
    setWorking(true)
    setActionError('')
    try {
      const response = await reissueHelperPassword(festivalId, helperUserId)
      setCredential(response.data.data)
    } catch (error) {
      setActionError(toErrorMessage(error))
    } finally {
      setWorking(false)
    }
  }

  return (
    <section className="mt-10 rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-extrabold text-gray-900">
          <UsersIcon className="h-5 w-5 text-blue-600" />
          도우미 계정
        </h2>
        <button
          type="button"
          onClick={handleCreate}
          disabled={working}
          className="inline-flex items-center gap-2 rounded-2xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
        >
          <UserPlusIcon className="h-4 w-4" />
          도우미 계정 발급
        </button>
      </div>

      <p className="mt-2 text-sm text-gray-500">
        현장 입장 검증(QR 스캔·입장 코드 확인)만 할 수 있는 임시 계정이에요. 페스티벌 종료 24시간 뒤 자동으로 사용
        중지됩니다.
      </p>

      {/* 발급·재발급 직후에만 평문 비밀번호를 보여준다 */}
      {credential && (
        <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <p className="flex items-center gap-1.5 text-sm font-extrabold text-amber-900">
            <TriangleAlertIcon className="h-4 w-4" />
            비밀번호는 지금만 확인할 수 있어요
          </p>
          <p className="mt-1 text-xs text-amber-800">
            이 화면을 닫으면 다시 볼 수 없어요. 도우미에게 전달한 뒤 닫아주세요.
          </p>

          <dl className="mt-3 space-y-2 rounded-xl bg-white p-3">
            <div className="flex items-center justify-between gap-3">
              <dt className="text-xs font-bold text-gray-500">아이디</dt>
              <dd className="truncate font-mono text-sm font-bold text-gray-900">{credential.username}</dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-xs font-bold text-gray-500">비밀번호</dt>
              <dd className="font-mono text-sm font-bold text-gray-900">{credential.password}</dd>
            </div>
          </dl>

          <button
            type="button"
            onClick={() => setCredential(null)}
            className="mt-3 w-full rounded-2xl bg-amber-900 py-2.5 text-sm font-bold text-white transition hover:bg-amber-800"
          >
            전달했어요 (닫기)
          </button>
        </div>
      )}

      {actionError && (
        <p role="alert" className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600">
          <CircleAlertIcon className="h-4 w-4" />
          {actionError}
        </p>
      )}

      {loading && <p className="mt-4 text-sm text-gray-400">불러오는 중…</p>}

      {!loading && loadError && <p className="mt-4 text-sm font-semibold text-red-600">{loadError}</p>}

      {!loading && !loadError && summary && (
        <>
          <p className="mt-4 text-sm font-bold text-gray-900">발급된 계정 {summary.totalCount}개</p>

          {summary.totalCount === 0 ? (
            <p className="mt-2 text-sm text-gray-400">아직 발급한 도우미 계정이 없어요.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {summary.helpers.map((helper) => (
                <li
                  key={helper.helperUserId}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-100 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="truncate font-mono text-sm font-bold text-gray-900">{helper.username}</p>
                    <p className="text-xs text-gray-400">{formatDate(helper.createdAt)} 발급</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleReissue(helper.helperUserId, helper.username)}
                    disabled={working}
                    className="inline-flex shrink-0 items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-bold text-gray-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <KeyRoundIcon className="h-3.5 w-3.5" />
                    비밀번호 재발급
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}

export default HostHelperAccounts
