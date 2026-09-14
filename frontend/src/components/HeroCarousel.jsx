import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import Badge, { badgeVariantForLabel } from './Badge'

//모바일에서 스와이프로 슬라이드를 넘기기 위한 최소 이동 거리(px). 너무 작으면 스크롤·탭과 헷갈린다.
const SWIPE_THRESHOLD_PX = 40

function HeroCarousel({ slides = [] }) {
  const [index, setIndex] = useState(0)
  const touchStartXRef = useRef(null)
  const count = slides.length

  if (count === 0) return null

  const go = (step) => setIndex((current) => (current + step + count) % count)

  function handleTouchStart(event) {
    touchStartXRef.current = event.touches[0].clientX
  }

  function handleTouchEnd(event) {
    if (touchStartXRef.current === null) return
    const deltaX = event.changedTouches[0].clientX - touchStartXRef.current
    touchStartXRef.current = null
    if (deltaX > SWIPE_THRESHOLD_PX) go(-1)
    else if (deltaX < -SWIPE_THRESHOLD_PX) go(1)
  }

  return (
    <section className="mx-auto max-w-[1440px] px-6 pt-6">
      <div
        className="relative aspect-[16/6] w-full overflow-hidden rounded-3xl bg-gray-100"
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      >
        {slides.map((festival, i) => {
          const active = i === index
          return (
            <Link
              key={festival.id}
              to={`/festivals/${festival.id}`}
              className={`absolute inset-0 block transition-opacity duration-300 ${
                active ? 'opacity-100' : 'pointer-events-none opacity-0'
              }`}
              aria-label={`${festival.title} 상세 보기`}
              aria-hidden={!active}
              tabIndex={active ? 0 : -1}
            >
              {festival.image ? (
                <img src={festival.image} alt={festival.title} className="h-full w-full object-cover" />
              ) : (
                <div className="h-full w-full bg-gradient-to-br from-brand-navy to-brand-blue" aria-hidden="true" />
              )}
              {/* 밝은 썸네일 위에서도 흰 글씨가 묻히지 않도록 하단을 충분히 어둡게 깔고, 글자에도 그림자를 준다. */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/45 to-black/10" />
              <div className="absolute bottom-0 left-0 max-w-[75%] p-5 pb-12 sm:max-w-none sm:p-6 sm:pb-6 md:p-10">
                {festival.badge ? (
                  <Badge variant={badgeVariantForLabel(festival.badge)}>{festival.badge}</Badge>
                ) : null}
                <h2 className="mt-2 text-lg font-extrabold tracking-tight text-white text-balance [text-shadow:0_2px_10px_rgba(0,0,0,0.8)] sm:mt-3 sm:text-2xl md:text-4xl">
                  {festival.title}
                </h2>
                <p className="mt-1 text-xs text-gray-100 [text-shadow:0_1px_6px_rgba(0,0,0,0.8)] sm:mt-2 sm:text-sm md:text-base">
                  {festival.location} · {festival.date}
                </p>
              </div>
            </Link>
          )
        })}

        {/* 모바일에서는 화살표가 배너 세로 중앙(=제목 위치)을 가리므로 오른쪽 아래 구석에 작게 모아 둔다. */}
        <button
          type="button"
          onClick={() => go(-1)}
          aria-label="이전 슬라이드"
          className="absolute bottom-3 right-14 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow transition hover:bg-white sm:bottom-auto sm:left-4 sm:right-auto sm:top-1/2 sm:h-11 sm:w-11 sm:-translate-y-1/2"
        >
          <ChevronLeftIcon className="h-4 w-4 sm:h-5 sm:w-5" />
        </button>
        <button
          type="button"
          onClick={() => go(1)}
          aria-label="다음 슬라이드"
          className="absolute bottom-3 right-3 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow transition hover:bg-white sm:bottom-auto sm:right-4 sm:top-1/2 sm:h-11 sm:w-11 sm:-translate-y-1/2"
        >
          <ChevronRightIcon className="h-4 w-4 sm:h-5 sm:w-5" />
        </button>

        <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-2">
          {slides.map((festival, i) => (
            <button
              key={festival.id}
              type="button"
              onClick={() => setIndex(i)}
              aria-label={`${i + 1}번째 슬라이드로 이동`}
              aria-current={i === index}
              className={`h-2 rounded-full transition-all ${
                i === index ? 'w-6 bg-blue-600' : 'w-2 bg-white/70'
              }`}
            />
          ))}
        </div>
      </div>
    </section>
  )
}

export default HeroCarousel