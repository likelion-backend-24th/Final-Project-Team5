import { Link } from 'react-router-dom'
import { AlarmClockIcon } from 'lucide-react'
import { CLOSING_SOON_FESTIVALS } from '../data/festivals'
import Badge from './Badge'
import FestivalCard from './FestivalCard'
import styles from './Section.module.css'

/** 마감임박 — 남은 일수가 짧은 순으로 가로 스크롤 레일에 노출. */
function ClosingSoon({ festivals = CLOSING_SOON_FESTIVALS }) {
  if (festivals.length === 0) return null

  return (
    <section aria-label="마감임박" className={styles.section}>
      <div className={styles.header}>
        <div>
          <h2 className={styles.heading}>
            <AlarmClockIcon size={22} aria-hidden="true" className={styles.headingIcon} />
            마감임박
          </h2>
          <p className={styles.description}>예매 마감이 코앞인 페스티벌을 먼저 확인하세요.</p>
        </div>
        <Link to="/festivals?sort=deadline" className={styles.more}>
          전체 보기
        </Link>
      </div>

      <div className={styles.rail}>
        {festivals.map((festival) => (
          <FestivalCard
            key={festival.id}
            festival={festival}
            badge={<Badge variant="danger">D-{festival.dday}</Badge>}
          />
        ))}
      </div>
    </section>
  )
}

export default ClosingSoon
