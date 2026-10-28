// 네이버 지도 스크립트는 한 번만 불러온다 (키는 콘솔에서 이 사이트 주소로 제한돼 있음)
const KEY_ID = import.meta.env.VITE_NAVER_MAP_KEY_ID as string | undefined

let loading: Promise<typeof naver.maps> | null = null

export function loadNaverMaps(): Promise<typeof naver.maps> {
  if (loading) {
    return loading
  }
  loading = new Promise((resolve, reject) => {
    if (!KEY_ID) {
      reject(new Error('지도 키(VITE_NAVER_MAP_KEY_ID)가 없어요.'))
      return
    }
    // 키가 틀리거나 도메인이 등록되지 않으면 네이버가 이 함수를 부른다
    ;(window as unknown as { navermap_authFailure?: () => void }).navermap_authFailure = () =>
      reject(new Error('지도 인증에 실패했어요.'))
    const script = document.createElement('script')
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(KEY_ID)}`
    script.async = true
    script.onload = () => resolve(window.naver.maps)
    script.onerror = () => {
      loading = null
      reject(new Error('지도를 불러오지 못했어요.'))
    }
    document.head.appendChild(script)
  })
  return loading
}

/** 숫자 핀 (코스 순위, 단계 순서). variant: 'rank' 순위, 'dim' 흐리게, 'step' 단계, 'target' 검색으로 고른 곳 */
export function pinIcon(label: string | number, variant: 'rank' | 'dim' | 'step' | 'target' = 'rank'): naver.maps.HtmlIcon {
  return {
    content: `<div class="pin pin-${variant}">${label}</div>`,
    anchor: new naver.maps.Point(15, 15),
  }
}

/** 반경(m)이 화면에 다 들어오는 정도의 확대 단계 */
export function zoomForRadius(radius: number): number {
  if (radius <= 600) return 16
  if (radius <= 1000) return 15
  if (radius <= 2000) return 14
  if (radius <= 4000) return 13
  if (radius <= 8000) return 12
  return 11
}

/** 지금 지도 화면을 덮는 반경(m): 가운데에서 가까운 변까지, 500m~20km */
export function visibleRadius(map: naver.maps.Map): number {
  const bounds = map.getBounds()
  const center = map.getCenter()
  const ns = distanceMeters(center.lat(), center.lng(), bounds.getNE().lat(), center.lng())
  const ew = distanceMeters(center.lat(), center.lng(), center.lat(), bounds.getNE().lng())
  return Math.round(Math.min(20000, Math.max(500, Math.min(ns, ew))) / 100) * 100
}

export function distanceMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLng = (lng2 - lng1) * rad
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.sqrt(a))
}

/** 지도를 그 자리·확대로 바로 옮긴다 (morph 애니메이션은 탭이 뒤에 있으면 확대가 덜 된 채 멈추는 일이 있어서) */
export function jumpTo(map: naver.maps.Map, lat: number, lng: number, zoom: number) {
  map.setZoom(zoom, false)
  map.setCenter(new naver.maps.LatLng(lat, lng))
}
