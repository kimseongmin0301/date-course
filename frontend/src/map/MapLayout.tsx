import { useEffect, useRef, useState, type ReactNode } from 'react'
import { loadNaverMaps } from '../lib/naverMap'
import { MapContext } from './context'

// 처음 지도 가운데: 서울시청
const START = { lat: 37.5666, lng: 126.9784, zoom: 13 }

/** 왼쪽(휴대폰은 아래) 화면 + 오른쪽(휴대폰은 위) 지도. 지도는 앱이 떠 있는 동안 하나만 만든다 */
export default function MapLayout({ children }: { children: ReactNode }) {
  const el = useRef<HTMLDivElement>(null)
  const overlayEl = useRef<HTMLDivElement>(null)
  const [map, setMap] = useState<naver.maps.Map | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [overlay, setOverlay] = useState<HTMLElement | null>(null)

  useEffect(() => {
    setOverlay(overlayEl.current)
    let cancelled = false
    loadNaverMaps()
      .then((maps) => {
        if (cancelled || !el.current) return
        setMap(
          new maps.Map(el.current, {
            center: new maps.LatLng(START.lat, START.lng),
            zoom: START.zoom,
            minZoom: 7,
            scaleControl: false,
            mapDataControl: false,
            zoomControl: true,
            zoomControlOptions: { position: maps.Position.TOP_RIGHT, style: maps.ZoomControlStyle.SMALL },
          }),
        )
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <MapContext.Provider value={{ map, error, overlay }}>
      <div className="layout">
        <div className="panel">{children}</div>
        <div className="map-area">
          <div ref={el} className="map" />
          <div ref={overlayEl} className="map-overlay" />
          {error && <p className="map-error">{error}</p>}
        </div>
      </div>
    </MapContext.Provider>
  )
}
