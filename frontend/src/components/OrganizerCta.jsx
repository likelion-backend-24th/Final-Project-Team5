import { Link } from 'react-router-dom'
import { ArrowRightIcon, CalendarPlusIcon, ListIcon, MegaphoneIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'

function OrganizerCta({ isFirst = false }) {
  const { user } = useAuth()
  const isHost = user?.role === 'HOST'

  return (
    <section className={`mx-auto mb-4 max-w-[1440px] px-6 ${isFirst ? '' : 'mt-[44px]'}`}>
      <div className="flex flex-col items-start gap-8 rounded-3xl bg-blue-600 px-6 py-10 md:flex-row md:items-center md:justify-between md:px-10">
        <div>
          <h2 className="text-balance text-2xl font-extrabold tracking-tight text-white">
            페스티벌을 직접 열고 싶으신가요?
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-blue-100">
            {isHost
              ? '내가 등록한 페스티벌의 심사 상태와 상세 정보를 확인해보세요.'
              : '주최자로 등록하고 나만의 페스티벌을 FevalGo에 올려보세요.'}
          </p>
        </div>

        <div className="flex w-full flex-col gap-3 sm:flex-row md:w-auto">
          {isHost ? (
            <Link
              to="/host/festivals"
              className="inline-flex items-center justify-center gap-2 rounded-2xl border border-blue-400 bg-blue-500 px-6 py-3.5 text-sm font-bold text-white transition hover:bg-blue-400"
            >
              <ListIcon className="h-5 w-5" />
              내 페스티벌 보기
              <ArrowRightIcon className="h-5 w-5" />
            </Link>
          ) : (
            <Link
              to="/organizers/apply"
              className="inline-flex items-center justify-center gap-2 rounded-2xl bg-white px-6 py-3.5 text-sm font-bold text-blue-600 transition hover:bg-blue-50"
            >
              <MegaphoneIcon className="h-5 w-5" />
              주최자 신청
            </Link>
          )}
          <Link
            to="/festivals/new"
            className="inline-flex items-center justify-center gap-2 rounded-2xl border border-blue-400 bg-blue-500 px-6 py-3.5 text-sm font-bold text-white transition hover:bg-blue-400"
          >
            <CalendarPlusIcon className="h-5 w-5" />
            페스티벌 등록
            <ArrowRightIcon className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </section>
  )
}

export default OrganizerCta