/**
 * 홈페이지용 목(mock) 데이터.
 * 추후 백엔드 API 연동 시 이 모듈만 교체하면 컴포넌트는 그대로 쓸 수 있다.
 */

/** 카테고리 필터 칩 목록 */
export const CATEGORIES = [
  { id: 'all', label: '전체' },
  { id: 'music', label: '음악' },
  { id: 'show', label: '공연/전시' },
  { id: 'food', label: '푸드' },
  { id: 'culture', label: '문화행사' },
  { id: 'sports', label: '스포츠' },
]

export const CATEGORY_LABELS = Object.fromEntries(
  CATEGORIES.map((c) => [c.id, c.label]),
)

/**
 * @typedef {Object} Festival
 * @property {string} id
 * @property {string} title
 * @property {string} category      CATEGORIES의 id (all 제외)
 * @property {string} location
 * @property {string} date          화면에 그대로 노출되는 기간 문자열
 * @property {string} price
 * @property {string} image         public/ 기준 경로
 * @property {boolean} [featured]   메인 배너 캐러셀 노출 여부
 * @property {string} [badge]
 * @property {number} [dday]        마감까지 남은 일수 (있으면 마감임박 섹션에 노출)
 */

/** @type {Festival[]} */
export const FESTIVALS = [
  {
    id: 'seoul-summer-music',
    title: '서울 썸머 뮤직 페스티벌 2027',
    category: 'music',
    location: '서울 잠실 올림픽주경기장',
    date: '2027.07.18 – 07.20',
    price: '99,000원~',
    image: '/festivals/hero-seoul-music.webp',
    featured: true,
    badge: '예매 오픈',
  },
  {
    id: 'night-food-market',
    title: '한강 나이트 푸드 마켓',
    category: 'food',
    location: '서울 여의도 한강공원',
    date: '2026.10.01 – 10.15',
    price: '무료입장',
    image: '/festivals/hero-food-night.webp',
    featured: true,
    badge: 'HOT',
    dday: 5,
  },
  {
    id: 'indie-live-club',
    title: '인디 라이브 클럽 나이트',
    category: 'music',
    location: '서울 홍대 롤링홀',
    date: '2026.09.28',
    price: '44,000원~',
    image: '/festivals/card-indie.webp',
    badge: '마감임박',
    dday: 2,
  },
  {
    id: 'seoul-philharmonic',
    title: '서울 필하모닉 가을 정기공연',
    category: 'show',
    location: '서울 예술의전당 콘서트홀',
    date: '2026.11.05',
    price: '55,000원~',
    image: '/festivals/card-classical.webp',
  },
  {
    id: 'modern-art-expo',
    title: '모던 아트 엑스포 2026',
    category: 'show',
    location: '서울 DDP 아트홀',
    date: '2026.10.20 – 12.30',
    price: '18,000원~',
    image: '/festivals/card-art.webp',
  },
  {
    id: 'park-food-truck',
    title: '올림픽공원 푸드트럭 페스타',
    category: 'food',
    location: '서울 올림픽공원 잔디마당',
    date: '2026.09.12 – 09.14',
    price: '무료입장',
    image: '/festivals/card-food.webp',
    dday: 7,
  },
  {
    id: 'edm-electric-night',
    title: '일렉트릭 나이트 EDM 페스티벌',
    category: 'music',
    location: '인천 파라다이스시티',
    date: '2026.11.22',
    price: '132,000원~',
    image: '/festivals/card-edm.webp',
    badge: 'HOT',
  },
  {
    id: 'grand-musical',
    title: '뮤지컬 〈그랜드 스테이지〉',
    category: 'show',
    location: '서울 블루스퀘어 신한카드홀',
    date: '2026.10.01 – 2027.01.30',
    price: '66,000원~',
    image: '/festivals/card-musical.webp',
  },
  {
    id: 'hanok-village-night',
    title: '전주 한옥마을 야행(夜行)',
    category: 'culture',
    location: '전북 전주 한옥마을 일원',
    date: '2026.10.09 – 10.11',
    price: '12,000원~',
    image: '/festivals/card-art.webp',
    badge: '지역행사',
  },
  {
    id: 'lantern-festival',
    title: '진주 남강 유등축제',
    category: 'culture',
    location: '경남 진주 남강 일원',
    date: '2026.10.17 – 10.25',
    price: '무료입장',
    image: '/festivals/card-musical.webp',
    dday: 3,
  },
  {
    id: 'seoul-city-marathon',
    title: '서울 시티 마라톤 2026',
    category: 'sports',
    location: '서울 광화문 → 잠실종합운동장',
    date: '2026.11.01',
    price: '45,000원~',
    image: '/festivals/card-edm.webp',
    badge: '접수중',
    dday: 9,
  },
  {
    id: 'busan-surf-open',
    title: '부산 송정 서프 오픈',
    category: 'sports',
    location: '부산 송정해수욕장',
    date: '2026.09.26 – 09.27',
    price: '22,000원~',
    image: '/festivals/card-food.webp',
  },
]

/** 메인 배너 캐러셀에 노출할 페스티벌 */
export const FEATURED_FESTIVALS = FESTIVALS.filter((f) => f.featured)

/** 마감임박 섹션: 남은 일수가 짧은 순 */
export const CLOSING_SOON_FESTIVALS = FESTIVALS.filter(
  (f) => typeof f.dday === 'number',
).sort((a, b) => a.dday - b.dday)

/** 카테고리로 거른 목록. `all`이면 전체를 그대로 돌려준다. */
export function filterByCategory(categoryId) {
  if (categoryId === 'all') return FESTIVALS
  return FESTIVALS.filter((f) => f.category === categoryId)
}
