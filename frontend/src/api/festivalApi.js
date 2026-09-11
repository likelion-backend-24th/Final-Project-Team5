import apiClient, { API_BASE_URL } from './client'

export function fetchFestivals(params) {
  return apiClient.get('/api/festivals', { params })
}

export function fetchFestivalDetail(id) {
  return apiClient.get(`/api/festivals/${id}`)
}

export const FESTIVAL_CATEGORIES = [
  { value: 'MUSIC', label: '음악' },
  { value: 'LOCAL', label: '지역행사' },
  { value: 'FOOD', label: '푸드' },
  { value: 'CULTURE', label: '문화행사' },
  { value: 'SPORTS', label: '스포츠' },
]

export const FESTIVAL_CATEGORY_LABELS = Object.fromEntries(
  FESTIVAL_CATEGORIES.map((category) => [category.value, category.label]),
)

//방문자에게 노출되는 상태(PUBLISHED/CLOSED)의 배지 라벨. PENDING/REJECTED는 방문자 화면에 나타나지 않는다.
export const FESTIVAL_VISIBLE_STATUS_LABELS = {
  PUBLISHED: '진행중',
  CLOSED: '종료됨',
}

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replaceAll(' ', '')
}

function formatDateRange(startAt, endAt) {
  const start = formatDate(startAt)
  const end = formatDate(endAt)
  if (!start || !end) return ''
  return start === end ? start : `${start} – ${end}`
}

function formatPrice(ticketTypes) {
  if (!ticketTypes || ticketTypes.length === 0) return '가격 정보 없음'
  const minPrice = Math.min(...ticketTypes.map((ticketType) => ticketType.price))
  return minPrice <= 0 ? '무료입장' : `${minPrice.toLocaleString()}원~`
}

//백엔드가 돌려주는 imageUrls는 도메인 없는 상대 경로(/api/festivals/images/...)라 API_BASE_URL을 붙여야 <img>에 바로 쓸 수 있다.
export function toAbsoluteImageUrl(imageUrl) {
  return imageUrl ? `${API_BASE_URL}${imageUrl}` : null
}

function daysUntil(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return Math.ceil((date.setHours(0, 0, 0, 0) - new Date().setHours(0, 0, 0, 0)) / (1000 * 60 * 60 * 24))
}

/** FestivalResponseDto(백엔드)를 기존 FestivalCard/mock 데이터 형태로 변환한다. */
export function mapFestivalToCard(festival) {
  return {
    id: festival.id,
    title: festival.name,
    category: festival.festivalCategory,
    location: festival.location,
    date: formatDateRange(festival.startAt, festival.endAt),
    price: formatPrice(festival.ticketTypes),
    image: toAbsoluteImageUrl(festival.thumbnailImageUrl),
    festivalStatus: festival.festivalStatus,
    dday: daysUntil(festival.startAt),
  }
}
