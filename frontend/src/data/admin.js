/**
 * 어드민 대시보드용 데이터 모듈.
 *
 * 주최자 신청 승인 / 페스티벌 등록 승인 서브탭은 실제 백엔드(/api/admin/host-applications,
 * /api/admin/festivals)와 연동되어 있고, 주최자 목록 / 정산 대시보드는 대응하는 백엔드가 아직
 * 없어 목업 데이터로 동작한다. 전부 async 함수 형태로 노출해두었으니, 주최자 목록·정산 쪽 API가
 * 생기면 이 모듈의 목업 함수만 실제 호출로 교체하면 된다.
 */
import {
  fetchPendingHostApplications,
  reviewHostApplication,
  fetchPendingFestivals,
  reviewFestival,
} from '../api/adminApi'
import { FESTIVAL_CATEGORY_LABELS, toAbsoluteImageUrl } from '../api/festivalApi'

export const REVIEW_STATUS_META = {
  PENDING: { label: '승인대기', cls: 'bg-amber-100 text-amber-700' },
  APPROVED: { label: '승인', cls: 'bg-blue-50 text-blue-600' },
  REJECTED: { label: '반려', cls: 'bg-red-50 text-red-600' },
}

export const CATEGORY_BADGE = {
  음악: 'bg-blue-50 text-blue-600',
  지역행사: 'bg-teal-50 text-teal-600',
  '공연/전시': 'bg-purple-50 text-purple-600',
  푸드: 'bg-orange-50 text-orange-600',
  문화행사: 'bg-teal-50 text-teal-600',
  스포츠: 'bg-emerald-50 text-emerald-600',
}
export const DEFAULT_CATEGORY_BADGE_CLS = 'bg-gray-100 text-gray-600'

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date
    .toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .replaceAll(' ', '')
    .replace(/\.$/, '')
}

function formatDateRange(startAt, endAt) {
  const start = formatDate(startAt)
  const end = formatDate(endAt)
  if (!start || !end) return start || end
  return start === end ? start : `${start} – ${end}`
}

/* ---------- 주최자 신청 승인 (실제 API 연동) ---------- */

const HOST_APPLICATION_ERROR_MESSAGES = {
  FORBIDDEN_ROLE: '운영자 권한이 없습니다.',
  APPLICATION_NOT_FOUND: '존재하지 않는 신청입니다. 목록을 새로고침해주세요.',
  ALREADY_REVIEWED: '이미 처리되었거나 Role 부여 처리 중인 신청입니다. 목록을 새로고침해주세요.',
  REJECT_REASON_REQUIRED: '반려 사유를 입력해주세요.',
}

/** GET /api/admin/host-applications 응답을 주최자 관리 화면이 기대하는 형태로 매핑한다.
 * 이 API는 심사 대기(PENDING) 신청만 내려주고 신청자 이름/이메일 필드도 별도로 제공하지 않아,
 * 흔히 쓰이는 필드명 후보를 순서대로 시도하고 없으면 안내 문구로 대체한다. */
function mapHostApplication(raw) {
  const name = raw.applicantNickname ?? raw.nickname ?? raw.name ?? raw.username ?? '이름 정보 없음'
  const email = raw.applicantEmail ?? raw.email ?? raw.username ?? '이메일 정보 없음'
  return {
    id: String(raw.id),
    name,
    email,
    appliedAt: formatDate(raw.createdAt),
    status: 'PENDING',
    intro: raw.introduction ?? '',
    contact: raw.contact ?? '',
  }
}

export async function fetchOrganizerApplications() {
  const response = await fetchPendingHostApplications()
  return response.data.data.map(mapHostApplication)
}

