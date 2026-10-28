import { createContext, useContext } from 'react'

export interface MapState {
  /** 아직 불러오는 중이거나 실패하면 null */
  map: naver.maps.Map | null
  error: string | null
  /** 지도 위에 띄울 버튼을 넣는 자리 (createPortal 대상) */
  overlay: HTMLElement | null
}

export const MapContext = createContext<MapState>({ map: null, error: null, overlay: null })

/** 모든 화면이 함께 쓰는 지도 하나 (화면을 옮겨도 다시 만들지 않는다: 지도를 만들 때마다 이용 횟수가 늘어서) */
export function useMap(): MapState {
  return useContext(MapContext)
}
