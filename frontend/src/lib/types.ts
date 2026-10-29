// 백엔드 API 응답 모양 (AGENTS.md 7절 "API")

export type KeywordKind = 'mood' | 'activity' | 'setting' | 'budget'

export interface Keyword {
  code: string
  name: string
  kind: KeywordKind
}

export type StepRole = 'spot' | 'cafe' | 'meal'

export interface StepSummary {
  order: number
  role: StepRole
  stayMinutes: number | null
  walkMinutes: number | null
  placeId: number
  name: string
  branch: string | null
  category: string
  lat: number
  lng: number
}

export interface CourseCard {
  id: number
  title: string
  summary: string
  kind: 'curated' | 'auto'
  sido: string
  sigungu: string | null
  centerLat: number
  centerLng: number
  totalMinutes: number | null
  distanceMeters: number
  keywords: string[]
  matchedKeywords: string[]
  steps: StepSummary[]
}

export interface CourseSearchResponse {
  total: number
  page: number
  size: number
  courses: CourseCard[]
}

export interface Place {
  id: number
  name: string
  branch: string | null
  category: string
  detailCategory: string | null
  lat: number
  lng: number
  sido: string
  sigungu: string | null
  roadAddress: string | null
  lotAddress: string | null
  phone: string | null
  imageUrl: string | null
  description: string | null
  naverMapUrl: string
}

export interface StepDetail {
  order: number
  role: StepRole
  stayMinutes: number | null
  walkMinutes: number | null
  memo: string | null
  place: Place
}

export interface CourseDetail {
  id: number
  title: string
  summary: string
  kind: 'curated' | 'auto'
  sido: string
  sigungu: string | null
  centerLat: number
  centerLng: number
  totalMinutes: number | null
  budgetMin: number | null
  budgetMax: number | null
  keywords: string[]
  steps: StepDetail[]
}

export interface LocationResult {
  kind: 'area' | 'station' | 'place' | 'address'
  name: string
  detail: string | null
  lat: number
  lng: number
  /** 지도에 보여줄 반경(m) 제안 */
  radius: number
}
