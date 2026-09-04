import apiClient from './client'

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

/** FestivalResponseDto(백엔드)를 기존 FestivalCard/mock 데이터 형태로 변환한다. */
export function mapFestivalToCard(festival) {
  return {
    id: festival.id,
    title: festival.name,
    category: festival.festivalCategory,
    location: festival.location,
    date: formatDateRange(festival.startAt, festival.endAt),
    price: formatPrice(festival.ticketTypes),
    image: null,
  }
}
