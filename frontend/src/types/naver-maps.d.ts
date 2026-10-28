// 네이버 지도 JavaScript API v3 (Dynamic Map) 중 이 앱이 쓰는 것만 적은 타입
// https://navermaps.github.io/maps.js.ncp/docs/
declare namespace naver.maps {
  class LatLng {
    constructor(lat: number, lng: number)
    lat(): number
    lng(): number
  }

  class Point {
    constructor(x: number, y: number)
  }

  class LatLngBounds {
    constructor(sw: LatLng, ne: LatLng)
    extend(latlng: LatLng): LatLngBounds
    getSW(): LatLng
    getNE(): LatLng
    getCenter(): LatLng
  }

  interface MapOptions {
    center?: LatLng
    zoom?: number
    minZoom?: number
    scaleControl?: boolean
    mapDataControl?: boolean
    logoControlOptions?: { position: number }
    zoomControl?: boolean
    zoomControlOptions?: { position: number; style?: number }
  }

  class Map {
    constructor(el: HTMLElement, options?: MapOptions)
    getCenter(): LatLng
    getZoom(): number
    getBounds(): LatLngBounds
    setCenter(center: LatLng): void
    setZoom(zoom: number, effect?: boolean): void
    morph(center: LatLng, zoom?: number): void
    panTo(center: LatLng): void
    fitBounds(bounds: LatLngBounds, margin?: { top: number; right: number; bottom: number; left: number }): void
    autoResize(): void
  }

  interface HtmlIcon {
    content: string
    anchor?: Point
  }

  class Marker {
    constructor(options: { position: LatLng; map?: Map | null; icon?: HtmlIcon; title?: string; zIndex?: number; clickable?: boolean })
    setMap(map: Map | null): void
  }

  class Polyline {
    constructor(options: {
      map?: Map | null
      path: LatLng[]
      strokeColor?: string
      strokeWeight?: number
      strokeOpacity?: number
      strokeStyle?: string
      strokeLineCap?: string
      strokeLineJoin?: string
    })
    setMap(map: Map | null): void
  }

  type MapEventListener = { readonly __listener: unique symbol }

  namespace Event {
    function addListener(target: object, type: string, listener: () => void): MapEventListener
    function removeListener(listener: MapEventListener): void
  }

  const Position: { TOP_LEFT: number; TOP_RIGHT: number; BOTTOM_LEFT: number; BOTTOM_RIGHT: number; RIGHT_CENTER: number }
  const ZoomControlStyle: { SMALL: number; LARGE: number }
}
