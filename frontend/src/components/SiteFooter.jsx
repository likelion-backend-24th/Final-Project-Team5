import { Link } from 'react-router-dom'
import { MessageCircleIcon } from 'lucide-react'

function SiteFooter() {
  return (
    <footer className="mt-16 border-t border-gray-200 bg-white">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-8 px-6 py-10 md:flex-row md:items-start md:justify-between">
        <div className="max-w-sm">
          <p className="text-xl font-extrabold tracking-tight text-blue-600">FevalGo</p>
          <p className="mt-3 text-sm leading-relaxed text-gray-500">
            음악, 공연·전시, 푸드까지. 전국의 페스티벌과 행사를 한 곳에서 찾고 간편하게 예매하는 티켓 플랫폼입니다.
          </p>
        </div>

        <div className="flex flex-col gap-8 sm:flex-row sm:gap-16">
          <div className="space-y-3">
            <p className="text-sm font-bold text-gray-900">주최자 센터</p>
            <nav className="flex flex-col gap-2 text-sm text-gray-500">
              <Link to="/organizers/apply" className="hover:text-blue-600">
                주최자 신청
              </Link>
              <Link to="/festivals/new" className="hover:text-blue-600">
                페스티벌 등록
              </Link>
            </nav>
          </div>

          <div className="space-y-3">
            <nav className="flex items-center gap-6 text-sm font-semibold text-gray-700">
              <Link to="/terms" className="hover:text-blue-600">
                이용약관
              </Link>
              <Link to="/privacy" className="hover:text-blue-600">
                개인정보처리방침
              </Link>
            </nav>
            <p className="flex items-center gap-2 text-sm text-gray-500">
              <MessageCircleIcon className="h-4 w-4" />
              고객센터 1588-0000 (평일 09:00–18:00)
            </p>
          </div>
        </div>
      </div>

      <div className="border-t border-gray-100 py-5">
        <p className="text-center text-xs text-gray-400">© 2026 FevalGo. All rights reserved.</p>
      </div>
    </footer>
  )
}

export default SiteFooter