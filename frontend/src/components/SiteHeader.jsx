import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookmarkIcon, LogInIcon, LogOutIcon, SearchIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'

function SiteHeader() {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()
  const { user, isAuthenticated, logout } = useAuth()

  function handleSubmit(event) {
    event.preventDefault()
    const keyword = query.trim()
    if (!keyword) return
    navigate(`/festivals?q=${encodeURIComponent(keyword)}`)
  }

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <header className="sticky top-0 z-40 border-b border-gray-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex max-w-[1440px] flex-wrap items-center gap-4 px-6 py-4">
        <Link to="/" className="text-2xl font-extrabold tracking-tight text-blue-600" aria-label="FevalGo 홈">
          FevalGo
        </Link>

        <form
          role="search"
          onSubmit={handleSubmit}
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
          {isAuthenticated && user.role === 'ADMIN' && (
            <>
              <Link
                to="/admin/host-applications"
                className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-gray-600 transition hover:bg-gray-100"
              >
                주최자 심사
              </Link>
              <Link
                to="/admin/festivals"
                className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-gray-600 transition hover:bg-gray-100"
              >
                페스티벌 심사
              </Link>
            </>
          )}

          {isAuthenticated ? (
            <>
              <span className="text-sm font-semibold text-gray-700">{user.nickname}님</span>
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
            to="/reservations"
            className="flex items-center gap-1.5 rounded-lg border border-gray-200 px-3 py-2 text-sm font-semibold text-black transition hover:bg-gray-50"
          >
            <BookmarkIcon className="h-4 w-4" />
            <span className="hidden sm:inline">내 예약</span>
          </Link>
        </nav>
      </div>
    </header>
  )
}

export default SiteHeader