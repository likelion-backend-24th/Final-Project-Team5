import { LayoutDashboard, Users, Megaphone, CalendarDays, Wallet } from 'lucide-react'

//어드민 패널의 탭·서브탭·경로·초기 쿼리 유효값을 관리하는 단일 소스.
//AdminDashboard.jsx, OrganizerManagement.jsx, FestivalManagement.jsx가 이 파일을 공유한다.

export const ADMIN_TABS = [
  { key: 'dashboard', label: '대시보드', icon: LayoutDashboard, path: '/admin' },
  { key: 'members', label: '회원 관리', icon: Users, path: '/admin/members' },
  { key: 'hosts', label: '주최자 관리', icon: Megaphone, path: '/admin/hosts' },
  { key: 'festivals', label: '페스티벌 관리', icon: CalendarDays, path: '/admin/festivals' },
  { key: 'settlements', label: '정산 대시보드', icon: Wallet, path: '/admin/settlements' },
]

export const ADMIN_TAB_META = {
  dashboard: { title: '대시보드', description: '플랫폼 운영 현황을 한눈에 확인합니다.' },
  members: { title: '회원 관리', description: '전체 회원을 조회하고 계정 정지·해제를 처리합니다.' },
  hosts: { title: '주최자 관리', description: '주최자 신청을 심사하고 주최자 현황을 관리합니다.' },
  festivals: { title: '페스티벌 관리', description: '페스티벌 등록과 행사 취소를 심사하고 승인·반려를 처리합니다.' },
  settlements: { title: '정산 대시보드', description: '플랫폼 거래·수수료 현황과 페스티벌별 정산 상태를 확인합니다.' },
}

export const ADMIN_SUB_TABS = {
  hosts: [
    { key: 'applications', label: '주최자 신청 승인', path: '/admin/hosts/applications' },
    { key: 'list', label: '주최자 목록', path: '/admin/hosts/list' },
  ],
  festivals: [
    { key: 'submissions', label: '페스티벌 등록 승인', path: '/admin/festivals/submissions' },
    { key: 'operations', label: '운영 현황', path: '/admin/festivals/operations' },
    { key: 'cancellations', label: '행사 취소 승인', path: '/admin/festivals/cancellations' },
  ],
}

export const ADMIN_DEFAULT_SUB = {
  hosts: 'applications',
  festivals: 'submissions',
}

//tab/sub 조합별로 쿼리 status가 허용하는 값. OrganizerManagement.jsx(ACCOUNT_FILTERS),
//FestivalOperations.jsx(OPERATION_FILTERS), CancellationRequests.jsx(CANCELLATION_FILTERS)의
//필터 키와 정확히 같아야 하며, adminNav.test.js가 이 대응을 검증한다.
export const ADMIN_STATUS_OPTIONS = {
  'hosts/list': ['ALL', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN'],
  'festivals/operations': ['ALL', 'SCHEDULED', 'ONGOING', 'CLOSED', 'CANCELLED'],
  'festivals/cancellations': ['PENDING', 'REFUNDING', 'CANCELLED', 'REJECTED'],
}

export function isValidAdminStatus(tab, sub, value) {
  if (!value) return false
  const options = ADMIN_STATUS_OPTIONS[`${tab}/${sub}`]
  return Boolean(options?.includes(value))
}

//경로가 유효하면 { status: 'ok', tab, sub }, 상위 탭만 있고 서브탭이 없으면
//{ status: 'needs-default-sub', redirectTo }, 그 외엔 { status: 'invalid' }를 반환한다.
export function parseAdminPath(pathname) {
  const normalized = pathname.replace(/\/+$/, '')

  if (normalized === '' || normalized === '/admin') {
    return { status: 'ok', tab: 'dashboard', sub: null }
  }
  if (!normalized.startsWith('/admin/')) {
    return { status: 'invalid' }
  }

  const segments = normalized.slice('/admin/'.length).split('/').filter(Boolean)
  const [tabSegment, subSegment, ...rest] = segments

  const tab = ADMIN_TABS.find((t) => t.key !== 'dashboard' && t.key === tabSegment)
  if (!tab) return { status: 'invalid' }

  const subTabs = ADMIN_SUB_TABS[tab.key]
  if (!subTabs) {
    //회원 관리·정산 대시보드는 서브탭이 없다 — 그 이상의 세그먼트가 오면 잘못된 경로다.
    if (subSegment) return { status: 'invalid' }
    return { status: 'ok', tab: tab.key, sub: null }
  }

  if (!subSegment) {
    return { status: 'needs-default-sub', redirectTo: `/admin/${tab.key}/${ADMIN_DEFAULT_SUB[tab.key]}` }
  }
  if (rest.length > 0) return { status: 'invalid' }

  const validSub = subTabs.some((s) => s.key === subSegment)
  if (!validSub) return { status: 'invalid' }

  return { status: 'ok', tab: tab.key, sub: subSegment }
}
