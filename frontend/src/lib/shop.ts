// 가게 이름으로 찾기: 그 가게가 든 코스를 위로 올리고(서버 shop=id), 다른 코스에는 "넣어 보기"로 그 가게를 넣은 일정을 보여준다.
// 주소에 가게를 실어 코스 순위 → 코스 상세로 넘긴다: shop(우리 데이터의 id, 네이버에서만 찾았으면 없음)·sname·slat·slng·saddr·scat
import { distanceMeters } from './naverMap'
import { defaultStay, type Plan, type PlanStep } from './plan'
import type { LocationResult, Place } from './types'

export interface Shop {
  /** 우리 데이터의 가게 id (네이버에서만 찾은 가게는 null) */
  id: number | null
  name: string
  lat: number
  lng: number
  address: string
  /** cafe·restaurant·bar·shop·activity: 코스의 어느 단계를 바꿀지 */
  category: string
}

const KEYS = ['shop', 'sname', 'slat', 'slng', 'saddr', 'scat'] as const

/** 지역 검색·AI 검색 결과가 가게면 */
export function shopOf(l: LocationResult | null | undefined): Shop | null {
  if (!l || l.kind !== 'shop') return null
  return { id: l.placeId ?? null, name: l.name, lat: l.lat, lng: l.lng, address: l.detail ?? '', category: l.category ?? 'shop' }
}

/** 주소에 가게를 싣는다 (없으면 지운다) */
export function putShop(params: URLSearchParams, shop: Shop | null) {
  KEYS.forEach((k) => params.delete(k))
  if (!shop) return
  if (shop.id) params.set('shop', String(shop.id))
  params.set('sname', shop.name)
  params.set('slat', shop.lat.toFixed(6))
  params.set('slng', shop.lng.toFixed(6))
  params.set('saddr', shop.address)
  params.set('scat', shop.category)
}

export function shopFromParams(params: URLSearchParams): Shop | null {
  const name = params.get('sname')
  const lat = Number(params.get('slat'))
  const lng = Number(params.get('slng'))
  if (!name || !Number.isFinite(lat) || !Number.isFinite(lng) || lat === 0) return null
  const id = Number(params.get('shop'))
  return { id: Number.isInteger(id) && id > 0 ? id : null, name, lat, lng, address: params.get('saddr') ?? '', category: params.get('scat') ?? 'shop' }
}

/** 코스 상세로 넘길 주소 뒤쪽 ("?food=…&shop=…") */
export function shopQuery(shop: Shop | null, food: string | null): string {
  const q = new URLSearchParams()
  if (food) q.set('food', food)
  putShop(q, shop)
  const s = q.toString()
  return s ? `?${s}` : ''
}

/** 이미 이 일정에 든 가게인지 (우리 데이터면 id, 네이버 가게면 이름 + 100m) */
export function hasShop(plan: Plan, shop: Shop): boolean {
  return plan.steps.some((s) =>
    shop.id ? s.place.id === shop.id : s.place.name === shop.name && distanceMeters(s.place.lat, s.place.lng, shop.lat, shop.lng) < 100,
  )
}

/** 네이버에서만 찾은 가게 → 일정에 넣을 장소 (id 0, 분류 naver: 저장할 때 이름·주소·좌표를 그대로) */
export function naverShopPlace(shop: Shop): Place {
  return {
    id: 0,
    name: shop.name.slice(0, 30),
    branch: null,
    category: 'naver',
    detailCategory: null,
    lat: shop.lat,
    lng: shop.lng,
    sido: '',
    sigungu: null,
    roadAddress: shop.address,
    lotAddress: null,
    phone: null,
    imageUrl: null,
    description: null,
    naverMapUrl: `https://map.naver.com/p/search/${encodeURIComponent(`${shop.name} ${shop.address}`)}`,
  }
}

/**
 * 가게를 넣은 일정: 같은 종류 단계를 그 가게로 바꾼다 (카페 → 카페 단계, 식당·술집 → 식당 단계).
 * 같은 종류가 없거나 소품샵·놀거리면 끼워 넣는다 (카페는 식당 앞, 식당은 맨 뒤, 그 밖에는 첫 장소 다음)
 */
export function withShop(plan: Plan, place: Place, reasons: string[], category: string): Plan {
  const target = category === 'cafe' ? 'cafe' : category === 'restaurant' || category === 'bar' ? 'meal' : null
  const at = target ? plan.steps.findIndex((s) => s.role === target) : -1
  if (at >= 0) {
    const steps = plan.steps.map((s, i): PlanStep =>
      i === at ? { ...s, place, reasons, memo: null, origin: s.origin === 'added' ? 'added' : 'swap' } : s,
    )
    return { ...plan, steps }
  }
  const meal = plan.steps.findIndex((s) => s.role === 'meal')
  const index = target === 'cafe' ? (meal >= 0 ? meal : plan.steps.length) : target === 'meal' ? plan.steps.length : Math.min(1, plan.steps.length)
  const step: PlanStep = {
    key: `a${Date.now()}`,
    role: 'mine',
    place,
    reasons,
    stayMinutes: defaultStay(place.category === 'naver' ? category : place.category),
    fixedArrive: null,
    memo: null,
    courseOrder: null,
    origin: 'added',
  }
  return { ...plan, steps: [...plan.steps.slice(0, index), step, ...plan.steps.slice(index)] }
}
