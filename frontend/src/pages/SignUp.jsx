import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRightIcon, CircleAlertIcon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { signup } from '../api/authApi'
import { GoogleIcon, KakaoIcon } from '../components/SocialIcons'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

const initialForm = {
  name: '',
  username: '',
  nickname: '',
  password: '',
  passwordConfirm: '',
}

function validate(form) {
  const errors = {}

  if (!form.name.trim()) {
    errors.name = '이름을 입력해주세요.'
  }

  if (!form.username.trim()) {
    errors.username = '이메일을 입력해주세요.'
  } else if (!EMAIL_PATTERN.test(form.username.trim())) {
    errors.username = '올바른 이메일 형식이 아니에요.'
  }

  if (!form.nickname.trim()) {
    errors.nickname = '닉네임을 입력해주세요.'
  } else if (form.nickname.trim().length < 2 || form.nickname.trim().length > 12) {
    errors.nickname = '닉네임은 2~12자로 입력해주세요.'
  }

  if (!form.password) {
    errors.password = '비밀번호를 입력해주세요.'
  } else if (!PASSWORD_PATTERN.test(form.password)) {
    errors.password = '영문, 숫자를 포함해 8자 이상 입력해주세요.'
  }

  if (!form.passwordConfirm) {
    errors.passwordConfirm = '비밀번호를 한 번 더 입력해주세요.'
  } else if (form.password !== form.passwordConfirm) {
    errors.passwordConfirm = '비밀번호가 일치하지 않아요.'
  }

  return errors
}

const inputBaseClass =
  'w-full rounded-2xl border bg-white px-4 py-3 text-[15px] text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'

function inputClass(hasError) {
  return `${inputBaseClass} ${hasError ? 'border-red-500' : 'border-gray-300'}`
}

