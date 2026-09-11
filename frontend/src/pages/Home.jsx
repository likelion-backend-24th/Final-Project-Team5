import { useEffect, useMemo, useState } from 'react'
import AdminHomeBanner from '../components/AdminHomeBanner'
import ClosingSoon from '../components/ClosingSoon'
import FestivalBrowser from '../components/FestivalBrowser'
import HeroCarousel from '../components/HeroCarousel'
import OrganizerCta from '../components/OrganizerCta'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchFestivals, mapFestivalToCard } from '../api/festivalApi'

const HERO_SLIDE_LIMIT = 5
const CLOSING_SOON_LIMIT = 5

function Home() {
  const { user } = useAuth()
  const isHost = user?.role === 'HOST'

  const [festivals, setFestivals] = useState([])

  useEffect(() => {
    let cancelled = false
    fetchFestivals({ page: 0, size: 100 })
      .then((response) => {
        if (!cancelled) setFestivals(response.data.data.map(mapFestivalToCard))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const heroSlides = useMemo(() => festivals.slice(0, HERO_SLIDE_LIMIT), [festivals])
  const closingSoon = useMemo(
    () =>
      festivals
        .filter((f) => typeof f.dday === 'number' && f.dday >= 0)
        .sort((a, b) => a.dday - b.dday)
        .slice(0, CLOSING_SOON_LIMIT),
    [festivals],
  )

  return (
    <main className="pb-4">
      <AdminHomeBanner />
      {/* HOST는 등록/조회 진입이 자주 쓰는 동작이라 배너를 맨 위로 올려 바로 보이게 한다. */}
      {isHost && <OrganizerCta isFirst />}
      <HeroCarousel slides={heroSlides} />
      <FestivalBrowser festivals={festivals} />
      <ClosingSoon festivals={closingSoon} />
      {!isHost && <OrganizerCta />}
    </main>
  )
}

export default Home