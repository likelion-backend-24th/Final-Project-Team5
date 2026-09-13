import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookmarkIcon, LogInIcon, LogOutIcon, ScanLineIcon, SearchIcon, UserRoundIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'

function SiteHeader() {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()
  const { user, isAuthenticated, isLoading, logout } = useAuth()
  //도우미는 담당 행사 하나만 다루고 예매를 할 수 없어 검색·내 예약이 의미가 없다.
  //대신 현장 검증 화면으로 가는 링크를 둔다(본인 정보 확인은 도우미도 가능해 그대로 남긴다).
  const isHelper = user?.role === 'HELPER'

  function handleSubmit(event) {
    event.preventDefault()
    const keyword = query.trim()
    if (!keyword) return
    navigate(`/festivals?q=${encodeURIComponent(keyword)}`)
    setQuery('')
  }

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <header className="sticky top-0 z-40 border-b border-gray-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex max-w-[1440px] flex-wrap items-center gap-4 px-6 py-4">
        <Link to="/" className="shrink-0" aria-label="FevalGo 홈">
          <img src="/brand/logo-horizontal.webp" alt="FevalGo" className="h-11 w-auto" />
        </Link>

        <form
          role="search"
          onSubmit={handleSubmit}
          hidden={isHelper}
          className="relative max-w-[380px] flex-1 max-md:order-3 max-md:basis-full"
        >
          <input
            type="search"
            name="q"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="어떤 페스티벌을 찾으세요?"
            aria-label="페스티벌 검색"
            className="w-full rounded-full border border-gray-200 bg-white py-2.5 pl-4 pr-11 text-sm text-gray-900 outline-none transition placeholder:text-gray-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <button
            type="submit"
            aria-label="검색"
            className="absolute right-3 top-1/2 flex h-7 w-7 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
          >
            <SearchIcon className="h-4 w-4" />
          </button>
        </form>

        <nav className="ml-auto flex items-center gap-2">
          {/* 세션 복원 중에는 "로그인·회원가입"이 잠깐 비쳤다 닉네임으로 바뀌며 깜빡였다. 그 사이엔 빈 자리만 잡아둔다. */}
          {isLoading ? (
            <span aria-hidden="true" className="h-9 w-9 animate-pulse rounded-lg bg-gray-100 sm:w-28" />
          ) : isAuthenticated ? (
            <>
              {/* 닉네임 글자만으로는 마이페이지로 가는 버튼인지 알기 어렵다는 QA 피드백 — 아이콘+라벨이 있는 버튼 모양으로 바꿨다. */}
              <Link
                to="/mypage"
                className="flex items-center gap-1.5 rounded-lg border border-gray-200 px-3 py-2 text-sm font-semibold text-black transition hover:bg-gray-50"
              >
                <UserRoundIcon className="h-4 w-4" />
                {/* 도우미 아이디(helper-xxxx@helper.local)처럼 긴 이름이 모바일 헤더를 두 줄로 깨뜨리지 않도록 잘라낸다. */}
                <span className="hidden max-w-[160px] truncate sm:inline">{user.nickname}님 · </span>
                <span>마이페이지</span>
              </Link>
              <button
                type="button"
                onClick={handleLogout}
                className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-black transition hover:bg-gray-100"
              >
                <LogOutIcon className="h-4 w-4" />
                <span className="hidden sm:inline">로그아웃</span>
              </button>
            </>
          ) : (
            <Link
              to="/login"
              className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-black transition hover:bg-gray-100"
            >
              <LogInIcon className="h-4 w-4" />
              <span className="hidden sm:inline">로그인·회원가입</span>
            </Link>
          )}

          <Link
            to={isHelper ? '/check-in' : '/reservations'}
            className="flex items-center gap-1.5 rounded-lg border border-gray-200 px-3 py-2 text-sm font-semibold text-black transition hover:bg-gray-50"
          >
            {isHelper ? <ScanLineIcon className="h-4 w-4" /> : <BookmarkIcon className="h-4 w-4" />}
            <span className="hidden sm:inline">{isHelper ? '입장 검증' : '내 예약'}</span>
          </Link>
        </nav>
      </div>
    </header>
  )
}

export default SiteHeader