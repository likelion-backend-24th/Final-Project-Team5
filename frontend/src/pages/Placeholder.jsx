import { Link } from 'react-router-dom'
import styles from './Placeholder.module.css'

/** 홈에서 링크만 걸어둔 화면들의 임시 페이지. 실제 화면이 생기면 교체한다. */
function Placeholder({ title, description = '아직 준비 중인 화면입니다.' }) {
  return (
    <main className={styles.main}>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
      <Link to="/" className={styles.back}>
        홈으로 돌아가기
      </Link>
    </main>
  )
}

export default Placeholder
