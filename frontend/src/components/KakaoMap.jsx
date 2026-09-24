import { useEffect, useRef, useState } from 'react'
import { DEFAULT_CENTER, loadKakaoMaps, mapKakaoRegionToFestivalRegion, stripRegionPrefix } from '../lib/kakaoMap'

/**
 * 카카오맵 표시/클릭-선택 겸용 컴포넌트.
 * mode="pick": 클릭한 좌표를 역지오코딩해 { latitude, longitude, region, locationDetail }를 onPick으로 올려준다.
 * mode="view": 주어진 좌표에 마커만 찍어 보여준다(클릭 비활성).
 * latitude/longitude가 없으면 pick 모드는 서울시청을 기본 중심으로 시작하고, view 모드는 아예 렌더링하지 않는 게 낫다(호출부에서 조건부 렌더링).
 */
function KakaoMap({ mode = 'view', latitude, longitude, onPick, height = 280 }) {
  const containerRef = useRef(null)
  const mapObjRef = useRef(null)
  const markerRef = useRef(null)
  const [status, setStatus] = useState('loading') // 'loading' | 'ready' | 'error'
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let cancelled = false

    loadKakaoMaps()
      .then((kakao) => {
        if (cancelled || !containerRef.current) return

        const center = new kakao.maps.LatLng(
          latitude ?? DEFAULT_CENTER.latitude,
          longitude ?? DEFAULT_CENTER.longitude,
        )
        const map = new kakao.maps.Map(containerRef.current, {
          center,
          level: latitude != null ? 4 : 12,
        })
        mapObjRef.current = map

        if (latitude != null && longitude != null) {
          markerRef.current = new kakao.maps.Marker({ position: center, map })
        }

        if (mode === 'pick') {
          const geocoder = new kakao.maps.services.Geocoder()
          kakao.maps.event.addListener(map, 'click', (mouseEvent) => {
            const latlng = mouseEvent.latLng
            if (markerRef.current) {
              markerRef.current.setPosition(latlng)
            } else {
              markerRef.current = new kakao.maps.Marker({ position: latlng, map })
            }

            geocoder.coord2Address(latlng.getLng(), latlng.getLat(), (result, geocodeStatus) => {
              if (geocodeStatus !== kakao.maps.services.Status.OK || cancelled) return
              const address = result[0]?.address
              const roadAddress = result[0]?.road_address
              const fullAddress = roadAddress?.address_name || address?.address_name || ''
              onPick?.({
                latitude: latlng.getLat(),
                longitude: latlng.getLng(),
                region: mapKakaoRegionToFestivalRegion(address?.region_1depth_name),
                locationDetail: stripRegionPrefix(fullAddress),
              })
            })
          })
        }

        setStatus('ready')
      })
      .catch((error) => {
        if (cancelled) return
        setErrorMessage(error.message)
        setStatus('error')
      })

    return () => {
      cancelled = true
    }
    //지도는 한 번만 만든다 — latitude/longitude/onPick을 의도적으로 deps에서 뺐다. view 모드는 상세 데이터가
    //로딩된 뒤 조건부 렌더링으로만 좌표를 넘기므로 마운트 시점 값 그대로 충분하다.
  }, [mode])

  //pick 모드에서는 지도 클릭 외에도(주소 검색 결과 선택 등) 좌표가 바뀔 수 있다 — 그럴 때 지도가 따라
  //움직이도록 별도로 감지한다. 클릭으로 인한 변경도 같은 값으로 다시 세팅될 뿐이라 문제 없다.
  useEffect(() => {
    const map = mapObjRef.current
    if (!map || status !== 'ready' || latitude == null || longitude == null) return

    const kakao = window.kakao
    const position = new kakao.maps.LatLng(latitude, longitude)
    map.setCenter(position)
    map.setLevel(4)
    if (markerRef.current) {
      markerRef.current.setPosition(position)
    } else {
      markerRef.current = new kakao.maps.Marker({ position, map })
    }
  }, [latitude, longitude, status])

  return (
    <div>
      <div
        ref={containerRef}
        style={{ width: '100%', height, borderRadius: 16, overflow: 'hidden', backgroundColor: '#f3f4f6' }}
      />
      {status === 'error' && (
        <p style={{ marginTop: 6, fontSize: 12, color: 'var(--fgColor-danger, #dc2626)' }}>{errorMessage}</p>
      )}
      {mode === 'pick' && status === 'ready' && (
        <p style={{ marginTop: 6, fontSize: 12, color: 'var(--fgColor-muted, #6b7280)' }}>
          지도를 클릭하면 위 주소 칸이 자동으로 채워져요. 이후에도 직접 수정할 수 있어요.
        </p>
      )}
    </div>
  )
}

export default KakaoMap
