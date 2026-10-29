import type { StepRole } from './types'

export function formatMinutes(minutes: number | null | undefined): string {
  if (!minutes) return ''
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}분`
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`
}

export function formatDistance(meters: number): string {
  return meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`
}

export const ROLE_LABEL: Record<StepRole, string> = {
  spot: '구경',
  cafe: '카페',
  meal: '식사',
}

export const CATEGORY_LABEL: Record<string, string> = {
  attraction: '명소',
  culture: '문화',
  activity: '놀거리',
  shop: '소품샵',
  cafe: '카페',
  restaurant: '식당',
  bar: '술집',
  custom: '직접 넣은 곳',
}

/** 검색 주소의 kw (쉼표로 이은 code) ↔ 배열 */
export function parseKeywords(value: string | null): string[] {
  return value ? value.split(',').filter(Boolean) : []
}
