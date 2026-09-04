import { useEffect, useState } from 'react'
import { CircleAlertIcon, CircleCheckIcon, ClockIcon } from 'lucide-react'
import { fetchPendingHostApplications, reviewHostApplication } from '../api/adminApi'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './AdminList.module.css'

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const ERROR_MESSAGES = {
  FORBIDDEN_ROLE: '운영자 권한이 없습니다.',
  APPLICATION_NOT_FOUND: '존재하지 않는 신청입니다. 목록을 새로고침해주세요.',
  ALREADY_REVIEWED: '이미 처리되었거나 Role 부여 처리 중인 신청입니다. 목록을 새로고침해주세요.',
  REJECT_REASON_REQUIRED: '반려 사유를 입력해주세요.',
}

/** 백엔드 스펙(GET/PATCH /api/admin/host-applications) 기준 운영자 주최 신청 심사 화면. */
function AdminHostApplications() {
  const { user, isLoading: authLoading } = useAuth()
  const isAdmin = user?.role === 'ADMIN'

  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionErrors, setActionErrors] = useState({})
  const [pendingActionId, setPendingActionId] = useState(null)
  const [rejectDraftId, setRejectDraftId] = useState(null)
  const [rejectReason, setRejectReason] = useState('')

  function loadApplications() {
    setLoading(true)
    setLoadError('')
    fetchPendingHostApplications()
      .then((response) => setApplications(response.data.data))
      .catch(() => setLoadError('심사 대기 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (authLoading || !isAdmin) return
    loadApplications()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, isAdmin])

  function setActionError(id, message) {
    setActionErrors((prev) => ({ ...prev, [id]: message }))
  }

  async function handleApprove(id) {
    setPendingActionId(id)
    setActionError(id, '')
    try {
      await reviewHostApplication(id, { status: 'APPROVED' })
      setApplications((prev) => prev.filter((application) => application.id !== id))
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      setActionError(id, ERROR_MESSAGES[errorCode] ?? '승인 처리에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setPendingActionId(null)
    }
  }

  function openRejectDraft(id) {
    setRejectDraftId(id)
    setRejectReason('')
    setActionError(id, '')
  }

  function cancelRejectDraft() {
    setRejectDraftId(null)
    setRejectReason('')
  }

  async function handleReject(id) {
    if (!rejectReason.trim()) {
      setActionError(id, ERROR_MESSAGES.REJECT_REASON_REQUIRED)
      return
    }

    setPendingActionId(id)
    setActionError(id, '')
    try {
      await reviewHostApplication(id, { status: 'REJECTED', rejectReason: rejectReason.trim() })
      setApplications((prev) => prev.filter((application) => application.id !== id))
      setRejectDraftId(null)
      setRejectReason('')
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      setActionError(id, ERROR_MESSAGES[errorCode] ?? '반려 처리에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setPendingActionId(null)
    }
  }

  if (authLoading) {
    return (
      <main className={styles.main}>
        <p className={styles.loading}>불러오는 중…</p>
      </main>
    )
  }

  if (!isAdmin) {
    return (
      <main className={styles.main}>
        <div className={styles.forbidden}>
          <CircleAlertIcon size={40} aria-hidden="true" />
          <h1 className={styles.forbiddenTitle}>운영자 권한이 필요합니다</h1>
          <p className={styles.forbiddenDescription}>이 페이지는 운영자(ADMIN)만 볼 수 있어요.</p>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>주최자 신청 심사</h1>
        <p className={styles.count}>심사 대기 {applications.length}건</p>
      </div>

      {loading && <p className={styles.loading}>불러오는 중…</p>}

      {!loading && loadError && (
        <p className={styles.loadError} role="alert">
          <CircleAlertIcon size={16} aria-hidden="true" />
          {loadError}
        </p>
      )}

      {!loading && !loadError && applications.length === 0 && (
        <div className={styles.emptyState}>
          <CircleCheckIcon size={32} aria-hidden="true" />
          <p>심사 대기 중인 주최자 신청이 없어요.</p>
        </div>
      )}

      {!loading && !loadError && applications.length > 0 && (
        <ul className={styles.list}>
          {applications.map((application) => (
            <li key={application.id} className={styles.card}>
              <div className={styles.cardHeader}>
                <span className={styles.badge}>
                  <ClockIcon size={12} aria-hidden="true" />
                  심사 대기
                </span>
                <span className={styles.date}>{formatDate(application.createdAt)}</span>
              </div>

              <p className={styles.introduction}>{application.introduction}</p>
              <p className={styles.contact}>연락처: {application.contact}</p>

              {actionErrors[application.id] && (
                <p className={styles.actionError} role="alert">
                  <CircleAlertIcon size={14} aria-hidden="true" />
                  {actionErrors[application.id]}
                </p>
              )}

              {rejectDraftId === application.id ? (
                <div className={styles.rejectDraft}>
                  <textarea
                    className={styles.rejectTextarea}
                    placeholder="반려 사유를 입력하세요"
                    value={rejectReason}
                    onChange={(event) => setRejectReason(event.target.value)}
                    rows={3}
                  />
                  <div className={styles.actions}>
                    <button
                      type="button"
                      className={styles.cancelButton}
                      onClick={cancelRejectDraft}
                      disabled={pendingActionId === application.id}
                    >
                      취소
                    </button>
                    <button
                      type="button"
                      className={styles.rejectButton}
                      onClick={() => handleReject(application.id)}
                      disabled={pendingActionId === application.id}
                    >
                      {pendingActionId === application.id ? '처리 중…' : '반려 확정'}
                    </button>
                  </div>
                </div>
              ) : (
                <div className={styles.actions}>
                  <button
                    type="button"
                    className={styles.rejectButton}
                    onClick={() => openRejectDraft(application.id)}
                    disabled={pendingActionId === application.id}
                  >
                    반려
                  </button>
                  <button
                    type="button"
                    className={styles.approveButton}
                    onClick={() => handleApprove(application.id)}
                    disabled={pendingActionId === application.id}
                  >
                    {pendingActionId === application.id ? '처리 중…' : '승인'}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

export default AdminHostApplications