/** PATCH /api/admin/host-applications/:id 를 호출한다. 반려 시 rejectReason이 필수다. */
export async function reviewOrganizerApplication(id, { status, rejectReason }) {
  try {
    await reviewHostApplication(id, { status, rejectReason })
  } catch (error) {
    const errorCode = error.response?.data?.errorCode
    throw new Error(
      HOST_APPLICATION_ERROR_MESSAGES[errorCode] ??
        (status === 'APPROVED' ? '승인 처리에 실패했어요. 잠시 후 다시 시도해주세요.' : '반려 처리에 실패했어요. 잠시 후 다시 시도해주세요.'),
    )
  }
}

/* ---------- 페스티벌 등록 승인 (실제 API 연동) ---------- */

const FESTIVAL_REVIEW_ERROR_MESSAGES = {
  FORBIDDEN_ROLE: '운영자 권한이 없습니다.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌입니다. 목록을 새로고침해주세요.',
  ALREADY_REVIEWED: '이미 심사 처리된 페스티벌입니다. 목록을 새로고침해주세요.',
  INVALID_DECISION: '공개 또는 반려만 결정할 수 있어요.',
}

/** GET /api/admin/festivals 응답을 페스티벌 등록 승인 화면이 기대하는 형태로 매핑한다.
 * 이 API도 심사 대기 목록만 내려주고, 주최자 닉네임 필드를 별도로 제공하지 않는다. */
function mapFestivalSubmission(raw) {
  return {
    id: String(raw.id),
    name: raw.name,
    host: raw.hostNickname ?? raw.organizerNickname ?? raw.host ?? '주최자 정보 없음',
    image: toAbsoluteImageUrl(raw.thumbnailImageUrl) ?? '/placeholder.svg',
    date: formatDateRange(raw.startAt, raw.endAt),
    location: raw.location,
    category: FESTIVAL_CATEGORY_LABELS[raw.festivalCategory] ?? raw.festivalCategory,
    appliedAt: formatDate(raw.createdAt ?? raw.startAt),
    status: 'PENDING',
    description: raw.description ?? '',
    tickets: (raw.ticketTypes ?? []).map((t) => ({
      name: t.name,
      price: t.price > 0 ? `${Number(t.price).toLocaleString()}원` : '무료입장',
    })),
  }
}

export async function fetchFestivalSubmissions() {
  const response = await fetchPendingFestivals()
  return response.data.data.map(mapFestivalSubmission)
}

/** PATCH /api/admin/festivals/:id 를 호출한다. decision은 'PUBLISHED' | 'REJECTED'. */
export async function reviewFestivalSubmission(id, decision) {
  try {
    await reviewFestival(id, { decision })
  } catch (error) {
    const errorCode = error.response?.data?.errorCode
    throw new Error(
      FESTIVAL_REVIEW_ERROR_MESSAGES[errorCode] ??
        (decision === 'PUBLISHED' ? '공개 처리에 실패했어요. 잠시 후 다시 시도해주세요.' : '반려 처리에 실패했어요. 잠시 후 다시 시도해주세요.'),
    )
  }
}

/* ---------- 주최자 목록 (목업 — 대응 백엔드 없음) ---------- */

export const ACCOUNT_STATUS_META = {
  ACTIVE: { label: '활동중', cls: 'bg-emerald-100 text-emerald-700' },
  SUSPENDED: { label: '정지됨', cls: 'bg-gray-200 text-gray-600' },
  WITHDRAWN: { label: '탈퇴', cls: 'bg-red-100 text-red-600' },
}

