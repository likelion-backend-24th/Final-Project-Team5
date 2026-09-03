import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRightIcon,
  BadgeCheckIcon,
  CircleAlertIcon,
  CircleCheckIcon,
  ClockIcon,
} from 'lucide-react'
import { fetchMyHostApplication, submitHostApplication } from '../api/hostApplicationApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './HostApplication.module.css'

const INTRODUCTION_MAX_LENGTH = 1000
const CONTACT_MAX_LENGTH = 255
const REVIEW_STATUSES = ['PENDING', 'APPROVAL_PENDING']

const initialForm = { introduction: '', contact: '' }

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

function validate(form) {
  const errors = {}

  if (!form.introduction.trim()) {
    errors.introduction = '소개를 입력해주세요.'
  } else if (form.introduction.length > INTRODUCTION_MAX_LENGTH) {
    errors.introduction = `소개는 ${INTRODUCTION_MAX_LENGTH}자 이내로 입력해주세요.`
  }

  if (!form.contact.trim()) {
    errors.contact = '연락처를 입력해주세요.'
  } else if (form.contact.length > CONTACT_MAX_LENGTH) {
    errors.contact = `연락처는 ${CONTACT_MAX_LENGTH}자 이내로 입력해주세요.`
  }

  return errors
}

/** 백엔드 스펙(POST /api/host-applications, GET /api/host-applications/me) 기준 주최자 신청 화면. */
function HostApplication() {
  const { user, isLoading: authLoading } = useAuth()
  const isHost = user?.role === 'HOST'

  const [checking, setChecking] = useState(true)
  const [application, setApplication] = useState(null)

  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(null)

  useEffect(() => {
    if (authLoading || isHost) return

    let cancelled = false
    fetchMyHostApplication()
      .then((response) => {
        if (!cancelled) setApplication(response.data.data)
      })
      .catch((error) => {
        if (cancelled) return
        if (error.response?.status !== 404) {
          setSubmitError('신청 내역을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
        }
      })
      .finally(() => {
        if (!cancelled) setChecking(false)
      })

    return () => {
      cancelled = true
    }
  }, [authLoading, isHost])

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
      const response = await submitHostApplication({
        introduction: form.introduction.trim(),
        contact: form.contact.trim(),
      })
      setSubmitted(response.data.data)
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      if (errorCode === 'ALREADY_HOST') {
        setSubmitError('이미 주최자 권한을 가지고 있습니다.')
      } else if (errorCode === 'DUPLICATE_APPLICATION') {
        setSubmitError('이미 처리 대기 중인 신청이 있습니다.')
      } else {
        setSubmitError('신청에 실패했어요. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (authLoading || (!isHost && checking)) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <p className={styles.loading}>불러오는 중…</p>
        </div>
      </main>
    )
  }

  if (submitted) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleCheckIcon size={40} aria-hidden="true" className={styles.infoIconSuccess} />
            <h1 className={styles.infoTitle}>신청이 접수되었습니다</h1>
            <p className={styles.infoDescription}>
              심사 결과는 입력하신 연락처({submitted.contact})로 안내드려요.
            </p>
            <Link to="/" className={styles.infoLink}>
              홈으로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (isHost) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <BadgeCheckIcon size={40} aria-hidden="true" className={styles.infoIconSuccess} />
            <h1 className={styles.infoTitle}>이미 주최자입니다</h1>
            <p className={styles.infoDescription}>주최자 권한으로 페스티벌을 등록할 수 있어요.</p>
            <Link to="/" className={styles.infoLink}>
              홈으로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (application && REVIEW_STATUSES.includes(application.status)) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <ClockIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>이미 심사 중인 신청이 있습니다</h1>
            <p className={styles.infoDescription}>
              신청일 {formatDate(application.createdAt)} · 심사 결과를 조금만 기다려주세요.
            </p>
            <Link to="/" className={styles.infoLink}>
              홈으로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  const wasRejected = application?.status === 'REJECTED'

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>주최자 신청</h1>
        <p className={styles.subtitle}>
          페스티벌을 직접 열고 싶으신가요? 주최자 신청을 통해 승인받아 보세요.
        </p>

        {wasRejected && (
          <p className={styles.rejectNotice}>
            <CircleAlertIcon size={16} aria-hidden="true" />
            지난 신청이 반려되었어요{application.rejectReason ? `: ${application.rejectReason}` : '.'}{' '}
            내용을 보완해 다시 신청해주세요.
          </p>
        )}

        {submitError && (
          <p className={styles.submitError} role="alert">
            <CircleAlertIcon size={16} aria-hidden="true" />
            {submitError}
          </p>
        )}

        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <div className={styles.field}>
            <label htmlFor="introduction" className={styles.label}>
              소개
            </label>
            <textarea
              id="introduction"
              className={styles.textarea}
              placeholder="주최자로서의 활동 경험이나 개최하려는 페스티벌을 소개해 주세요."
              value={form.introduction}
              onChange={handleChange('introduction')}
              maxLength={INTRODUCTION_MAX_LENGTH}
              aria-invalid={Boolean(errors.introduction)}
              rows={6}
            />
            <div className={styles.textareaFooter}>
              {errors.introduction ? (
                <p className={styles.errorText}>{errors.introduction}</p>
              ) : (
                <span />
              )}
              <span className={styles.counter}>
                {form.introduction.length} / {INTRODUCTION_MAX_LENGTH}
              </span>
            </div>
          </div>

          <div className={styles.field}>
            <label htmlFor="contact" className={styles.label}>
              연락처
            </label>
            <input
              id="contact"
              type="text"
              className={styles.input}
              placeholder="연락 가능한 전화번호 또는 이메일을 입력하세요"
              value={form.contact}
              onChange={handleChange('contact')}
              maxLength={CONTACT_MAX_LENGTH}
              aria-invalid={Boolean(errors.contact)}
            />
            {errors.contact ? (
              <p className={styles.errorText}>{errors.contact}</p>
            ) : (
              <p className={styles.hint}>심사 결과를 안내받을 연락처예요.</p>
            )}
          </div>

          <button type="submit" className={styles.submit} disabled={submitting}>
            {submitting ? '신청 중…' : '신청하기'}
            <ArrowRightIcon size={16} aria-hidden="true" />
          </button>
        </form>
      </div>
    </main>
  )
}

export default HostApplication
