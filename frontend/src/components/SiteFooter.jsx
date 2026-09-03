import { Link } from 'react-router-dom'
import { MessageCircleIcon } from 'lucide-react'
import styles from './SiteFooter.module.css'

function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <div className={styles.brand}>
          <span className={styles.logo}>FevalGo</span>
          <p className={styles.tagline}>
            음악, 공연·전시, 푸드까지. 전국의 페스티벌과 행사를 한 곳에서 찾고
            간편하게 예매하는 티켓 플랫폼입니다.
          </p>
        </div>

        <nav className={styles.nav} aria-label="약관 및 고객지원">
          <div className={styles.links}>
            <Link to="/terms" className={styles.link}>
              이용약관
            </Link>
            <Link to="/privacy" className={`${styles.link} ${styles.linkStrong}`}>
              개인정보처리방침
            </Link>
          </div>
          <span className={styles.support}>
            <MessageCircleIcon size={14} aria-hidden="true" />
            고객센터 1588-0000 (평일 09:00–18:00)
          </span>
        </nav>
      </div>

      <div className={styles.copyright}>© 2026 FevalGo. All rights reserved.</div>
    </footer>
  )
}

export default SiteFooter
