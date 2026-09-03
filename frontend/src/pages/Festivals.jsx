import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { filterByCategory } from '../data/festivals'
import CategoryChips from '../components/CategoryChips'
import FestivalCard from '../components/FestivalCard'
import Pagination from '../components/Pagination'
import sectionStyles from '../components/Section.module.css'
import styles from './Festivals.module.css'

const PAGE_SIZE = 8

/** 전체 페스티벌 목록. 카테고리 칩 + ?q= 이름 검색 + ?sort=deadline 마감임박순 + ?page= 페이지네이션을 함께 지원한다. */
function Festivals() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q')?.trim() ?? ''
  const sort = searchParams.get('sort')
  const category = searchParams.get('category') ?? 'all'
  const requestedPage = Math.max(1, Number.parseInt(searchParams.get('page') ?? '1', 10) || 1)

  const festivals = useMemo(() => {
    let list = filterByCategory(category)

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
  }, [category, query, sort])

  const totalPages = Math.max(1, Math.ceil(festivals.length / PAGE_SIZE))
  const page = Math.min(requestedPage, totalPages)
  const pagedFestivals = festivals.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

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
    <main className={styles.main}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>{query ? `"${query}" 검색 결과` : '전체 페스티벌'}</h1>
        <p className={styles.resultCount}>
          총 {festivals.length}건
          {query && (
            <>
              {' · '}
              <Link to="/festivals" className={styles.clearSearch}>
                검색 초기화
              </Link>
            </>
          )}
        </p>
      </div>

      <CategoryChips value={category} onChange={handleCategoryChange} />

      {festivals.length === 0 ? (
        <p className={sectionStyles.empty}>
          {query ? '검색 결과가 없습니다.' : '해당 카테고리에 등록된 페스티벌이 없습니다.'}
        </p>
      ) : (
        <>
          <div className={sectionStyles.grid}>
            {pagedFestivals.map((festival) => (
              <FestivalCard key={festival.id} festival={festival} />
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={handlePageChange} />
        </>
      )}
    </main>
  )
}

export default Festivals
