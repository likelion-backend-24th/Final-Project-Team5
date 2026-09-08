import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CircleAlertIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { updateNickname, updatePassword, withdrawAccount } from '../api/userApi'

const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'
const inputClass =
  'w-full rounded-2xl border border-gray-300 bg-white px-4 py-3 text-[15px] text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
const primaryBtn =
  'inline-flex items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-3 text-[15px] font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400'

function MyPageSettingsTab({ user }) {
  const navigate = useNavigate()
  const { refreshUser, logout } = useAuth()

  // 닉네임
  const [nickname, setNickname] = useState(user.nickname)
  const [nicknameError, setNicknameError] = useState('')
  const [nicknameSuccess, setNicknameSuccess] = useState('')
  const [savingNickname, setSavingNickname] = useState(false)

  // 비밀번호
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    newPasswordConfirm: '',
  })
  const [passwordError, setPasswordError] = useState('')
  const [changingPassword, setChangingPassword] = useState(false)

  // 회원탈퇴
  const [withdrawPassword, setWithdrawPassword] = useState('')
  const [withdrawError, setWithdrawError] = useState('')
  const [withdrawing, setWithdrawing] = useState(false)

  async function handleNicknameSubmit(event) {
    event.preventDefault()
    const trimmed = nickname.trim()
    if (!trimmed) {
      setNicknameError('닉네임을 입력해주세요.')
      return
    }

    setSavingNickname(true)
    setNicknameError('')
    setNicknameSuccess('')

    try {
      await updateNickname(trimmed)
      await refreshUser()
      setNicknameSuccess('닉네임이 변경되었어요.')
    } catch (error) {
      setNicknameError(
        error.response?.data?.message ?? '닉네임 변경에 실패했어요. 잠시 후 다시 시도해주세요.',
      )
    } finally {
      setSavingNickname(false)
    }
  }

  async function handlePasswordSubmit(event) {
    event.preventDefault()
    const { currentPassword, newPassword, newPasswordConfirm } = passwordForm

    if (!currentPassword || !newPassword || !newPasswordConfirm) {
      setPasswordError('모든 항목을 입력해주세요.')
      return
    }
    if (newPassword.length < 8) {
      setPasswordError('새 비밀번호는 최소 8자 이상이어야 해요.')
      return
    }
    if (newPassword !== newPasswordConfirm) {
      setPasswordError('새 비밀번호가 일치하지 않아요.')
      return
    }

    setChangingPassword(true)
    setPasswordError('')

    try {
      await updatePassword({ currentPassword, newPassword, newPasswordConfirm })
      // 비밀번호 변경 시 기존 로그인 세션이 모두 무효화되므로, 재로그인하도록 안내한다.
      logout()
      navigate('/login')
    } catch (error) {
      setPasswordError(
        error.response?.data?.message ?? '비밀번호 변경에 실패했어요. 현재 비밀번호를 확인해주세요.',
      )
    } finally {
      setChangingPassword(false)
    }
  }

  async function handleWithdrawSubmit(event) {
    event.preventDefault()
    if (!withdrawPassword) {
      setWithdrawError('비밀번호를 입력해주세요.')
      return
    }

    setWithdrawing(true)
    setWithdrawError('')

    try {
      await withdrawAccount(withdrawPassword)
      logout()
      navigate('/')
    } catch (error) {
      setWithdrawError(
        error.response?.data?.message ?? '회원 탈퇴에 실패했어요. 비밀번호를 확인해주세요.',
      )
    } finally {
      setWithdrawing(false)
    }
  }

  return (
    <section className="space-y-5">
      <form className={cardClass} onSubmit={handleNicknameSubmit}>
        <h2 className="text-lg font-extrabold text-gray-900">프로필 수정</h2>
        <div className="mt-5 space-y-2">
          <label htmlFor="nickname" className="block text-sm font-bold text-gray-900">
            닉네임
          </label>
          <input
            id="nickname"
            value={nickname}
            onChange={(event) => {
              setNickname(event.target.value)
              setNicknameError('')
              setNicknameSuccess('')
            }}
            className={inputClass}
            placeholder="닉네임을 입력하세요"
          />
          {nicknameError && (
            <p className="flex items-center gap-1.5 text-xs font-semibold text-red-500">
              <CircleAlertIcon className="h-3.5 w-3.5" />
              {nicknameError}
            </p>
          )}
          {nicknameSuccess && <p className="text-xs font-semibold text-green-600">{nicknameSuccess}</p>}
        </div>
        <div className="mt-4 flex justify-end">
          <button type="submit" disabled={savingNickname} className={primaryBtn}>
            {savingNickname ? '저장 중…' : '저장'}
          </button>
        </div>
      </form>

      <form className={cardClass} onSubmit={handlePasswordSubmit}>
        <h2 className="text-lg font-extrabold text-gray-900">비밀번호 변경</h2>
        <div className="mt-5 space-y-4">
          <div className="space-y-2">
            <label htmlFor="cur-pw" className="block text-sm font-bold text-gray-900">
              현재 비밀번호
            </label>
            <input
              id="cur-pw"
              type="password"
              placeholder="현재 비밀번호"
              value={passwordForm.currentPassword}
              onChange={(event) => {
                setPasswordForm((prev) => ({ ...prev, currentPassword: event.target.value }))
                setPasswordError('')
              }}
              className={inputClass}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="new-pw" className="block text-sm font-bold text-gray-900">
              새 비밀번호
            </label>
            <input
              id="new-pw"
              type="password"
              placeholder="8자 이상 입력하세요"
              value={passwordForm.newPassword}
              onChange={(event) => {
                setPasswordForm((prev) => ({ ...prev, newPassword: event.target.value }))
                setPasswordError('')
              }}
              className={inputClass}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="new-pw2" className="block text-sm font-bold text-gray-900">
              새 비밀번호 확인
            </label>
            <input
              id="new-pw2"
              type="password"
              placeholder="새 비밀번호를 다시 입력하세요"
              value={passwordForm.newPasswordConfirm}
              onChange={(event) => {
                setPasswordForm((prev) => ({ ...prev, newPasswordConfirm: event.target.value }))
                setPasswordError('')
              }}
              className={inputClass}
            />
          </div>
          {passwordError && (
            <p className="flex items-center gap-1.5 text-xs font-semibold text-red-500">
              <CircleAlertIcon className="h-3.5 w-3.5" />
              {passwordError}
            </p>
          )}
        </div>
        <div className="mt-4 flex justify-end">
          <button type="submit" disabled={changingPassword} className={primaryBtn}>
            {changingPassword ? '변경 중…' : '비밀번호 변경'}
          </button>
        </div>
      </form>

      <form
        className="rounded-3xl border border-red-200 bg-red-50/50 p-6 md:p-8"
        onSubmit={handleWithdrawSubmit}
      >
        <h2 className="text-lg font-extrabold text-red-700">회원탈퇴</h2>
        <p className="mt-1 text-sm leading-relaxed text-red-600/90">
          탈퇴 시 예약 및 주최 정보가 모두 삭제되며 복구할 수 없습니다.
        </p>
        <div className="mt-5 space-y-2">
          <label htmlFor="del-pw" className="block text-sm font-bold text-red-700">
            비밀번호 확인
          </label>
          <input
            id="del-pw"
            type="password"
            placeholder="비밀번호를 입력하세요"
            value={withdrawPassword}
            onChange={(event) => {
              setWithdrawPassword(event.target.value)
              setWithdrawError('')
            }}
            className="w-full rounded-2xl border border-red-300 bg-white px-4 py-3 text-[15px] text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
          />
          {withdrawError && (
            <p className="flex items-center gap-1.5 text-xs font-semibold text-red-600">
              <CircleAlertIcon className="h-3.5 w-3.5" />
              {withdrawError}
            </p>
          )}
        </div>
        <div className="mt-4 flex justify-end">
          <button
            type="submit"
            disabled={withdrawing}
            className="inline-flex items-center justify-center gap-2 rounded-2xl bg-red-600 px-5 py-3 text-[15px] font-bold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
          >
            {withdrawing ? '처리 중…' : '회원탈퇴'}
          </button>
        </div>
      </form>
    </section>
  )
}

export default MyPageSettingsTab