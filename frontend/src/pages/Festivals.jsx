import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { fetchFestivals, mapFestivalToCard, FESTIVAL_CATEGORY_LABELS, FESTIVAL_VISIBLE_STATUS_LABELS } from '../api/festivalApi'
import Badge from '../components/Badge'
import CategoryChips from '../components/CategoryChips'
import FestivalCard from '../components/FestivalCard'
import Pagination from '../components/Pagination'

const PAGE_SIZE = 8
// 백엔드가 카테고리/검색 쿼리 파라미터를 지원하지 않아, 공개된 페스티벌을 한 번에 크게 받아와 기존처럼 클라이언트에서 거른다.
const FETCH_SIZE = 100

const CATEGORY_CHIPS = [
  { id: 'all', label: '전체' },
  ...Object.entries(FESTIVAL_CATEGORY_LABELS).map(([id, label]) => ({ id, label })),
]

/** 전체 페스티벌 목록. 실제 등록된(PUBLISHED) 페스티벌을 조회한다. */
function Festivals() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q')?.trim() ?? ''
  const sort = searchParams.get('sort')
  const category = searchParams.get('category') ?? 'all'
  const requestedPage = Math.max(1, Number.parseInt(searchParams.get('page') ?? '1', 10) || 1)

  const [festivals, setFestivals] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')

    fetchFestivals({ page: 0, size: FETCH_SIZE })
      .then((response) => {
        if (cancelled) return
        setFestivals(response.data.data.map(mapFestivalToCard))
      })
      .catch(() => {
        if (!cancelled) setLoadError('페스티벌 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const filtered = useMemo(() => {
    let list = category === 'all' ? festivals : festivals.filter((festival) => festival.category === category)

    if (query) {
      const keyword = query.toLowerCase()
      list = list.filter((festival) => festival.title.toLowerCase().includes(keyword))
    }

    if (sort === 'deadline') {
      list = [...list].sort((a, b) => {
        if (typeof a.dday !== 'number') return 1
        if (typeof b.dday !== 'number') return -1
        return a.dday - b.dday
      })
    }

    return list
  }, [festivals, category, query, sort])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const page = Math.min(requestedPage, totalPages)
  const pagedFestivals = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  function updateParams(mutate) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      mutate(next)
      return next
    })
  }

  function handleCategoryChange(nextCategory) {
    updateParams((next) => {
      if (nextCategory === 'all') next.delete('category')
      else next.set('category', nextCategory)
      next.delete('page')
    })
  }

  function handlePageChange(nextPage) {
    updateParams((next) => {
      if (nextPage <= 1) next.delete('page')
      else next.set('page', String(nextPage))
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
  <main className="w-full">
    <div className="mx-auto flex max-w-[1440px] flex-col gap-4 px-6 pt-5 pb-14 sm:gap-6 sm:pt-7 sm:pb-[60px]">
      <div className="flex flex-col gap-1">
        <h1 className="text-[22px] font-extrabold tracking-tight text-gray-900 sm:text-[28px]">
          {query ? `"${query}" 검색 결과` : '전체 페스티벌'}
        </h1>
        <p className="text-sm text-gray-500">
          총 {filtered.length}건
          {query && (
            <>
              {' · '}
              <Link to="/festivals" className="text-[13px] font-semibold text-blue-600 hover:underline">
                검색 초기화
              </Link>
            </>
          )}
        </p>
      </div>

      <CategoryChips value={category} onChange={handleCategoryChange} categories={CATEGORY_CHIPS} />

      {loading && <p className="py-10 text-sm text-gray-500">불러오는 중…</p>}

      {!loading && loadError && <p className="py-10 text-sm text-gray-500">{loadError}</p>}

      {!loading && !loadError && filtered.length === 0 && (
        <p className="py-10 text-sm text-gray-500">
          {query ? '검색 결과가 없습니다.' : '해당 카테고리에 등록된 페스티벌이 없습니다.'}
        </p>
      )}

      {!loading && !loadError && filtered.length > 0 && (
        <>
          <div className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
            {pagedFestivals.map((festival) => (
              <FestivalCard
                key={festival.id}
                festival={festival}
                categoryLabel={FESTIVAL_CATEGORY_LABELS[festival.category] ?? festival.category}
                badge={
                  festival.festivalStatus === 'CLOSED' ? (
                    <Badge variant="secondary">{FESTIVAL_VISIBLE_STATUS_LABELS.CLOSED}</Badge>
                  ) : undefined
                }
              />
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={handlePageChange} />
        </>
      )}
    </div>
  </main>
)
}

export default Festivals