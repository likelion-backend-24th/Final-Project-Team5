import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRightIcon,
  CircleAlertIcon,
  CircleCheckIcon,
  InfoIcon,
  LockIcon,
  PlusIcon,
  Trash2Icon,
} from 'lucide-react'
import { createFestival } from '../api/hostFestivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './HostFestivalNew.module.css'

const CATEGORY_OPTIONS = [
  { value: 'MUSIC', label: '음악' },
  { value: 'LOCAL', label: '지역 행사' },
]

function createEmptyTicketType(key) {
  return { key, name: '', price: '', quantity: '' }
}

function validateTicketType(ticket) {
  const errors = {}

  if (!ticket.name.trim()) {
    errors.name = '이름을 입력해주세요.'
  }

  if (ticket.price.trim() === '') {
    errors.price = '가격을 입력해주세요.'
  } else if (Number.isNaN(Number(ticket.price)) || Number(ticket.price) < 0) {
    errors.price = '0 이상의 숫자를 입력해주세요.'
  }

  if (ticket.quantity.trim() === '') {
    errors.quantity = '수량을 입력해주세요.'
  } else if (!Number.isInteger(Number(ticket.quantity)) || Number(ticket.quantity) < 1) {
    errors.quantity = '1 이상의 정수를 입력해주세요.'
  }

  return errors
}

function validate(form) {
  const fieldErrors = {}

  if (!form.name.trim()) {
    fieldErrors.name = '페스티벌 이름을 입력해주세요.'
  }
  if (!form.startAt) {
    fieldErrors.startAt = '시작 일시를 입력해주세요.'
  }
  if (!form.endAt) {
    fieldErrors.endAt = '종료 일시를 입력해주세요.'
  } else if (form.startAt && form.endAt <= form.startAt) {
    fieldErrors.endAt = '종료 일시는 시작 일시 이후여야 해요.'
  }
  if (!form.location.trim()) {
    fieldErrors.location = '장소를 입력해주세요.'
  }
  if (!form.festivalCategory) {
    fieldErrors.festivalCategory = '카테고리를 선택해주세요.'
  }

  const ticketErrors = {}
  form.ticketTypes.forEach((ticket) => {
    const errors = validateTicketType(ticket)
    if (Object.keys(errors).length > 0) ticketErrors[ticket.key] = errors
  })

  if (form.ticketTypes.length === 0) {
    fieldErrors.ticketTypes = '티켓 종류를 최소 1개 이상 등록해주세요.'
  } else if (Object.keys(ticketErrors).length > 0) {
    fieldErrors.ticketTypes = '티켓 종류 정보를 확인해주세요.'
  }

  return { fieldErrors, ticketErrors }
}

