import { Link } from 'react-router-dom'
import { CalendarIcon, MapPinIcon } from 'lucide-react'
import { CATEGORY_LABELS } from '../data/festivals'
import Badge from './Badge'
import styles from './FestivalCard.module.css'

/**
 * 페스티벌 한 건을 보여주는 카드.
 * @param {{ festival: import('../data/festivals').Festival, badge?: React.ReactNode }} props
 *   badge를 넘기면 썸네일 좌상단 배지를 그것으로 덮어쓴다(마감임박 D-day 등).
 */
function FestivalCard({ festival, badge }) {
  const thumbBadge = badge ?? (festival.badge ? <Badge>{festival.badge}</Badge> : null)

  return (
    <Link to={`/festivals/${festival.id}`} className={styles.card}>
      <div className={styles.thumb}>
        <img
          src={festival.image}
          alt={festival.title}
          className={styles.thumbImage}
          loading="lazy"
        />
        {thumbBadge ? <span className={styles.thumbBadge}>{thumbBadge}</span> : null}
      </div>

      <div className={styles.body}>
        <span className={styles.category}>
          <Badge variant="secondary">{CATEGORY_LABELS[festival.category]}</Badge>
        </span>
        <h3 className={styles.title}>{festival.title}</h3>

        <div className={styles.meta}>
          <span className={styles.metaRow}>
            <MapPinIcon size={14} aria-hidden="true" />
            <span className={styles.metaText}>{festival.location}</span>
          </span>
          <span className={styles.metaRow}>
            <CalendarIcon size={14} aria-hidden="true" />
            <span className={styles.metaText}>{festival.date}</span>
          </span>
        </div>

        <p className={styles.price}>{festival.price}</p>
      </div>
    </Link>
  )
}

export default FestivalCard
