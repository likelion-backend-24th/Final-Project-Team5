/** 데이터가 오기 전 자리만 잡아두는 회색 블록. 스크린리더에는 숨기고 감싸는 쪽이 role="status"로 알린다. */
export function Skeleton({ className = '' }) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-lg bg-gray-200 motion-reduce:animate-none ${className}`}
    />
  )
}

//FestivalCard와 같은 뼈대(정사각 썸네일 + 배지·제목·장소·날짜·가격 줄)라 실제 카드로 바뀔 때 레이아웃이 튀지 않는다.
export function FestivalCardSkeleton() {
  return (
    <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white">
      <Skeleton className="aspect-square w-full rounded-none" />
      <div className="space-y-2 p-4">
        <Skeleton className="h-6 w-16 rounded-full" />
        <Skeleton className="h-5 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="mt-1 h-5 w-1/3" />
      </div>
    </div>
  )
}

export function FestivalGridSkeleton({ count = 8 }) {
  return (
    <div role="status" aria-label="불러오는 중" className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }, (_, index) => (
        <FestivalCardSkeleton key={index} />
      ))}
    </div>
  )
}

//HeroCarousel과 같은 비율·여백이라 배너가 나타날 때 아래 내용이 밀리지 않는다.
export function HeroSkeleton() {
  return (
    <section role="status" aria-label="불러오는 중" className="mx-auto max-w-[1440px] px-6 pt-6">
      <Skeleton className="aspect-[16/6] w-full rounded-3xl" />
    </section>
  )
}
