import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRightIcon, CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { completeProfileSetup } from '../api/userApi'

const PROVIDER_LABELS = { KAKAO: '카카오', GOOGLE: '구글' }

const inputBaseClass =
  'w-full rounded-2xl border bg-white px-4 py-3 text-[15px] text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'

function inputClass(hasError) {
  return `${inputBaseClass} ${hasError ? 'border-red-500' : 'border-gray-300'}`
}

/**
 * 소셜(카카오·구글) 로그인으로 처음 들어온 회원의 최초 1회 프로필 설정 화면.
 * 소셜 가입은 약관 동의·닉네임 입력 없이 계정이 만들어지므로, 여기서 이름·닉네임을 확정하고 약관에 동의받는다.
 * 완료 전에는 App 라우팅이 다른 화면으로 못 가게 이 화면으로 되돌린다.
 */
function ProfileSetup() {
  const navigate = useNavigate()
  const { user, refreshUser } = useAuth()

  const [name, setName] = useState(user?.name ?? '')
  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [agreed, setAgreed] = useState(false)
  const [errors, setErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const providerLabel = (user?.socialProviders ?? []).map((p) => PROVIDER_LABELS[p] ?? p).join('·') || '소셜'

  function validate() {
    if (!name.trim()) return { name: '이름을 입력해주세요.' }
    const trimmedNickname = nickname.trim()
    if (!trimmedNickname) return { nickname: '닉네임을 입력해주세요.' }
    if (trimmedNickname.length < 2 || trimmedNickname.length > 12) {
      return { nickname: '닉네임은 2~12자로 입력해주세요.' }
    }
    if (!agreed) return { agreed: '약관에 동의해주세요.' }
    return {}
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const nextErrors = validate()
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    setSubmitError('')
    try {
      await completeProfileSetup({ name: name.trim(), nickname: nickname.trim(), termsAgreed: agreed })
      await refreshUser()
      navigate('/', { replace: true })
    } catch (error) {
      const data = error.response?.data
      if (data?.errorCode === 'DUPLICATE_NICKNAME') {
        setErrors({ nickname: data.message || '이미 사용 중인 닉네임이에요.' })
      } else {
        setSubmitError(data?.message || '프로필 설정에 실패했어요. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 pt-6 pb-14 sm:pt-10 sm:pb-20">
      <div className="h-fit w-full max-w-md rounded-3xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">거의 다 됐어요</h1>
        <p className="mt-2 text-sm leading-relaxed text-gray-500">
          {providerLabel} 계정으로 처음 로그인하셨네요. FevalGo에서 사용할 이름과 닉네임을 확인하고 약관에
          동의해주세요.
        </p>

        {submitError && (
          <p
            role="alert"
            className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600"
          >
            <CircleAlertIcon className="h-4 w-4" />
            {submitError}
          </p>
        )}

        <form className="mt-6 space-y-5" onSubmit={handleSubmit} noValidate>
          <div className="space-y-2">
            <label htmlFor="setup-name" className="block text-sm font-bold text-gray-900">
              이름
            </label>
            <input
              id="setup-name"
              type="text"
              value={name}
              onChange={(event) => {
                setName(event.target.value)
                setErrors((prev) => ({ ...prev, name: undefined }))
              }}
              aria-invalid={Boolean(errors.name)}
              autoComplete="name"
              className={inputClass(errors.name)}
            />
            {errors.name && <p className="text-xs font-semibold text-red-500">{errors.name}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="setup-nickname" className="block text-sm font-bold text-gray-900">
              닉네임
            </label>
            <input
              id="setup-nickname"
              type="text"
              placeholder="2~12자"
              value={nickname}
              onChange={(event) => {
                setNickname(event.target.value)
                setErrors((prev) => ({ ...prev, nickname: undefined }))
              }}
              aria-invalid={Boolean(errors.nickname)}
              autoComplete="nickname"
              className={inputClass(errors.nickname)}
            />
            {errors.nickname ? (
              <p className="text-xs font-semibold text-red-500">{errors.nickname}</p>
            ) : (
              <p className="text-xs text-gray-400">자동으로 만들어진 닉네임이에요. 원하는 이름으로 바꿔도 됩니다.</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(event) => {
                  setAgreed(event.target.checked)
                  setErrors((prev) => ({ ...prev, agreed: undefined }))
                }}
                className="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span>
                <Link to="/terms" className="font-bold text-blue-600 hover:underline">
                  이용약관
                </Link>{' '}
                및{' '}
                <Link to="/privacy" className="font-bold text-blue-600 hover:underline">
                  개인정보처리방침
                </Link>
                에 동의합니다. <span className="text-gray-500">(필수)</span>
              </span>
            </label>
            {errors.agreed && <p className="text-xs font-semibold text-red-500">{errors.agreed}</p>}
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full cursor-pointer items-center justify-center gap-2 rounded-2xl bg-blue-600 py-3.5 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            {submitting ? '저장 중…' : '시작하기'}
            <ArrowRightIcon className="h-4 w-4" />
          </button>
        </form>
      </div>
    </main>
  )
}

export default ProfileSetup