/** feval-go wireframe 기준 회원가입 화면. 이름/이메일/닉네임/비밀번호(확인)를 받는다. */
function SignUp() {
  const navigate = useNavigate()
  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState({})
  const [agreed, setAgreed] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function handleChange(field) {
    return (event) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [field]: value }))
      setErrors((prev) => ({ ...prev, [field]: undefined }))
      setSubmitError('')
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!agreed) return

    const nextErrors = validate(form)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    setSubmitError('')

    try {
      await signup({
        name: form.name.trim(),
        username: form.username.trim(),
        nickname: form.nickname.trim(),
        password: form.password,
      })
      navigate('/login')
    } catch (error) {
      if (error.response?.status === 409) {
        setErrors((prev) => ({
          ...prev,
          username: '이미 가입된 이메일이에요. 다른 이메일을 사용해주세요.',
        }))
      } else {
        setSubmitError('회원가입에 실패했어요. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 pt-6 pb-14 sm:pt-10 sm:pb-20">
      <div className="h-fit w-full max-w-md rounded-3xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">회원가입</h1>
        <p className="mt-2 text-sm text-gray-500">
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="font-bold text-blue-600 hover:underline">
            로그인
          </Link>
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
            <label htmlFor="name" className="block text-sm font-bold text-gray-900">
              이름
            </label>
            <input
              id="name"
              type="text"
              placeholder="실명을 입력하세요"
              value={form.name}
              onChange={handleChange('name')}
              aria-invalid={Boolean(errors.name)}
              autoComplete="name"
              className={inputClass(errors.name)}
            />
            {errors.name && <p className="text-xs font-semibold text-red-500">{errors.name}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="username" className="block text-sm font-bold text-gray-900">
              이메일
            </label>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                id="username"
                type="email"
                placeholder="이메일을 입력하세요"
                value={form.username}
                onChange={handleChange('username')}
                aria-invalid={Boolean(errors.username)}
                autoComplete="email"
                className={inputClass(errors.username)}
              />
              <button
                type="button"
                disabled
                title="준비 중인 기능이에요"
                className="shrink-0 whitespace-nowrap rounded-2xl border border-gray-300 bg-gray-50 px-4 py-3 text-sm font-semibold text-gray-400 transition disabled:cursor-not-allowed sm:py-0"
              >
                인증코드 받기
              </button>
            </div>
            {errors.username ? (
              <p className="text-xs font-semibold text-red-500">{errors.username}</p>
            ) : (
              <p className="text-xs text-gray-400">
                로그인에 사용할 이메일이에요. 인증 후 가입할 수 있어요.
              </p>
            )}
          </div>

          <div className="space-y-2">
            <label htmlFor="nickname" className="block text-sm font-bold text-gray-900">
              닉네임
            </label>
            <input
              id="nickname"
              type="text"
              placeholder="커뮤니티에서 표시될 이름"
              value={form.nickname}
              onChange={handleChange('nickname')}
              aria-invalid={Boolean(errors.nickname)}
              autoComplete="nickname"
              className={inputClass(errors.nickname)}
            />
            {errors.nickname ? (
              <p className="text-xs font-semibold text-red-500">{errors.nickname}</p>
            ) : (
              <p className="text-xs text-gray-400">2~12자, 언제든 변경 가능해요.</p>
            )}
          </div>

          <div className="space-y-2">
            <label htmlFor="password" className="block text-sm font-bold text-gray-900">
              비밀번호
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="8자 이상 입력하세요"
                value={form.password}
                onChange={handleChange('password')}
                aria-invalid={Boolean(errors.password)}
                autoComplete="new-password"
                className={`${inputClass(errors.password)} pr-12`}
              />
              <button
                type="button"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                {showPassword ? <EyeOffIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
              </button>
            </div>
            {errors.password ? (
              <p className="text-xs font-semibold text-red-500">{errors.password}</p>
            ) : (
              <p className="text-xs text-gray-400">영문, 숫자를 포함해 8자 이상 입력해주세요.</p>
            )}
          </div>

          <div className="space-y-2">
            <label htmlFor="passwordConfirm" className="block text-sm font-bold text-gray-900">
              비밀번호 확인
            </label>
            <div className="relative">
              <input
                id="passwordConfirm"
                type={showPasswordConfirm ? 'text' : 'password'}
                placeholder="비밀번호를 다시 입력하세요"
                value={form.passwordConfirm}
                onChange={handleChange('passwordConfirm')}
                aria-invalid={Boolean(errors.passwordConfirm)}
                autoComplete="new-password"
                className={`${inputClass(errors.passwordConfirm)} pr-12`}
              />
              <button
                type="button"
                onClick={() => setShowPasswordConfirm((value) => !value)}
                aria-label={showPasswordConfirm ? '비밀번호 숨기기' : '비밀번호 표시'}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                {showPasswordConfirm ? (
                  <EyeOffIcon className="h-5 w-5" />
                ) : (
                  <EyeIcon className="h-5 w-5" />
                )}
              </button>
            </div>
            {errors.passwordConfirm && (
              <p className="text-xs font-semibold text-red-500">{errors.passwordConfirm}</p>
            )}
          </div>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(event) => setAgreed(event.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
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

          <button
            type="submit"
            disabled={!agreed || submitting}
            className="flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 py-3.5 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            {submitting ? '가입 중…' : '가입하기'}
            <ArrowRightIcon className="h-4 w-4" />
          </button>
        </form>

        <div className="my-6 flex items-center gap-4">
          <div className="h-px flex-1 bg-gray-200" />
          <span className="text-sm text-gray-400">또는</span>
          <div className="h-px flex-1 bg-gray-200" />
        </div>

        <div className="flex items-center justify-center gap-5">
          <button
            type="button"
            disabled
            title="준비 중인 기능이에요"
            aria-label="카카오로 가입하기"
            className="flex h-14 w-14 items-center justify-center rounded-full bg-[#FEE500] transition"
          >
            <KakaoIcon size={28} />
          </button>
          <button
            type="button"
            disabled
            title="준비 중인 기능이에요"
            aria-label="Google로 가입하기"
            className="flex h-14 w-14 items-center justify-center rounded-full border border-gray-200 bg-white transition"
          >
            <GoogleIcon size={28} />
          </button>
        </div>
      </div>
    </main>
  )
}

export default SignUp