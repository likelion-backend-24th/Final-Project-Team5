import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { FEATURED_FESTIVALS } from '../data/festivals'
import Badge from './Badge'

function HeroCarousel({ slides = FEATURED_FESTIVALS }) {
  const [index, setIndex] = useState(0)
  const count = slides.length

  if (count === 0) return null

  const go = (step) => setIndex((current) => (current + step + count) % count)

  return (
    <section className="mx-auto max-w-[1440px] px-6 pt-6">
      <div className="relative aspect-[16/6] w-full overflow-hidden rounded-3xl bg-gray-100">
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
              <img
                src={festival.image}
                alt={festival.title}
                className="h-full w-full object-cover"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/10 to-transparent" />
              <div className="absolute bottom-0 left-0 p-6 md:p-10">
                {festival.badge ? <Badge>{festival.badge}</Badge> : null}
                <h2 className="mt-3 text-2xl font-extrabold tracking-tight text-white text-balance md:text-4xl">
                  {festival.title}
                </h2>
                <p className="mt-2 text-sm text-gray-200 md:text-base">
                  {festival.location} · {festival.date}
                </p>
              </div>
            </Link>
          )
        })}

        <button
          type="button"
          onClick={() => go(-1)}
          aria-label="이전 슬라이드"
          className="absolute left-4 top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow transition hover:bg-white"
        >
          <ChevronLeftIcon className="h-5 w-5" />
        </button>
        <button
          type="button"
          onClick={() => go(1)}
          aria-label="다음 슬라이드"
          className="absolute right-4 top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow transition hover:bg-white"
        >
          <ChevronRightIcon className="h-5 w-5" />
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