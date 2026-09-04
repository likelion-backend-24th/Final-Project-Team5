import { Link } from 'react-router-dom'
import { ArrowRightIcon, CalendarPlusIcon, ListIcon, MegaphoneIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import styles from './OrganizerCta.module.css'

/** 홈 하단 — 주최자 유입을 위한 배너. 신청/등록 두 갈래로 안내하고, HOST면 내 페스티벌 목록 진입도 보여준다. */
function OrganizerCta() {
  const { user } = useAuth()
  const isHost = user?.role === 'HOST'

  return (
    <section aria-label="주최자 안내" className={styles.banner}>
      <div className={styles.text}>
        <h2 className={styles.heading}>페스티벌을 직접 열고 싶으신가요?</h2>
        <p className={styles.description}>
          {isHost
            ? '내가 등록한 페스티벌의 심사 상태와 상세 정보를 확인해보세요.'
            : '주최자로 등록하고 나만의 페스티벌을 FevalGo에 올려보세요.'}
        </p>
      </div>

      <div className={styles.actions}>
        {isHost ? (
          <Link to="/host/festivals" className={styles.registerButton}>
            <ListIcon size={18} aria-hidden="true" />
            내 페스티벌 보기
            <ArrowRightIcon size={18} aria-hidden="true" />
          </Link>
        ) : (
          <Link to="/organizers/apply" className={styles.applyButton}>
            <MegaphoneIcon size={18} aria-hidden="true" />
            주최자 신청
          </Link>
        )}
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