const MOCK_ORGANIZERS = [
  {
    id: 'og1',
    nickname: '블루노트라인',
    email: 'jazz.lee@fevalgo.com',
    accountStatus: 'ACTIVE',
    approvedAt: '2026.03.14',
    festivalCount: 2,
    ticketsSold: 3820,
    revenue: '₩186,500,000',
  },
  {
    id: 'og2',
    nickname: '부산비치컬처',
    email: 'haeun.park@fevalgo.com',
    accountStatus: 'ACTIVE',
    approvedAt: '2026.02.28',
    festivalCount: 3,
    ticketsSold: 2910,
    revenue: '₩142,300,000',
  },
  {
    id: 'og3',
    nickname: '성수사운드랩',
    email: 'sungsoo.kim@fevalgo.com',
    accountStatus: 'SUSPENDED',
    approvedAt: '2026.01.09',
    festivalCount: 5,
    ticketsSold: 1740,
    revenue: '₩98,700,000',
  },
  {
    id: 'og4',
    nickname: '최무대스튜디오',
    email: 'stage.choi@fevalgo.com',
    accountStatus: 'ACTIVE',
    approvedAt: '2026.04.02',
    festivalCount: 2,
    ticketsSold: 1320,
    revenue: '₩76,400,000',
  },
  {
    id: 'og5',
    nickname: '한푸드컴퍼니',
    email: 'food.han@fevalgo.com',
    accountStatus: 'ACTIVE',
    approvedAt: '2025.12.20',
    festivalCount: 6,
    ticketsSold: 5210,
    revenue: '₩64,200,000',
  },
  {
    id: 'og6',
    nickname: '남산야경',
    email: 'night.jung@fevalgo.com',
    accountStatus: 'WITHDRAWN',
    approvedAt: '2025.11.05',
    festivalCount: 1,
    ticketsSold: 480,
    revenue: '₩12,800,000',
  },
  {
    id: 'og7',
    nickname: '라이브네이션코리아',
    email: 'live.nation@fevalgo.com',
    accountStatus: 'ACTIVE',
    approvedAt: '2025.10.18',
    festivalCount: 4,
    ticketsSold: 8940,
    revenue: '₩428,000,000',
  },
]

export async function fetchOrganizers() {
  return MOCK_ORGANIZERS.map((o) => ({ ...o }))
}

/** 목업: 주최자 권한 회수 API는 아직 없어 아무 것도 호출하지 않는다. */
export async function revokeOrganizer(id) {
  return { id }
}

/* ---------- 정산 대시보드 (목업 — 대응 백엔드 없음) ---------- */

export const settlementKpis = [
  { label: '누적 거래액 (GMV)', value: '₩1,284,500,000', delta: '+12.4%', up: true },
  { label: '정산 완료 금액', value: '₩982,300,000', delta: '+8.1%', up: true },
  { label: '총 결제 건수', value: '23,481건', delta: '+15.7%', up: true },
  { label: '정산 대기 금액', value: '₩302,200,000', delta: '-3.2%', up: false },
]

export const monthlyRevenue = [
  { month: '1월', gmv: 142, fee: 14 },
  { month: '2월', gmv: 168, fee: 17 },
  { month: '3월', gmv: 195, fee: 20 },
  { month: '4월', gmv: 176, fee: 18 },
  { month: '5월', gmv: 231, fee: 23 },
  { month: '6월', gmv: 284, fee: 28 },
]

export const settlements = [
  { id: 'st1', host: 'FevalGo Live', festivals: 4, revenue: '₩428,000,000', fee: '₩42,800,000', status: '정산완료' },
  { id: 'st2', host: '블루노트라인', festivals: 2, revenue: '₩186,500,000', fee: '₩18,650,000', status: '정산대기' },
  { id: 'st3', host: '부산 비치컬처', festivals: 3, revenue: '₩142,300,000', fee: '₩14,230,000', status: '정산대기' },
  { id: 'st4', host: '성수 사운드랩', festivals: 5, revenue: '₩98,700,000', fee: '₩9,870,000', status: '정산완료' },
  { id: 'st5', host: '최무대 스튜디오', festivals: 2, revenue: '₩76,400,000', fee: '₩7,640,000', status: '정산대기' },
  { id: 'st6', host: '한푸드', festivals: 6, revenue: '₩64,200,000', fee: '₩6,420,000', status: '정산완료' },
]

export async function fetchSettlementOverview() {
  return { kpis: settlementKpis, monthlyRevenue, settlements }
}