/** 백엔드 스펙(POST /api/host/festivals) 기준 주최자용 페스티벌 등록 화면. */
function HostFestivalNew() {
  const { user, isLoading: authLoading } = useAuth()
  const isHost = user?.role === 'HOST'
  const ticketKeySeq = useRef(0)

  const [form, setForm] = useState(() => ({
    name: '',
    description: '',
    startAt: '',
    endAt: '',
    location: '',
    festivalCategory: 'MUSIC',
    ticketTypes: [createEmptyTicketType(0)],
  }))
  const [errors, setErrors] = useState({})
  const [ticketErrors, setTicketErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(null)

  function handleChange(field) {
    return (event) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [field]: value }))
      setErrors((prev) => ({ ...prev, [field]: undefined }))
      setSubmitError('')
    }
  }

  function handleCategorySelect(value) {
    setForm((prev) => ({ ...prev, festivalCategory: value }))
    setErrors((prev) => ({ ...prev, festivalCategory: undefined }))
  }

  function handleTicketChange(key, field) {
    return (event) => {
      const { value } = event.target
      setForm((prev) => ({
        ...prev,
        ticketTypes: prev.ticketTypes.map((ticket) =>
          ticket.key === key ? { ...ticket, [field]: value } : ticket,
        ),
      }))
      setTicketErrors((prev) => ({
        ...prev,
        [key]: { ...prev[key], [field]: undefined },
      }))
      setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
      setSubmitError('')
    }
  }

  function handleAddTicket() {
    ticketKeySeq.current += 1
    setForm((prev) => ({
      ...prev,
      ticketTypes: [...prev.ticketTypes, createEmptyTicketType(ticketKeySeq.current)],
    }))
    setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
  }

  function handleRemoveTicket(key) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.filter((ticket) => ticket.key !== key),
    }))
    setTicketErrors((prev) => {
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  async function handleSubmit(event) {
    event.preventDefault()

    const { fieldErrors, ticketErrors: nextTicketErrors } = validate(form)
    setErrors(fieldErrors)
    setTicketErrors(nextTicketErrors)
    if (Object.keys(fieldErrors).length > 0) return

    setSubmitting(true)
    setSubmitError('')

    try {
      const response = await createFestival({
        name: form.name.trim(),
        description: form.description.trim(),
        startAt: form.startAt,
        endAt: form.endAt,
        location: form.location.trim(),
        festivalCategory: form.festivalCategory,
        ticketTypes: form.ticketTypes.map((ticket) => ({
          name: ticket.name.trim(),
          price: Number(ticket.price),
          quantity: Number(ticket.quantity),
        })),
      })
      setSubmitted(response.data.data)
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      if (errorCode === 'FORBIDDEN_ROLE') {
        setSubmitError('주최자 권한이 없습니다.')
      } else {
        setSubmitError('등록에 실패했어요. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (authLoading) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <p className={styles.loading}>불러오는 중…</p>
        </div>
      </main>
    )
  }

  if (!isHost) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <LockIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>주최자만 이용 가능한 페이지입니다</h1>
            <p className={styles.infoDescription}>
              페스티벌을 등록하려면 먼저 주최자 신청 후 승인을 받아주세요.
            </p>
            <Link to="/host-application" className={styles.infoButton}>
              주최자 신청하러 가기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
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
            <h1 className={styles.infoTitle}>등록이 완료되었습니다</h1>
            <p className={styles.infoDescription}>
              운영자 승인 후 공개됩니다. ({submitted.name})
            </p>
            <Link to="/" className={styles.infoLink}>
              홈으로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.title}>페스티벌 등록</h1>

        <p className={styles.banner}>
          <InfoIcon size={16} aria-hidden="true" />
          등록 후 운영자 승인이 완료되어야 목록에 공개됩니다.
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
              페스티벌 이름
            </label>
            <input
              id="name"
              type="text"
              className={styles.input}
              placeholder="페스티벌 이름을 입력하세요"
              value={form.name}
              onChange={handleChange('name')}
              aria-invalid={Boolean(errors.name)}
            />
            {errors.name && <p className={styles.errorText}>{errors.name}</p>}
          </div>

          <div className={styles.field}>
            <label htmlFor="description" className={styles.label}>
              소개 <span className={styles.optional}>(선택)</span>
            </label>
            <textarea
              id="description"
              className={styles.textarea}
              placeholder="페스티벌을 소개해 주세요."
              value={form.description}
              onChange={handleChange('description')}
              rows={5}
            />
          </div>

          <div className={styles.row}>
            <div className={styles.field}>
              <label htmlFor="startAt" className={styles.label}>
                시작 일시
              </label>
              <input
                id="startAt"
                type="datetime-local"
                className={styles.input}
                value={form.startAt}
                onChange={handleChange('startAt')}
                aria-invalid={Boolean(errors.startAt)}
              />
              {errors.startAt && <p className={styles.errorText}>{errors.startAt}</p>}
            </div>

            <div className={styles.field}>
              <label htmlFor="endAt" className={styles.label}>
                종료 일시
              </label>
              <input
                id="endAt"
                type="datetime-local"
                className={styles.input}
                value={form.endAt}
                onChange={handleChange('endAt')}
                aria-invalid={Boolean(errors.endAt)}
              />
              {errors.endAt && <p className={styles.errorText}>{errors.endAt}</p>}
            </div>
          </div>

          <div className={styles.field}>
            <label htmlFor="location" className={styles.label}>
              장소
            </label>
            <input
              id="location"
              type="text"
              className={styles.input}
              placeholder="예: 서울 잠실 올림픽주경기장"
              value={form.location}
              onChange={handleChange('location')}
              aria-invalid={Boolean(errors.location)}
            />
            {errors.location && <p className={styles.errorText}>{errors.location}</p>}
          </div>

          <div className={styles.field}>
            <span className={styles.label}>카테고리</span>
            <div className={styles.categoryGroup} role="radiogroup" aria-label="카테고리">
              {CATEGORY_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={form.festivalCategory === option.value}
                  className={`${styles.categoryOption} ${
                    form.festivalCategory === option.value ? styles.categoryOptionActive : ''
                  }`}
                  onClick={() => handleCategorySelect(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
            {errors.festivalCategory && <p className={styles.errorText}>{errors.festivalCategory}</p>}
          </div>

          <div className={styles.field}>
            <div className={styles.ticketHeader}>
              <span className={styles.label}>티켓 종류</span>
              <span className={styles.ticketHint}>최소 1개 이상 등록해야 합니다.</span>
            </div>

            {errors.ticketTypes && <p className={styles.errorText}>{errors.ticketTypes}</p>}

            <div className={styles.ticketList}>
              {form.ticketTypes.map((ticket, index) => {
                const rowErrors = ticketErrors[ticket.key] ?? {}
                return (
                  <div className={styles.ticketRow} key={ticket.key}>
                    <div className={styles.ticketRowHeader}>
                      <span className={styles.ticketRowTitle}>티켓 {index + 1}</span>
                      <button
                        type="button"
                        className={styles.ticketRemove}
                        onClick={() => handleRemoveTicket(ticket.key)}
                      >
                        <Trash2Icon size={14} aria-hidden="true" />
                        삭제
                      </button>
                    </div>
                    <div className={styles.ticketFields}>
                      <div className={styles.ticketField}>
                        <input
                          type="text"
                          className={styles.input}
                          placeholder="이름 (예: 일반)"
                          value={ticket.name}
                          onChange={handleTicketChange(ticket.key, 'name')}
                          aria-invalid={Boolean(rowErrors.name)}
                          aria-label={`티켓 ${index + 1} 이름`}
                        />
                        {rowErrors.name && <p className={styles.errorText}>{rowErrors.name}</p>}
                      </div>
                      <div className={styles.ticketField}>
                        <input
                          type="number"
                          min="0"
                          className={styles.input}
                          placeholder="가격 (원)"
                          value={ticket.price}
                          onChange={handleTicketChange(ticket.key, 'price')}
                          aria-invalid={Boolean(rowErrors.price)}
                          aria-label={`티켓 ${index + 1} 가격`}
                        />
                        {rowErrors.price && <p className={styles.errorText}>{rowErrors.price}</p>}
                      </div>
                      <div className={styles.ticketField}>
                        <input
                          type="number"
                          min="1"
                          className={styles.input}
                          placeholder="수량"
                          value={ticket.quantity}
                          onChange={handleTicketChange(ticket.key, 'quantity')}
                          aria-invalid={Boolean(rowErrors.quantity)}
                          aria-label={`티켓 ${index + 1} 수량`}
                        />
                        {rowErrors.quantity && <p className={styles.errorText}>{rowErrors.quantity}</p>}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>

            <button type="button" className={styles.addTicket} onClick={handleAddTicket}>
              <PlusIcon size={16} aria-hidden="true" />
              티켓 종류 추가
            </button>
          </div>

          <button type="submit" className={styles.submit} disabled={submitting}>
            {submitting ? '등록 중…' : '등록하기'}
            <ArrowRightIcon size={16} aria-hidden="true" />
          </button>
        </form>
      </div>
    </main>
  )
}

export default HostFestivalNew
