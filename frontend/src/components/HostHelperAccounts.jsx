import { useCallback, useEffect, useRef, useState } from 'react'
import { UsersIcon } from 'lucide-react'
import { createHelperAccount, fetchHelperAccounts, resendHelperInvitation, revokeHelperAccount, convertLegacyHelper } from '../api/hostFestivalApi'

const labels = { PENDING: '초대 대기', ACCEPTED: '활성화 완료', ACTIVE: '활성화 완료', EXPIRED: '만료', REVOKED: '해지' }
const errors = {
  INVITATION_DUPLICATE: '이미 초대한 이메일이에요. 목록의 재발송을 이용해주세요.',
  INVITATION_COOLDOWN: '최근 발송 후 60초 뒤 다시 시도해주세요.',
  INVITATION_SEND_LIMIT: '24시간 동안 최대 10회 발송할 수 있어요.',
  INVITATION_SEND_FAILED: '메일 발송에 실패했어요. 계정은 보존되었으니 목록에서 재발송해주세요.',
  HELPER_FESTIVAL_ENDED: '종료된 행사에는 초대할 수 없어요.',
}
function message(error) { return errors[error.response?.data?.errorCode] || error.response?.data?.message || '요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.' }
function date(value) { return value ? new Date(value).toLocaleString('ko-KR') : '—' }
const inputClass = 'w-full rounded-xl border border-gray-300 px-3 py-2 text-sm'
const buttonClass = 'rounded-xl border border-gray-300 px-3 py-2 text-sm font-bold disabled:opacity-40'

export default function HostHelperAccounts({ festivalId }) {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [email, setEmail] = useState('')
  const [legacyEmails, setLegacyEmails] = useState({})
  const [working, setWorking] = useState(false)
  const busy = useRef(false)
  const load = useCallback(async () => {
    try { setSummary((await fetchHelperAccounts(festivalId)).data.data) }
    catch (err) { setError(message(err)) }
    finally { setLoading(false) }
  }, [festivalId])
  useEffect(() => {
    let cancelled = false
    fetchHelperAccounts(festivalId).then(response => { if (!cancelled) setSummary(response.data.data) })
      .catch(err => { if (!cancelled) setError(message(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [festivalId])

  async function act(action, success) {
    if (busy.current) return
    busy.current = true
    setWorking(true)
    setError('')
    setNotice('')
    try { await action(); setNotice(success) }
    catch (err) { setError(message(err)) }
    finally { await load(); busy.current = false; setWorking(false) }
  }
  function invite(event) {
    event.preventDefault()
    act(async () => { await createHelperAccount(festivalId, email.trim().toLowerCase()); setEmail('') }, '초대 메일을 발송했어요.')
  }
  return <section className="mt-10 rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
    <h2 className="flex items-center gap-2 text-lg font-extrabold"><UsersIcon className="h-5 w-5 text-blue-600" />도우미 이메일 초대</h2>
    <p className="mt-2 text-sm text-gray-500">수신 이메일로 비밀번호 설정 링크를 보내요. 로그인에는 별도로 발급된 아이디를 사용해요. 행사 종료 시 이용이 중지되고 24시간 뒤 계정과 연락처가 삭제돼요.</p>
    <form onSubmit={invite} className="mt-4 flex flex-wrap items-end gap-2">
      <label className="min-w-0 flex-1 text-sm font-bold">초대 이메일
        <input aria-label="초대 이메일" className={inputClass} type="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} autoComplete="email" />
      </label>
      <button className={buttonClass} disabled={working} type="submit">이메일 초대</button>
    </form>
    {error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}
    {notice && <p role="status" className="mt-4 text-sm text-blue-600">{notice}</p>}
    {loading && <p className="mt-4">불러오는 중…</p>}
    {!loading && !summary && <button className={buttonClass} onClick={load}>다시 불러오기</button>}
    {summary && <>
      <p className="mt-4 font-bold">도우미 {summary.totalCount}명</p>
      {summary.totalCount === 0 && <p className="mt-2 text-sm text-gray-500">아직 초대한 도우미가 없어요.</p>}
      <ul className="mt-3 space-y-3">{summary.helpers.map(helper => {
        const canResend = !helper.legacy && ['PENDING', 'EXPIRED'].includes(helper.status)
        const status = canResend && helper.deliveryStatus === 'SEND_FAILED' ? '발송 실패' : labels[helper.status] || helper.status
        return <li key={helper.helperUserId} className="rounded-2xl border border-gray-200 p-4">
          <p className="break-all font-mono text-sm font-bold">{helper.username}</p>
          <p className="mt-1 text-sm font-bold text-blue-600">{status}</p>
          <p className="break-all text-sm">{helper.email || '연락 이메일 없는 기존 계정'}</p>
          <p className="mt-2 text-xs text-gray-500">최초 발송: {date(helper.sentAt)}<br />최근 발송: {date(helper.lastSentAt)}<br />초대 만료: {date(helper.expiresAt)}</p>
          {helper.legacy && helper.status !== 'REVOKED' && <form className="mt-3 flex flex-wrap gap-2" onSubmit={event => {
            event.preventDefault()
            act(() => convertLegacyHelper(festivalId, helper.helperUserId, (legacyEmails[helper.helperUserId] || '').trim().toLowerCase()), '전환 초대를 보냈어요. 수락 전까지 기존 로그인을 유지해요.')
          }}>
            <label className="min-w-0 flex-1 text-sm">전환 이메일
              <input className={inputClass} type="email" required maxLength={254} value={legacyEmails[helper.helperUserId] || ''} onChange={event => setLegacyEmails(prev => ({ ...prev, [helper.helperUserId]: event.target.value }))} />
            </label><button className={buttonClass} disabled={working}>이메일 초대로 전환</button>
          </form>}
          <div className="mt-3 flex gap-2">
            {canResend && <button className={buttonClass} disabled={working} onClick={() => act(() => resendHelperInvitation(festivalId, helper.helperUserId), '초대를 재발송했어요. 이전 링크는 만료됐어요.')}>재발송</button>}
            {helper.status !== 'REVOKED' && <button className={buttonClass} disabled={working} onClick={() => {
              if (window.confirm('초대와 계정을 해지할까요? 기존 로그인도 즉시 사용할 수 없게 됩니다.')) act(() => revokeHelperAccount(festivalId, helper.helperUserId), '도우미 계정을 해지했어요.')
            }}>해지</button>}
          </div>
        </li>
      })}</ul>
    </>}
  </section>
}
