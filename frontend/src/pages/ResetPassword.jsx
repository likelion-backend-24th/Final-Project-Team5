import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRightIcon, CheckCircle2Icon, CircleAlertIcon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { resetPassword, sendEmailVerificationCode, verifyEmailVerificationCode } from '../api/authApi'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const RESEND_COOLDOWN_SECONDS = 30

const inputClass =
  'w-full rounded-2xl border border-blue-600 bg-white px-4 py-3.5 text-[15px] text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-500/20'

const primaryBtn =
  'flex w-full cursor-pointer items-center justify-center gap-2 rounded-2xl bg-blue-600 py-3.5 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'

const secondaryBtn =
  'shrink-0 cursor-pointer whitespace-nowrap rounded-2xl border border-gray-300 bg-gray-50 px-4 py-3.5 text-sm font-semibold text-gray-600 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-100 disabled:text-gray-400'

function extractError(error, fallbackMessage) {
  const data = error.response?.data
  return { errorCode: data?.errorCode, message: data?.message || fallbackMessage }
}

/** feval-go wireframe 기준 비밀번호 재설정 화면. v0 시안의 단계별 위저드(email → code → password → done)를 따른다. */
function ResetPassword() {
  const [step, setStep] = useState('email')
  const [topError, setTopError] = useState('')

  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [sendingCode, setSendingCode] = useState(false)

  const [emailCode, setEmailCode] = useState('')
  const [verifyingCode, setVerifyingCode] = useState(false)
  const [verifyCodeError, setVerifyCodeError] = useState('')
  const [sendCodeError, setSendCodeError] = useState('')
  const [resendCooldown, setResendCooldown] = useState(0)

  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [passwordError, setPasswordError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const pwMismatch = newPasswordConfirm.length > 0 && newPassword !== newPasswordConfirm

  useEffect(() => {
    if (resendCooldown <= 0) return
    const timer = setInterval(() => {
      setResendCooldown((seconds) => (seconds <= 1 ? 0 : seconds - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [resendCooldown])

  function handleEmailChange(event) {
    setEmail(event.target.value)
    setEmailError('')
    setTopError('')
  }

  async function handleSendCode(event) {
    event?.preventDefault()
    const trimmedEmail = email.trim()
    if (!trimmedEmail) return

    if (!EMAIL_PATTERN.test(trimmedEmail)) {
      setEmailError('올바른 이메일 형식이 아니에요.')
      return
    }

    setSendingCode(true)
    setEmailError('')
    setSendCodeError('')

    try {
      await sendEmailVerificationCode(trimmedEmail)
      setEmailCode('')
      setVerifyCodeError('')
      setResendCooldown(RESEND_COOLDOWN_SECONDS)
      setStep('code')
    } catch (error) {
      const { errorCode, message } = extractError(
        error,
        '인증코드 발송에 실패했어요. 잠시 후 다시 시도해주세요.',
      )
      if (errorCode === 'TOO_MANY_REQUESTS_COOLDOWN') {
        setResendCooldown(RESEND_COOLDOWN_SECONDS)
        setSendCodeError(message)
        setStep('code')
      } else if (step === 'email') {
        setEmailError(message)
      } else {
        setSendCodeError(message)
      }
    } finally {
      setSendingCode(false)
    }
  }

  async function handleVerifyCode(event) {
    event.preventDefault()
    const code = emailCode.trim()
    if (!code) return

    setVerifyingCode(true)
    setVerifyCodeError('')

    try {
      await verifyEmailVerificationCode({ email: email.trim(), code })
      setStep('password')
    } catch (error) {
      const { message } = extractError(error, '인증코드가 올바르지 않아요.')
      setVerifyCodeError(message)
    } finally {
      setVerifyingCode(false)
    }
  }

  async function handleSubmitPassword(event) {
    event.preventDefault()
    if (newPassword.length < 8 || newPassword !== newPasswordConfirm) return

    setSubmitting(true)
    setPasswordError('')

    try {
      await resetPassword({ username: email.trim(), newPassword })
      setStep('done')
    } catch (error) {
      const { errorCode, message } = extractError(
        error,
        '비밀번호 재설정에 실패했어요. 잠시 후 다시 시도해주세요.',
      )

      if (errorCode === 'EMAIL_NOT_VERIFIED') {
        setStep('email')
        setTopError('이메일 인증이 만료됐어요. 인증코드를 다시 받아주세요.')
      } else {
        setPasswordError(message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex flex-1 justify-center bg-gray-50 px-6 pt-6 pb-14 sm:pt-10 sm:pb-20">
      <div className="h-fit w-full max-w-xl overflow-hidden rounded-3xl border-2 border-blue-600 bg-white shadow-sm">
        <div className="p-10 md:p-14">
          <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">비밀번호 재설정</h1>
          {step !== 'done' && (
            <p className="mt-2 text-sm text-gray-500">
              계정이 기억나셨나요?{' '}
              <Link to="/login" className="cursor-pointer font-bold text-blue-600 hover:underline">
                로그인
              </Link>
            </p>
          )}

          {topError && (
            <p
              role="alert"
              className="mt-4 flex items-center gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-[13px] font-semibold text-red-600"
            >
              <CircleAlertIcon className="h-4 w-4" />
              {topError}
            </p>
          )}

          {step === 'email' && (
            <form className="mt-7 space-y-5" onSubmit={handleSendCode} noValidate>
              <div className="space-y-2">
                <label htmlFor="reset-email" className="block text-sm font-bold text-gray-900">
                  이메일
                </label>
                <input
                  id="reset-email"
                  type="email"
                  value={email}
                  onChange={handleEmailChange}
                  placeholder="가입 시 사용한 이메일을 입력하세요"
                  aria-invalid={Boolean(emailError)}
                  autoComplete="email"
                  className={inputClass}
                />
                {emailError ? (
                  <p className="text-sm font-semibold text-red-500">{emailError}</p>
                ) : (
                  <p className="text-sm text-gray-400">입력한 이메일로 인증코드를 보내드려요.</p>
                )}
              </div>
              <button type="submit" className={primaryBtn} disabled={sendingCode || !email.trim()}>
                {sendingCode ? '발송 중…' : '인증코드 받기'}
                <ArrowRightIcon className="h-4 w-4" />
              </button>
            </form>
          )}

          {step === 'code' && (
            <form className="mt-7 space-y-5" onSubmit={handleVerifyCode} noValidate>
              <div className="space-y-2">
                <label htmlFor="reset-code" className="block text-sm font-bold text-gray-900">
                  인증코드
                </label>
                <div className="flex gap-2">
                  <input
                    id="reset-code"
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={emailCode}
                    onChange={(event) => {
                      setEmailCode(event.target.value)
                      setVerifyCodeError('')
                    }}
                    placeholder="이메일로 받은 6자리 코드를 입력하세요"
                    aria-invalid={Boolean(verifyCodeError)}
                    className={`${inputClass} min-w-0 flex-1`}
                  />
                  <button
                    type="button"
                    onClick={handleSendCode}
                    disabled={sendingCode || resendCooldown > 0}
                    className={secondaryBtn}
                  >
                    {resendCooldown > 0 ? `재발송 (${resendCooldown}초)` : '재발송'}
                  </button>
                </div>
                <p className="text-sm text-gray-400">
                  <span className="font-semibold text-gray-500">{email}</span> 로 코드를 보냈어요.{' '}
                  <button
                    type="button"
                    onClick={() => setStep('email')}
                    className="cursor-pointer font-bold text-blue-600 hover:underline"
                  >
                    이메일 변경
                  </button>
                </p>
                <p className="text-sm font-semibold text-blue-600">
                  인증코드를 발송했어요. 메일함을 확인해주세요. (유효시간 5분)
                </p>
                {sendCodeError && <p className="text-sm font-semibold text-red-500">{sendCodeError}</p>}
                {verifyCodeError && <p className="text-sm font-semibold text-red-500">{verifyCodeError}</p>}
              </div>
              <button type="submit" className={primaryBtn} disabled={verifyingCode || !emailCode.trim()}>
                {verifyingCode ? '확인 중…' : '인증하기'}
                <ArrowRightIcon className="h-4 w-4" />
              </button>
            </form>
          )}

          {step === 'password' && (
            <form className="mt-7 space-y-5" onSubmit={handleSubmitPassword} noValidate>
              <div className="space-y-2">
                <label htmlFor="new-password" className="block text-sm font-bold text-gray-900">
                  새 비밀번호
                </label>
                <div className="relative">
                  <input
                    id="new-password"
                    type={showPassword ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(event) => {
                      setNewPassword(event.target.value)
                      setPasswordError('')
                    }}
                    placeholder="8자 이상 입력하세요"
                    autoComplete="new-password"
                    className={`${inputClass} pr-12`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 cursor-pointer text-gray-400 hover:text-gray-600"
                    aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                  >
                    {showPassword ? <EyeOffIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
                  </button>
                </div>
              </div>
              <div className="space-y-2">
                <label htmlFor="new-password-confirm" className="block text-sm font-bold text-gray-900">
                  새 비밀번호 확인
                </label>
                <input
                  id="new-password-confirm"
                  type={showPassword ? 'text' : 'password'}
                  value={newPasswordConfirm}
                  onChange={(event) => {
                    setNewPasswordConfirm(event.target.value)
                    setPasswordError('')
                  }}
                  placeholder="비밀번호를 다시 입력하세요"
                  autoComplete="new-password"
                  className={inputClass}
                />
                {pwMismatch && (
                  <p className="text-sm font-semibold text-red-500">비밀번호가 일치하지 않아요.</p>
                )}
                {passwordError && <p className="text-sm font-semibold text-red-500">{passwordError}</p>}
              </div>
              <button
                type="submit"
                className={primaryBtn}
                disabled={submitting || newPassword.length < 8 || newPassword !== newPasswordConfirm}
              >
                {submitting ? '변경 중…' : '비밀번호 변경'}
                <ArrowRightIcon className="h-4 w-4" />
              </button>
            </form>
          )}

          {step === 'done' && (
            <div className="mt-7 flex flex-col items-center rounded-2xl border border-blue-100 bg-blue-50 px-6 py-10 text-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-blue-600 text-white">
                <CheckCircle2Icon className="h-8 w-8" />
              </div>
              <p className="mt-5 text-base font-extrabold text-gray-900">비밀번호가 변경되었어요</p>
              <p className="mt-1 text-sm text-gray-500">새 비밀번호로 다시 로그인해 주세요.</p>
              <Link to="/login" className={`${primaryBtn} mt-6 max-w-xs`}>
                로그인하러 가기
                <ArrowRightIcon className="h-4 w-4" />
              </Link>
            </div>
          )}
        </div>
      </div>
    </main>
  )
}

export default ResetPassword
