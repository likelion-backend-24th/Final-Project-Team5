import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import axios from 'axios'
import { ArrowRightIcon, CircleAlertIcon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { GoogleIcon, KakaoIcon } from '../components/SocialIcons'
import styles from './AuthForm.module.css'

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

/** feval-go wireframe 기준 로그인 화면. */
function Login() {
  const navigate = useNavigate()
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
      await axios.post('/api/auth/login', {
        username: form.username.trim(),
        password: form.password,
      })
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
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>로그인</h1>
        <p className={styles.subtitle}>
          계정이 없으신가요?{' '}
          <Link to="/signup" className={styles.crossLink}>
            회원가입
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
            <label htmlFor="username" className={styles.label}>
              이메일
            </label>
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
            {errors.username && <p className={styles.errorText}>{errors.username}</p>}
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
                placeholder="비밀번호를 입력하세요"
                value={form.password}
                onChange={handleChange('password')}
                aria-invalid={Boolean(errors.password)}
                autoComplete="current-password"
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
            {errors.password && <p className={styles.errorText}>{errors.password}</p>}
          </div>

          <button type="submit" className={styles.submit} disabled={submitting}>
            {submitting ? '로그인 중…' : '로그인'}
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
            aria-label="카카오 로그인"
          >
            <KakaoIcon size={28} />
          </button>
          <button
            type="button"
            className={styles.socialGoogle}
            disabled
            title="준비 중인 기능이에요"
            aria-label="Google 로그인"
          >
            <GoogleIcon size={28} />
          </button>
        </div>

        <p className={styles.footNote}>
          비밀번호를 잊으셨나요?{' '}
          <Link to="/reset-password" className={styles.crossLink}>
            비밀번호 재설정
          </Link>
        </p>
      </div>
    </main>
  )
}

export default Login
