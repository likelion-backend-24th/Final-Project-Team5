import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { filterByCategory } from '../data/festivals'
import CategoryChips from './CategoryChips'
import FestivalCard from './FestivalCard'
import styles from './Section.module.css'

/** 카테고리 필터 칩 + 인기 페스티벌 카드 그리드. */
function FestivalBrowser() {
  const [category, setCategory] = useState('all')
  const festivals = useMemo(() => filterByCategory(category), [category])

  return (
    <section aria-label="인기 페스티벌" className={styles.section}>
      <CategoryChips value={category} onChange={setCategory} />

      <div className={styles.header}>
        <h2 className={styles.heading}>인기 페스티벌</h2>
        <Link to="/festivals" className={styles.more}>
          전체 보기
        </Link>
      </div>

      {festivals.length === 0 ? (
        <p className={styles.empty}>해당 카테고리에 등록된 페스티벌이 없습니다.</p>
      ) : (
        <div className={styles.grid}>
          {festivals.map((festival) => (
            <FestivalCard key={festival.id} festival={festival} />
          ))}
        </div>
      )}
    </section>
  )
}

export default FestivalBrowser
