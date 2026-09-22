//카카오맵 JS SDK를 딱 한 번만 동적으로 로드해 재사용한다. 지도가 필요한 페이지(등록 폼·상세 페이지)에서만
//불러오고, 다른 페이지에서는 이 스크립트를 아예 요청하지 않는다.
//autoload=false + kakao.maps.load(cb)로 초기화 시점을 직접 제어한다 — 스크립트 로드 직후 바로 kakao.maps를
//쓰면 아직 내부 초기화가 안 끝나 있을 수 있어서다(카카오 공식 가이드 권장 패턴).
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

//카카오 좌표→주소 변환(coord2Address)이 돌려주는 region_1depth_name(시/도 한글명)을
//백엔드 FestivalRegion enum 값으로 매핑한다. 2023~2024년 사이 강원/전북의 행정구역 명칭이
//바뀌어서(강원도→강원특별자치도, 전라북도→전북특별자치도), 카카오 데이터가 어느 쪽을 반환하든
//받아지도록 신/구 명칭을 모두 등록해둔다.
const REGION_NAME_TO_ENUM = {
  '서울특별시': 'SEOUL',
  '부산광역시': 'BUSAN',
  '대구광역시': 'DAEGU',
  '인천광역시': 'INCHEON',
  '광주광역시': 'GWANGJU',
  '대전광역시': 'DAEJEON',
  '울산광역시': 'ULSAN',
  '세종특별자치시': 'SEJONG',
  '경기도': 'GYEONGGI',
  '강원특별자치도': 'GANGWON',
  '강원도': 'GANGWON',
  '충청북도': 'CHUNGBUK',
  '충청남도': 'CHUNGNAM',
  '전북특별자치도': 'JEONBUK',
  '전라북도': 'JEONBUK',
  '전라남도': 'JEONNAM',
  '경상북도': 'GYEONGBUK',
  '경상남도': 'GYEONGNAM',
  '제주특별자치도': 'JEJU',
  '제주도': 'JEJU',
}

export function mapKakaoRegionToFestivalRegion(region1DepthName) {
  if (!region1DepthName) return null
  return REGION_NAME_TO_ENUM[region1DepthName] ?? null
}
