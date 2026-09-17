import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowRightIcon, CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { confirmOauthLink } from '../api/authApi'

/**
 * 이미 아이디/비밀번호로 쓰이던 이메일에 구글 로그인을 시도했을 때, 구글 콜백이 조용히 연동하지
 * 않고 이 화면으로 보내 최초 1회 연동 동의를 받는다. 동의하면 구글 계정이 기존 계정에 연결되고, 비밀번호는
 * 그대로라 이후엔 비밀번호·구글 어느 쪽으로도 로그인된다. 동의하지 않으면 서버에는 아무 변화도 없고,
 * 토큰은 몇 분 뒤 그냥 만료된다.
 */
function OauthLinkConfirm() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { applyTokenLogin } = useAuth()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const token = searchParams.get('token')
  const email = searchParams.get('email')

  if (!token) {
    return (
      <main className="flex flex-1 justify-center bg-gray-50 px-6 pt-6 pb-14 sm:pt-10 sm:pb-20">
        <div className="h-fit w-full max-w-md rounded-3xl border border-gray-200 bg-white p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">잘못된 접근이에요.</p>
          <Link to="/login" className="mt-4 inline-block font-bold text-blue-600 hover:underline">
            로그인으로 돌아가기
          </Link>
        </div>
      </main>
    )
  }

  async function handleAgree() {
    setSubmitting(true)
    setError('')
    try {
      const response = await confirmOauthLink(token)
      await applyTokenLogin(response.data.data.accessToken)
      navigate('/', { replace: true })
    } catch {
      setError('연동에 실패했어요. 로그인 화면에서 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleDecline() {
    navigate('/login', { replace: true })
  }

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 pt-6 pb-14 sm:pt-10 sm:pb-20">
      <div className="h-fit w-full max-w-md rounded-3xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">기존에 가입된 계정이 있어요</h1>
        <p className="mt-3 text-sm leading-relaxed text-gray-500">
          {email && (
            <>
              <span className="font-bold text-gray-900">{email}</span>은 이미 아이디/비밀번호로 가입된 계정이에요. 해당 계정과 연동할까요?
              <br />
            </>
          )}
          연동하면 기존 비밀번호는 그대로 유지되고, 다음부터는 아이디/비밀번호와 구글 로그인 어느 쪽으로도 로그인할 수
          있어요.
        </p>

        {error && (
          <p
            role="alert"
            className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600"
          >
            <CircleAlertIcon className="h-4 w-4" />
            {error}
          </p>
        )}

        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={handleDecline}
            disabled={submitting}
            className="flex-1 cursor-pointer rounded-2xl border border-gray-200 py-3.5 text-[15px] font-bold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleAgree}
            disabled={submitting}
            className="flex flex-1 cursor-pointer items-center justify-center gap-2 rounded-2xl bg-blue-600 py-3.5 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            {submitting ? '연동 중…' : '연동하고 로그인'}
            <ArrowRightIcon className="h-4 w-4" />
          </button>
        </div>
      </div>
    </main>
  )
}

export default OauthLinkConfirm
