import apiClient, { API_BASE_URL } from './client'
import { prefetchQuery } from './queryCache'

export function fetchFestivals(params) {
  return apiClient.get('/api/festivals', { params })
}

export function fetchFestivalDetail(id) {
  return apiClient.get(`/api/festivals/${id}`)
}

//캐시 key는 모두 'festival'로 시작해야 예매·취소 뒤 invalidateQueries('festival') 한 번으로 함께 비워진다.
export const festivalListKey = (params) => `festivals:${JSON.stringify(params)}`
export const festivalDetailKey = (id) => `festival:${id}`

//상세 GET은 서버에서 조회수를 집계하므로(인기순 근거) hover처럼 "지나가기만 해도" 발생하는 이벤트로
//미리 받으면 조회수가 부풀려진다. 클릭 직전(pointerdown)에만 부른다.
export function prefetchFestivalDetail(id) {
  prefetchQuery(festivalDetailKey(id), () => fetchFestivalDetail(id))
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

//장소 행정구역(시/도) 드롭다운 — 백엔드 FestivalRegion과 값이 같아야 한다.
export const FESTIVAL_REGIONS = [
  { value: 'SEOUL', label: '서울특별시' },
  { value: 'BUSAN', label: '부산광역시' },
  { value: 'DAEGU', label: '대구광역시' },
  { value: 'INCHEON', label: '인천광역시' },
  { value: 'GWANGJU', label: '광주광역시' },
  { value: 'DAEJEON', label: '대전광역시' },
  { value: 'ULSAN', label: '울산광역시' },
  { value: 'SEJONG', label: '세종특별자치시' },
  { value: 'GYEONGGI', label: '경기도' },
  { value: 'GANGWON', label: '강원특별자치도' },
  { value: 'CHUNGBUK', label: '충청북도' },
  { value: 'CHUNGNAM', label: '충청남도' },
  { value: 'JEONBUK', label: '전북특별자치도' },
  { value: 'JEONNAM', label: '전라남도' },
  { value: 'GYEONGBUK', label: '경상북도' },
  { value: 'GYEONGNAM', label: '경상남도' },
  { value: 'JEJU', label: '제주특별자치도' },
]

export const FESTIVAL_REGION_LABELS = Object.fromEntries(
  FESTIVAL_REGIONS.map((region) => [region.value, region.label]),
)

//region(행정구역) + locationDetail(상세주소)을 화면에 보여줄 한 문장으로 합친다.
export function formatLocation(festival) {
  const regionLabel = FESTIVAL_REGION_LABELS[festival.region] ?? festival.region ?? ''
  const detail = festival.locationDetail ?? ''
  return [regionLabel, detail].filter(Boolean).join(' ')
}

//방문자에게 노출되는 상태(PUBLISHED/CLOSED)의 배지 라벨. PENDING/REJECTED는 방문자 화면에 나타나지 않는다.
export const FESTIVAL_VISIBLE_STATUS_LABELS = {
  PUBLISHED: '진행중',
  CLOSED: '종료됨',
}

//공개된 행사라도 시작 전에는 진행중으로 오해하지 않도록 날짜를 함께 확인한다.
export function festivalVisibleStatusLabel(festival, now = new Date()) {
  if (festival.festivalStatus === 'PUBLISHED' && new Date(festival.startAt) > now) return '진행 예정'
  return FESTIVAL_VISIBLE_STATUS_LABELS[festival.festivalStatus] ?? festival.festivalStatus
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

//"마감임박" 기준: 공연 시작까지 D-3일 이내. 홈 섹션과 /festivals?sort=deadline 목록이 같은 규칙을 쓴다.
export const CLOSING_SOON_MAX_DDAY = 3

export function isClosingSoon(festival) {
  return typeof festival.dday === 'number' && festival.dday >= 0 && festival.dday <= CLOSING_SOON_MAX_DDAY
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
    location: formatLocation(festival),
    date: formatDateRange(festival.startAt, festival.endAt),
    price: formatPrice(festival.ticketTypes),
    image: toAbsoluteImageUrl(festival.thumbnailImageUrl),
    festivalStatus: festival.festivalStatus,
    dday: daysUntil(festival.startAt),
  }
}
