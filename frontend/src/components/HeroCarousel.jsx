import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { FEATURED_FESTIVALS } from '../data/festivals'
import Badge from './Badge'
import styles from './HeroCarousel.module.css'

/** 추천 페스티벌 메인 배너. 좌우 화살표 + 하단 도트로 이동한다. */
function HeroCarousel({ slides = FEATURED_FESTIVALS }) {
  const [index, setIndex] = useState(0)
  const count = slides.length

  if (count === 0) return null

  const go = (step) => setIndex((current) => (current + step + count) % count)

  return (
    <section aria-label="추천 페스티벌" className={styles.carousel}>
      <div className={styles.viewport}>
        {slides.map((festival, i) => {
          const active = i === index
          return (
            <Link
              key={festival.id}
              to={`/festivals/${festival.id}`}
              className={`${styles.slide} ${active ? styles.slideActive : ''}`}
              aria-label={`${festival.title} 상세 보기`}
              aria-hidden={!active}
              tabIndex={active ? 0 : -1}
            >
              <img
                src={festival.image}
                alt={festival.title}
                className={styles.slideImage}
              />
              <div className={styles.scrim} />
              <div className={styles.caption}>
                {festival.badge ? <Badge>{festival.badge}</Badge> : null}
                <h2 className={styles.title}>{festival.title}</h2>
                <p className={styles.meta}>
                  {festival.location} · {festival.date}
                </p>
              </div>
            </Link>
          )
        })}

        <div className={styles.arrows}>
          <button
            type="button"
            className={styles.arrow}
            onClick={() => go(-1)}
            aria-label="이전 페스티벌"
          >
            <ChevronLeftIcon size={20} aria-hidden="true" />
          </button>
          <button
            type="button"
            className={styles.arrow}
            onClick={() => go(1)}
            aria-label="다음 페스티벌"
          >
            <ChevronRightIcon size={20} aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className={styles.dots}>
        {slides.map((festival, i) => (
          <button
            key={festival.id}
            type="button"
            className={`${styles.dot} ${i === index ? styles.dotActive : ''}`}
            onClick={() => setIndex(i)}
            aria-label={`${i + 1}번째 슬라이드로 이동`}
            aria-current={i === index}
          />
        ))}
      </div>
    </section>
  )
}

export default HeroCarousel
