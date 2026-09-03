import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRightIcon, CircleAlertIcon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { signup } from '../api/authApi'
import { GoogleIcon, KakaoIcon } from '../components/SocialIcons'
import styles from './AuthForm.module.css'

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
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>회원가입</h1>
        <p className={styles.subtitle}>
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className={styles.crossLink}>
            로그인
          </Link>
        </p>

        {submitError && (
          <p className={styles.submitError} role="alert">
            <CircleAlertIcon size={16} aria-hidden="true" />
            {submitError}
          </p>
        )}

        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <div className={styles.field}>
            <label htmlFor="name" className={styles.label}>
              이름
            </label>
            <input
              id="name"
              type="text"
              className={styles.input}
              placeholder="실명을 입력하세요"
              value={form.name}
              onChange={handleChange('name')}
              aria-invalid={Boolean(errors.name)}
              autoComplete="name"
            />
            {errors.name && <p className={styles.errorText}>{errors.name}</p>}
          </div>

          <div className={styles.field}>
            <label htmlFor="username" className={styles.label}>
              이메일
            </label>
            <div className={styles.emailRow}>
              <input
                id="username"
                type="email"
                className={styles.input}
                placeholder="이메일을 입력하세요"
                value={form.username}
                onChange={handleChange('username')}
                aria-invalid={Boolean(errors.username)}
                autoComplete="email"
              />
              <button
                type="button"
                className={styles.certButton}
                disabled
                title="준비 중인 기능이에요"
              >
                인증코드 받기
              </button>
            </div>
            {errors.username ? (
              <p className={styles.errorText}>{errors.username}</p>
            ) : (
              <p className={styles.hint}>로그인에 사용할 이메일이에요. 인증 후 가입할 수 있어요.</p>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="nickname" className={styles.label}>
              닉네임
            </label>
            <input
              id="nickname"
              type="text"
              className={styles.input}
              placeholder="커뮤니티에서 표시될 이름"
              value={form.nickname}
              onChange={handleChange('nickname')}
              aria-invalid={Boolean(errors.nickname)}
              autoComplete="nickname"
            />
            {errors.nickname ? (
              <p className={styles.errorText}>{errors.nickname}</p>
            ) : (
              <p className={styles.hint}>2~12자, 언제든 변경 가능해요.</p>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="password" className={styles.label}>
              비밀번호
            </label>
            <div className={styles.inputWithButton}>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                className={styles.input}
                placeholder="8자 이상 입력하세요"
                value={form.password}
                onChange={handleChange('password')}
                aria-invalid={Boolean(errors.password)}
                autoComplete="new-password"
              />
              <button
                type="button"
                className={styles.toggleVisibility}
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
              >
                {showPassword ? (
                  <EyeOffIcon size={20} aria-hidden="true" />
                ) : (
                  <EyeIcon size={20} aria-hidden="true" />
                )}
              </button>
            </div>
            {errors.password ? (
              <p className={styles.errorText}>{errors.password}</p>
            ) : (
              <p className={styles.hint}>영문, 숫자를 포함해 8자 이상 입력해주세요.</p>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="passwordConfirm" className={styles.label}>
              비밀번호 확인
            </label>
            <div className={styles.inputWithButton}>
              <input
                id="passwordConfirm"
                type={showPasswordConfirm ? 'text' : 'password'}
                className={styles.input}
                placeholder="비밀번호를 다시 입력하세요"
                value={form.passwordConfirm}
                onChange={handleChange('passwordConfirm')}
                aria-invalid={Boolean(errors.passwordConfirm)}
                autoComplete="new-password"
              />
              <button
                type="button"
                className={styles.toggleVisibility}
                onClick={() => setShowPasswordConfirm((value) => !value)}
                aria-label={showPasswordConfirm ? '비밀번호 숨기기' : '비밀번호 표시'}
              >
                {showPasswordConfirm ? (
                  <EyeOffIcon size={20} aria-hidden="true" />
                ) : (
                  <EyeIcon size={20} aria-hidden="true" />
                )}
              </button>
            </div>
            {errors.passwordConfirm && (
              <p className={styles.errorText}>{errors.passwordConfirm}</p>
            )}
          </div>

          <label className={styles.agreement}>
            <input
              type="checkbox"
              checked={agreed}
              onChange={(event) => setAgreed(event.target.checked)}
            />
            <span>
              <Link to="/terms" className={styles.crossLink}>
                이용약관
              </Link>{' '}
              및{' '}
              <Link to="/privacy" className={styles.crossLink}>
                개인정보처리방침
              </Link>
              에 동의합니다. <span className={styles.agreementRequired}>(필수)</span>
            </span>
          </label>

          <button type="submit" className={styles.submit} disabled={!agreed || submitting}>
            {submitting ? '가입 중…' : '가입하기'}
            <ArrowRightIcon size={16} aria-hidden="true" />
          </button>
        </form>

        <div className={styles.divider}>
          <span>또는</span>
        </div>

        <div className={styles.social}>
          <button
            type="button"
            className={styles.socialKakao}
            disabled
            title="준비 중인 기능이에요"
            aria-label="카카오로 가입하기"
          >
            <KakaoIcon size={28} />
          </button>
          <button
            type="button"
            className={styles.socialGoogle}
            disabled
            title="준비 중인 기능이에요"
            aria-label="Google로 가입하기"
          >
            <GoogleIcon size={28} />
          </button>
        </div>
      </div>
    </main>
  )
}

export default SignUp
