import ClosingSoon from '../components/ClosingSoon'
import FestivalBrowser from '../components/FestivalBrowser'
import HeroCarousel from '../components/HeroCarousel'
import styles from './Home.module.css'

function Home() {
  return (
    <main className={styles.main}>
      <HeroCarousel />
      <FestivalBrowser />
      <ClosingSoon />
    </main>
  )
}

export default Home
