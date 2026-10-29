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
  /** 실제로 찾은 반경. 고른 반경에 코스가 없으면 넓혀 찾고 widened */
  radius: number
  widened: boolean
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
  /** 이 장소를 고른 이유 (모범음식점, 관광공사 소개, 체인 아닌 동네 가게) */
  reasons: string[]
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

/** 코스 단계의 근처 대안. distanceMeters: 원래 단계 장소에서의 직선 거리 */
export interface AlternativeResponse {
  place: Place
  reasons: string[]
  distanceMeters: number
}

/** 서버에 저장한 내 일정의 단계 */
export interface PlanStepResponse {
  role: StepRole | 'mine'
  stayMinutes: number
  fixedArrive: number | null
  origin: 'course' | 'swap' | 'added'
  courseOrder: number | null
  place: Place
  reasons: string[]
}

export interface PlanResponse {
  courseId: number
  title: string
  date: string | null
  start: string
  savedAt: string
  steps: PlanStepResponse[]
}

/** 내 저장 목록의 일정 하나 */
export interface PlanSummary {
  courseId: number
  title: string
  sido: string
  sigungu: string | null
  date: string | null
  start: string
  end: string
  stepNames: string[]
  savedAt: string
}
