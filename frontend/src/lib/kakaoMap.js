//카카오맵 JS SDK를 딱 한 번만 동적으로 로드해 재사용한다. 지도가 필요한 페이지(등록 폼·상세 페이지)에서만
//불러오고, 다른 페이지에서는 이 스크립트를 아예 요청하지 않는다.
//autoload=false + kakao.maps.load(cb)로 초기화 시점을 직접 제어한다 — 스크립트 로드 직후 바로 kakao.maps를
//쓰면 아직 내부 초기화가 안 끝나 있을 수 있어서다(카카오 공식 가이드 권장 패턴).
//좌표가 없을 때 보여줄 기본 중심 좌표 — 서울시청. pick 모드의 초기 중심으로도, view 모드에서 좌표가
//없는 페스티벌의 대체 표시 위치로도 같이 쓴다.
export const DEFAULT_CENTER = { latitude: 37.56672510131013, longitude: 126.97869756327125 }

let loadPromise = null

export function loadKakaoMaps() {
  if (window.kakao?.maps?.services) return Promise.resolve(window.kakao)
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    const appKey = import.meta.env.VITE_KAKAO_MAP_APP_KEY
    if (!appKey) {
      loadPromise = null
      reject(new Error('지도를 불러올 수 없어요. (VITE_KAKAO_MAP_APP_KEY 미설정)'))
      return
    }

    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&libraries=services&autoload=false`
    script.async = true
    script.onload = () => {
      window.kakao.maps.load(() => resolve(window.kakao))
    }
    script.onerror = () => {
      loadPromise = null
      reject(new Error('지도를 불러오지 못했어요. 잠시 후 다시 시도해주세요.'))
    }
    document.head.appendChild(script)
  })

  return loadPromise
}

//카카오 주소 관련 API가 돌려주는 시/도 명칭을 백엔드 FestivalRegion enum 값으로 매핑한다.
//API마다 표기가 다르다 — coord2regioncode는 "경기도" 같은 전체 명칭을, coord2Address/주소·키워드
//검색은 "경기" 같은 축약형을 region_1depth_name(또는 주소 문자열 첫 토큰)으로 돌려준다. 실제로
//지도 클릭·주소 검색에서 쓰는 API들은 전부 축약형이라, 축약형이 빠져 있으면 지역을 영영 못 채운다
//(예전에 전체 명칭만 등록해놨던 버그 — 드롭다운이 남아있어 안 드러났을 뿐 실제로는 안 채워지고 있었다).
//2023~2024년 사이 강원/전북의 행정구역 명칭이 바뀌어서(강원도→강원특별자치도 등) 신/구 명칭도 다 등록한다.
const REGION_NAME_TO_ENUM = {
  '서울': 'SEOUL',
  '서울특별시': 'SEOUL',
  '부산': 'BUSAN',
  '부산광역시': 'BUSAN',
  '대구': 'DAEGU',
  '대구광역시': 'DAEGU',
  '인천': 'INCHEON',
  '인천광역시': 'INCHEON',
  '광주': 'GWANGJU',
  '광주광역시': 'GWANGJU',
  '대전': 'DAEJEON',
  '대전광역시': 'DAEJEON',
  '울산': 'ULSAN',
  '울산광역시': 'ULSAN',
  '세종': 'SEJONG',
  '세종특별자치시': 'SEJONG',
  '경기': 'GYEONGGI',
  '경기도': 'GYEONGGI',
  '강원': 'GANGWON',
  '강원특별자치도': 'GANGWON',
  '강원도': 'GANGWON',
  '충북': 'CHUNGBUK',
  '충청북도': 'CHUNGBUK',
  '충남': 'CHUNGNAM',
  '충청남도': 'CHUNGNAM',
  '전북': 'JEONBUK',
  '전북특별자치도': 'JEONBUK',
  '전라북도': 'JEONBUK',
  '전남': 'JEONNAM',
  '전라남도': 'JEONNAM',
  '경북': 'GYEONGBUK',
  '경상북도': 'GYEONGBUK',
  '경남': 'GYEONGNAM',
  '경상남도': 'GYEONGNAM',
  '제주': 'JEJU',
  '제주특별자치도': 'JEJU',
  '제주도': 'JEJU',
}

export function mapKakaoRegionToFestivalRegion(region1DepthName) {
  if (!region1DepthName) return null
  return REGION_NAME_TO_ENUM[region1DepthName] ?? null
}

//카카오 주소 문자열은 항상 "시/도 시/군/구 …" 순이라, 구조화된 region_1depth_name이 없을 때(키워드 장소
//검색 결과 등은 문자열 하나만 준다) 첫 토큰만 꺼내 지역을 판별한다.
export function regionFromAddressName(addressName) {
  if (!addressName) return null
  const firstToken = addressName.trim().split(/\s+/)[0]
  return mapKakaoRegionToFestivalRegion(firstToken)
}

//지역 드롭다운과 겹치는 맨 앞 시/도 토큰만 잘라 상세주소로 쓴다("경기 용인시 처인구…" → "용인시 처인구…").
export function stripRegionPrefix(addressName) {
  if (!addressName) return ''
  const rest = addressName.trim().split(/\s+/).slice(1).join(' ')
  return rest || addressName
}

//카카오 키워드 장소 검색(REST가 아니라 이미 로드된 JS SDK의 services.Places) — 주소 검색 입력창과
//AI 초안의 locationQuery 둘 다 여기서 재사용한다. 실패·결과없음이면 빈 배열을 돌려준다(호출부가 각자
//사용자에게 어떻게 안내할지 결정).
export function searchPlaces(keyword) {
  return loadKakaoMaps().then(
    (kakao) =>
      new Promise((resolve) => {
        const places = new kakao.maps.services.Places()
        places.keywordSearch(keyword, (data, status) => {
          resolve(status === kakao.maps.services.Status.OK ? data : [])
        })
      }),
  )
}

//카카오 장소 검색 결과 1건을 폼에 바로 넣을 수 있는 {latitude, longitude, region, locationDetail} 모양으로 바꾼다.
export function placeToLocationFields(place) {
  const fullAddress = place.road_address_name || place.address_name
  return {
    latitude: Number(place.y),
    longitude: Number(place.x),
    region: regionFromAddressName(fullAddress),
    locationDetail: stripRegionPrefix(fullAddress),
  }
}
