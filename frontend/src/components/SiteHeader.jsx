import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookmarkIcon, LogInIcon, SearchIcon } from 'lucide-react'
import styles from './SiteHeader.module.css'

/** 스크롤해도 고정되는 상단바. 로고 / 검색 / 우측 액션 3단 구성. */
function SiteHeader() {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()

  function handleSubmit(event) {
    event.preventDefault()
    const keyword = query.trim()
    if (!keyword) return
    navigate(`/festivals?q=${encodeURIComponent(keyword)}`)
  }

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link to="/" className={styles.logo} aria-label="FevalGo 홈">
          FevalGo
        </Link>

        <form
          className={styles.search}
          role="search"
          onSubmit={handleSubmit}
        >
          <div className={styles.searchField}>
            <input
              type="search"
              name="q"
              className={styles.searchInput}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="어떤 페스티벌을 찾으세요?"
              aria-label="페스티벌 검색"
            />
            <button type="submit" className={styles.searchButton} aria-label="검색">
              <SearchIcon size={16} aria-hidden="true" />
            </button>
          </div>
        </form>

        <div className={styles.actions}>
          <Link
            to="/login"
            className={`${styles.action} ${styles.actionInvisible}`}
            aria-label="로그인·회원가입"
          >
            <LogInIcon size={16} aria-hidden="true" className={styles.actionIcon} />
            <span className={styles.actionLabel}>로그인·회원가입</span>
          </Link>
          <Link
            to="/reservations"
            className={`${styles.action} ${styles.actionPrimary}`}
            aria-label="내 예약"
          >
            <BookmarkIcon size={16} aria-hidden="true" />
            <span className={styles.actionLabel}>내 예약</span>
          </Link>
        </div>
      </div>
    </header>
  )
}

export default SiteHeader
