import type { AiParse } from '../components/AiAsk'
import { putShop, shopOf } from './shop'

/**
 * AI 검색 결과 → 코스 순위 주소 (홈과 코스 순위 화면이 같이 쓴다).
 *   - 지점(성수동·반석역·광안리해수욕장)이 있으면 그 반경 (+ 풍경)
 *   - 지점 없이 시도("부산", "전라도")가 있으면 그 시도 전체 (+ 풍경)
 *   - 풍경만 있으면("바다 가고 싶어") 전국
 *   - 아무것도 못 알아들었으면 지금 지도 가운데(here)
 *   - 메뉴(food)는 어디서든 붙는다 (그 메뉴 가게가 든 코스를 위로)
 *   - 지점이 가게면 가게도 싣는다 (lib/shop.ts)
 * here 가 없고 지점·시도·풍경도 없으면 null
 */
export function searchUrlOf(q: string, r: AiParse, here: { lat: number; lng: number; radius: number } | null): string | null {
  const params = new URLSearchParams()
  if (r.area || (!r.sidos.length && !r.scene)) {
    const c = r.area ? { lat: r.area.lat, lng: r.area.lng, radius: r.area.radius } : here
    if (!c) return null
    params.set('lat', c.lat.toFixed(5))
    params.set('lng', c.lng.toFixed(5))
    // 명소·주소를 찾아 바짝 당겨 본 뒤라도 코스는 걸어 다닐 만한 넓이(1.5km 이상)에서 찾는다
    params.set('r', String(Math.min(20000, Math.max(1500, c.radius))))
  } else if (r.sidos.length) {
    params.set('sido', r.sidos.join(','))
  }
  if (r.scene) params.set('scene', r.scene)
  if (r.food) params.set('food', r.food)
  // 가게 이름이면 그 가게가 든 코스를 위로, 다른 코스에는 넣어 보기
  putShop(params, shopOf(r.area))
  if (r.keywords.length) params.set('kw', r.keywords.join(','))
  params.set('q', q)
  return `/search?${params}`
}
