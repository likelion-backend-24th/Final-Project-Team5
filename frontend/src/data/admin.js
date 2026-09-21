/**
 * 어드민 대시보드용 데이터 모듈.
 *
 * 주최자 신청 승인 / 페스티벌 등록 승인 / 주최자 목록 서브탭은 실제 백엔드와 연동되어 있다.
 * 정산 대시보드는 settlementApi와 SettlementReport에서 실제 API를 사용한다.
 */
import {
  fetchAdminHosts,
  fetchPendingHostApplications,
  reviewHostApplication,
  fetchPendingFestivals,
  reviewFestival,
} from '../api/adminApi'
import { FESTIVAL_CATEGORY_LABELS, toAbsoluteImageUrl } from '../api/festivalApi'

export const REVIEW_STATUS_META = {
  PENDING: { label: '승인대기', cls: 'bg-amber-100 text-amber-700' },
  //승인은 눌렀지만 auth-service의 권한 부여 응답을 못 받은 상태. 서버 배치가 자동 재시도하고, 운영자가 다시 승인을 눌러도 된다.
  APPROVAL_PENDING: { label: '승인 처리중', cls: 'bg-blue-100 text-blue-700' },
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
  FORBIDDEN_ADMIN_ROLE: '운영자 권한이 없습니다.',
  APPLICATION_NOT_FOUND: '존재하지 않는 신청입니다. 목록을 새로고침해주세요.',
  ALREADY_REVIEWED: '이미 처리되었거나 Role 부여 처리 중인 신청입니다. 목록을 새로고침해주세요.',
  REJECT_REASON_REQUIRED: '반려 사유를 입력해주세요.',
}

/** GET /api/admin/host-applications 응답을 주최자 관리 화면이 기대하는 형태로 매핑한다.
 * 이제 승인·반려된 신청도 함께 내려오고(이력), 신청자 이름·닉네임·이메일은 festival-service가
 * auth-service에서 조회해 채워준다(조회 실패 시 null → 안내 문구). */
function mapHostApplication(raw) {
  const nickname = raw.applicantNickname ?? '닉네임 정보 없음'
  const name = raw.applicantName ? `${nickname} (${raw.applicantName})` : nickname
  const email = raw.applicantEmail ?? '이메일 정보 없음'
  return {
    id: String(raw.id),
    name,
    email,
    appliedAt: formatDate(raw.createdAt),
    reviewedAt: raw.status === 'PENDING' ? '' : formatDate(raw.updatedAt),
    status: raw.status,
    rejectReason: raw.rejectReason ?? '',
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
    //승인은 권한 부여 응답을 못 받으면 APPROVAL_PENDING으로 돌아올 수 있어, 서버가 확정한 상태를 그대로 돌려준다.
    const response = await reviewHostApplication(id, { status, rejectReason })
    return response.data.data.status
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
  FORBIDDEN_ADMIN_ROLE: '운영자 권한이 없습니다.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌입니다. 목록을 새로고침해주세요.',
  ALREADY_REVIEWED: '이미 심사 처리된 페스티벌입니다. 목록을 새로고침해주세요.',
  INVALID_DECISION: '공개 또는 반려만 결정할 수 있어요.',
  REJECT_REASON_REQUIRED: '반려 사유를 입력해주세요.',
}

//공개 이후에만 도달할 수 있는 종료·취소 상태는 심사 이력에서 승인으로 표시한다.
const FESTIVAL_UI_STATUS = {
  PENDING: 'PENDING',
  PUBLISH_PENDING: 'PENDING',
  PUBLISHED: 'APPROVED',
  CLOSED: 'APPROVED',
  CANCELLATION_PENDING: 'APPROVED',
  CANCELLED: 'APPROVED',
  REJECTED: 'REJECTED',
}

/** GET /api/admin/festivals 응답을 페스티벌 등록 승인 화면이 기대하는 형태로 매핑한다.
 * 이제 공개·반려·종료된 페스티벌도 이력으로 함께 내려오고, 주최자 닉네임은 festival-service가
 * auth-service에서 조회해 채워준다(조회 실패 시 null → 안내 문구). */
function mapFestivalSubmission(raw) {
  return {
    id: String(raw.id),
    name: raw.name,
    host: raw.hostNickname ?? '주최자 정보 없음',
    image: toAbsoluteImageUrl(raw.thumbnailImageUrl) ?? '/placeholder.jpg',
    date: formatDateRange(raw.startAt, raw.endAt),
    location: raw.location,
    category: FESTIVAL_CATEGORY_LABELS[raw.festivalCategory] ?? raw.festivalCategory,
    appliedAt: formatDate(raw.createdAt ?? raw.startAt),
    status: FESTIVAL_UI_STATUS[raw.festivalStatus] ?? 'PENDING',
    rawStatus: raw.festivalStatus,
    rejectReason: raw.rejectReason ?? '',
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

/** PATCH /api/admin/festivals/:id 를 호출한다. decision은 'PUBLISHED' | 'REJECTED'. 반려 시 rejectReason이 필수다. */
export async function reviewFestivalSubmission(id, decision, rejectReason) {
  try {
    await reviewFestival(id, { decision, rejectReason })
  } catch (error) {
    const errorCode = error.response?.data?.errorCode
    throw new Error(
      FESTIVAL_REVIEW_ERROR_MESSAGES[errorCode] ??
        (decision === 'PUBLISHED' ? '공개 처리에 실패했어요. 잠시 후 다시 시도해주세요.' : '반려 처리에 실패했어요. 잠시 후 다시 시도해주세요.'),
    )
  }
}

/* ---------- 주최자 목록 ---------- */

export const ACCOUNT_STATUS_META = {
  ACTIVE: { label: '활동중', cls: 'bg-emerald-100 text-emerald-700' },
  PENDING_ACTIVATION: { label: '활성화 대기', cls: 'bg-amber-100 text-amber-700' },
  REVOKED: { label: '해지', cls: 'bg-gray-200 text-gray-600' },
  SUSPENDED: { label: '정지됨', cls: 'bg-gray-200 text-gray-600' },
  WITHDRAWN: { label: '탈퇴', cls: 'bg-red-100 text-red-600' },
}

const ORGANIZER_LIST_ERROR_MESSAGES = {
  FORBIDDEN_ADMIN_ROLE: '운영자 권한이 없습니다.',
}

export async function fetchOrganizers() {
  try {
    const [hostsResponse, festivalsResponse] = await Promise.all([
      fetchAdminHosts(),
      fetchPendingFestivals(),
    ])
    const festivalCounts = (festivalsResponse.data.data ?? []).reduce((counts, festival) => {
      counts.set(festival.hostUserId, (counts.get(festival.hostUserId) ?? 0) + 1)
      return counts
    }, new Map())

    return (hostsResponse.data.data ?? []).map((host) => ({
      id: String(host.id),
      nickname: host.nickname,
      email: host.email,
      accountStatus: host.accountStatus,
      joinedAt: formatDate(host.joinedAt),
      festivalCount: festivalCounts.get(host.id) ?? 0,
    }))
  } catch (error) {
    const errorCode = error.response?.data?.errorCode
    throw new Error(
      ORGANIZER_LIST_ERROR_MESSAGES[errorCode] ??
        '주최자 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.',
    )
  }
}
