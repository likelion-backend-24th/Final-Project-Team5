import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRightIcon, CircleAlertIcon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { GoogleIcon, KakaoIcon } from '../components/SocialIcons'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(form) {
  const errors = {}

  if (!form.username.trim()) {
    errors.username = '이메일을 입력해주세요.'
  } else if (!EMAIL_PATTERN.test(form.username.trim())) {
    errors.username = '올바른 이메일 형식이 아니에요.'
  }

  if (!form.password) {
    errors.password = '비밀번호를 입력해주세요.'
  }

  return errors
}

const inputBaseClass =
  'w-full rounded-2xl border bg-white px-4 py-3 text-[15px] text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'

function inputClass(hasError) {
  return `${inputBaseClass} ${hasError ? 'border-red-500' : 'border-gray-300'}`
}

/** feval-go wireframe 기준 로그인 화면. */
function Login() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [form, setForm] = useState({ username: '', password: '' })
  const [errors, setErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)
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

    const nextErrors = validate(form)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    setSubmitError('')

    try {
      await login(form.username.trim(), form.password)
      navigate('/')
    } catch (error) {
      if (error.response?.status === 401) {
        setSubmitError('이메일 또는 비밀번호가 올바르지 않아요.')
      } else {
        setSubmitError('로그인에 실패했어요. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 pt-6 pb-14 sm:pt-10 sm:pb-20">
      <div className="h-fit w-full max-w-md rounded-3xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">로그인</h1>
        <p className="mt-2 text-sm text-gray-500">
          계정이 없으신가요?{' '}
          <Link to="/signup" className="font-bold text-blue-600 hover:underline">
            회원가입
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
            <label htmlFor="username" className="block text-sm font-bold text-gray-900">
              이메일
            </label>
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
            {errors.username && <p className="text-xs font-semibold text-red-500">{errors.username}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="password" className="block text-sm font-bold text-gray-900">
              비밀번호
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="비밀번호를 입력하세요"
                value={form.password}
                onChange={handleChange('password')}
                aria-invalid={Boolean(errors.password)}
                autoComplete="current-password"
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
            {errors.password && <p className="text-xs font-semibold text-red-500">{errors.password}</p>}
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 py-3.5 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            {submitting ? '로그인 중…' : '로그인'}
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
            aria-label="카카오 로그인"
            className="flex h-14 w-14 items-center justify-center rounded-full bg-[#FEE500] transition"
          >
            <KakaoIcon size={28} />
          </button>
          <button
            type="button"
            disabled
            title="준비 중인 기능이에요"
            aria-label="Google 로그인"
            className="flex h-14 w-14 items-center justify-center rounded-full border border-gray-200 bg-white transition"
          >
            <GoogleIcon size={28} />
          </button>
        </div>

        <p className="mt-6 text-center text-sm text-gray-500">
          비밀번호를 잊으셨나요?{' '}
          <Link to="/reset-password" className="font-bold text-blue-600 hover:underline">
            비밀번호 재설정
          </Link>
        </p>
      </div>
    </main>
  )
}

export default Login