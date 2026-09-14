import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import SettlementReport from '../components/SettlementReport'
import styles from './AdminList.module.css'
export default function HostSettlements() {
  const { user, isLoading } = useAuth()
  return <main className={styles.main}>{isLoading ? <p role="status">불러오는 중…</p> : user?.role !== 'HOST' ? <p>주최자만 이용 가능한 페이지입니다.</p> : <><div className={styles.headerRow}><h1 className={styles.title}>내 정산</h1><Link to="/host/festivals">내 페스티벌</Link></div><SettlementReport host /></>}</main>
}
