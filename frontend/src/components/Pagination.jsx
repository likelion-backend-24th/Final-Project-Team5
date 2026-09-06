import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'

/** 번호형 페이지네이션. totalPages가 1 이하면 아무것도 그리지 않는다. */
function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)

  const buttonBaseClass =
    'inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border text-sm font-semibold transition'

  return (
    <nav aria-label="페이지네이션" className="mt-2 flex items-center justify-center gap-1.5">
      <button
        type="button"
        onClick={() => onChange(page - 1)}
        disabled={page <= 1}
        aria-label="이전 페이지"
        className={`${buttonBaseClass} border-gray-200 bg-white text-gray-900 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-white`}
      >
        <ChevronLeftIcon className="h-[18px] w-[18px]" />
      </button>

      {pages.map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => onChange(p)}
          aria-current={p === page ? 'page' : undefined}
          className={
            p === page
              ? `${buttonBaseClass} border-blue-600 bg-blue-600 text-white`
              : `${buttonBaseClass} border-gray-200 bg-white text-gray-900 hover:bg-gray-50`
          }
        >
          {p}
        </button>
      ))}

      <button
        type="button"
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages}
        aria-label="다음 페이지"
        className={`${buttonBaseClass} border-gray-200 bg-white text-gray-900 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-white`}
      >
        <ChevronRightIcon className="h-[18px] w-[18px]" />
      </button>
    </nav>
  )
}

export default Pagination