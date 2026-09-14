import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { acceptHelperInvitation, fetchHelperInvitation } from '../api/authApi'
import { useAuth } from '../context/AuthContext'

const errors = {
  INVITATION_INVALID: '올바르지 않거나 새 링크로 교체된 초대예요.',
  INVITATION_EXPIRED: '초대 링크가 만료되었어요.',
  INVITATION_ACCEPTED: '이미 사용한 초대 링크예요. 설정한 비밀번호로 로그인해주세요.',
  INVITATION_REVOKED: '주최자가 해지한 초대예요.',
  HELPER_FESTIVAL_ENDED: '이미 종료된 행사예요.',
}
const inputClass = 'mt-2 w-full rounded-2xl border border-gray-300 bg-white px-4 py-3'
export default function HelperInvite() {
  const { token } = useParams()
  const navigate = useNavigate()
  const { applyTokenLogin, user, isLoading } = useAuth()
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [fatal, setFatal] = useState(false)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [show, setShow] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const busy = useRef(false)
  useEffect(() => {
    let cancelled = false
    fetchHelperInvitation(token).then(response => {
      if (!cancelled) setInfo(response.data.data)
    }).catch(err => {
      if (!cancelled) { setFatal(true); setError(errors[err.response?.data?.errorCode] || '초대를 확인할 수 없어요. 잠시 후 다시 열어주세요.') }
    }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [token])
  async function submit(event) {
    event.preventDefault()
    if (busy.current || isLoading) return
    setError('')
    if (password.length < 8) { setError('비밀번호는 최소 8자 이상 입력해주세요.'); return }
    if (new TextEncoder().encode(password).length > 72) { setError('비밀번호는 UTF-8 기준 72바이트 이내로 입력해주세요.'); return }
    if (password !== confirm) { setError('비밀번호 확인이 일치하지 않아요.'); return }
    busy.current = true; setSubmitting(true)
    let accepted = false
    try {
      const response = await acceptHelperInvitation(token, password, confirm)
      accepted = true
      setPassword(''); setConfirm('')
      await applyTokenLogin(response.data.data.accessToken)
      navigate('/', { replace: true })
    } catch (err) {
      const code = err.response?.data?.errorCode
      if (accepted) {
        setFatal(true)
        setError('계정은 활성화되었지만 로그인 정보를 불러오지 못했어요. 발급된 아이디와 설정한 비밀번호로 로그인해주세요.')
      } else {
        setError(errors[code] || '활성화하지 못했어요. 연결을 확인하고 다시 시도해주세요.')
        if (errors[code]) setFatal(true)
      }
    } finally { busy.current = false; setSubmitting(false) }
  }
  return <main className="flex min-h-screen justify-center bg-gray-50 px-5 py-12">
    <div className="h-fit w-full max-w-md rounded-3xl border border-gray-200 bg-white p-7 shadow-sm">
      <p className="text-sm font-bold text-blue-600">FevalGo</p>
      <h1 className="mt-2 text-2xl font-extrabold">도우미 계정 활성화</h1>
      {loading && <p role="status" className="mt-5">초대를 확인하고 있어요…</p>}
      {info && <div className="mt-5 space-y-2 rounded-2xl bg-blue-50 p-4 text-sm">
        <h2 className="text-lg font-bold">{info.festivalName}</h2>
        <p>{info.festivalStartAt?.replace('T', ' ')} ~ {info.festivalEndAt?.replace('T', ' ')} (한국 시간)</p>
        <p>로그인 아이디<br /><strong className="break-all">{info.username}</strong></p>
        <p>초대 수신: {info.maskedEmail}</p>
        <p>만료: {info.expiresAt?.replace('T', ' ')}</p>
      </div>}
      {error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}
      {fatal && <div className="mt-4 text-sm"><p>새 초대가 필요하면 주최자에게 재발송을 요청해주세요.</p><Link className="mt-3 inline-block font-bold text-blue-600" to="/login" replace>로그인으로 이동</Link></div>}
      {info && !fatal && <>
        {user && <p className="mt-4 text-sm text-gray-600">현재 {user.nickname || user.username} 계정으로 로그인되어 있어요. 활성화하면 이 도우미 계정으로 전환돼요.</p>}
        <form onSubmit={submit} className="mt-5 space-y-4" noValidate>
          <label className="block text-sm font-bold">새 비밀번호<input className={inputClass} type={show ? 'text' : 'password'} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} minLength={8} maxLength={72} required /></label>
          <label className="block text-sm font-bold">새 비밀번호 확인<input className={inputClass} type={show ? 'text' : 'password'} autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} minLength={8} maxLength={72} required /></label>
          <button type="button" className="text-sm text-blue-600" aria-pressed={show} onClick={() => setShow(!show)}>{show ? '비밀번호 숨기기' : '비밀번호 표시'}</button>
          <p className="text-xs text-gray-500">8자 이상 입력해주세요. 활성화 후에는 발급된 아이디로 로그인해요.</p>
          <button disabled={submitting || isLoading} className="w-full rounded-2xl bg-blue-600 py-3 font-bold text-white disabled:opacity-40">{submitting ? '활성화 중…' : '비밀번호 설정하고 시작하기'}</button>
        </form>
      </>}
    </div>
  </main>
}
