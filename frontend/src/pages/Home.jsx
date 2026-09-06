import ClosingSoon from '../components/ClosingSoon'
import FestivalBrowser from '../components/FestivalBrowser'
import HeroCarousel from '../components/HeroCarousel'
import OrganizerCta from '../components/OrganizerCta'
import { useAuth } from '../context/AuthContext.jsx'

function Home() {
  const { user } = useAuth()
  const isHost = user?.role === 'HOST'

  return (
    <main className="pb-4">
      {/* HOST는 등록/조회 진입이 자주 쓰는 동작이라 배너를 맨 위로 올려 바로 보이게 한다. */}
      {isHost && <OrganizerCta isFirst />}
      <HeroCarousel />
      <FestivalBrowser />
      <ClosingSoon />
      {!isHost && <OrganizerCta />}
    </main>
  )
}

export default Home