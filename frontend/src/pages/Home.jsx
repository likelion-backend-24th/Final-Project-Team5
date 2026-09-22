import { useMemo } from 'react'
import AdminHomeBanner from '../components/AdminHomeBanner'
import ClosingSoon from '../components/ClosingSoon'
import FestivalBrowser from '../components/FestivalBrowser'
import HeroCarousel from '../components/HeroCarousel'
import { HeroSkeleton } from '../components/Skeleton'
import OrganizerCta from '../components/OrganizerCta'
import { useAuth } from '../context/AuthContext.jsx'
import { festivalListKey, fetchFestivals, isClosingSoon, mapFestivalToCard } from '../api/festivalApi'
import { useCachedQuery } from '../api/queryCache'

const HERO_SLIDE_LIMIT = 5
const CLOSING_SOON_LIMIT = 5
//인기순(조회수) — 히어로 배너와 인기 페스티벌 섹션 모두 이 순서를 그대로 쓴다.
//마감임박·카테고리 필터를 클라이언트에서 하므로(백엔드에 필터 파라미터가 없다) 한 번에 넉넉히 받는다.
const HOME_LIST_PARAMS = { page: 0, size: 100, sort: 'viewCount,desc' }

function Home() {
  const { user } = useAuth()
  const isHost = user?.role === 'HOST'

  //조회 실패를 행사 없음으로 오해하지 않도록 캐시의 오류도 화면에 전달한다.
  const { data, error, isLoading } = useCachedQuery(festivalListKey(HOME_LIST_PARAMS), () =>
    fetchFestivals(HOME_LIST_PARAMS),
  )
  const festivals = useMemo(() => (data ?? []).map(mapFestivalToCard), [data])
  const loadError = error ? '페스티벌 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.' : ''

  const heroSlides = useMemo(() => festivals.slice(0, HERO_SLIDE_LIMIT), [festivals])
  // 마감임박 기준: 공연 시작까지 D-3일 이내
  const closingSoon = useMemo(
    () =>
      festivals
        .filter(isClosingSoon)
        .sort((a, b) => a.dday - b.dday)
        .slice(0, CLOSING_SOON_LIMIT),
    [festivals],
  )

  return (
    <main className="pb-4">
      <AdminHomeBanner />
      {/* HOST는 등록/조회 진입이 자주 쓰는 동작이라 배너를 맨 위로 올려 바로 보이게 한다. */}
      {isHost && <OrganizerCta isFirst />}
      {isLoading ? <HeroSkeleton /> : <HeroCarousel slides={heroSlides} />}
      <FestivalBrowser festivals={festivals} loading={isLoading} loadError={loadError} />
      <ClosingSoon festivals={closingSoon} />
      {!isHost && <OrganizerCta />}
    </main>
  )
}

export default Home