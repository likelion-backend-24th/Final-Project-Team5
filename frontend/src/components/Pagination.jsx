import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import styles from './Pagination.module.css'

/** 번호형 페이지네이션. totalPages가 1 이하면 아무것도 그리지 않는다. */
function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)

  return (
    <nav className={styles.pagination} aria-label="페이지네이션">
      <button
        type="button"
        className={styles.arrow}
        onClick={() => onChange(page - 1)}
        disabled={page <= 1}
        aria-label="이전 페이지"
      >
        <ChevronLeftIcon size={18} aria-hidden="true" />
      </button>

      {pages.map((p) => (
        <button
          key={p}
          type="button"
          className={`${styles.page} ${p === page ? styles.pageActive : ''}`}
          onClick={() => onChange(p)}
          aria-current={p === page ? 'page' : undefined}
        >
          {p}
        </button>
      ))}

      <button
        type="button"
        className={styles.arrow}
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages}
        aria-label="다음 페이지"
      >
        <ChevronRightIcon size={18} aria-hidden="true" />
      </button>
    </nav>
  )
}

export default Pagination
