import { Link } from 'react-router-dom'
import { ArrowRightIcon, CalendarPlusIcon, MegaphoneIcon } from 'lucide-react'
import styles from './OrganizerCta.module.css'

/** 홈 하단 — 주최자 유입을 위한 배너. 신청/등록 두 갈래로 안내한다. */
function OrganizerCta() {
  return (
    <section aria-label="주최자 안내" className={styles.banner}>
      <div className={styles.text}>
        <h2 className={styles.heading}>페스티벌을 직접 열고 싶으신가요?</h2>
        <p className={styles.description}>
          주최자로 등록하고 나만의 페스티벌을 FevalGo에 올려보세요.
        </p>
      </div>

      <div className={styles.actions}>
        <Link to="/organizers/apply" className={styles.applyButton}>
          <MegaphoneIcon size={18} aria-hidden="true" />
          주최자 신청
        </Link>
        <Link to="/festivals/new" className={styles.registerButton}>
          <CalendarPlusIcon size={18} aria-hidden="true" />
          페스티벌 등록
          <ArrowRightIcon size={18} aria-hidden="true" />
        </Link>
      </div>
    </section>
  )
}

export default OrganizerCta
