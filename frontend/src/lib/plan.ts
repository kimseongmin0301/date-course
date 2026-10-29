// 내 일정: 코스를 바탕으로 장소를 바꾸고·더하고·빼고·순서를 바꾸고, 출발 시간·머무는 시간·고정 도착 시간으로 시간표를 만든다.
// "저장"하면 서버에 둔다 (로그인 없이 쿠키 dc_vid 의 방문자 id 로, 다음에 와도 그대로).
// 저장하기 전의 고친 내용은 이 브라우저에 임시로 둔다 (새로고침해도 안 사라지게)
import { api } from './api'
import { distanceMeters, walkMinutes } from './naverMap'
import { readJson, writeJson } from './storage'
import type { CourseDetail, Place, PlanResponse, StepRole } from './types'

export type PlanRole = StepRole | 'mine'

export interface PlanStep {
  /** 화면 key (코스 단계는 c{순서}, 더한 곳은 a{시각}) */
  key: string
  role: PlanRole
  place: Place
  reasons: string[]
  stayMinutes: number
  /** 도착 시간을 고정했으면 그날 0시부터 분 (예약 등). 없으면 앞 일정에 이어서 */
  fixedArrive: number | null
  memo: string | null
  /** 원래 코스의 몇 번째 단계였는지 (근처 대안 API 에 씀). 더한 곳은 null */
  courseOrder: number | null
  /** course: 원래 그대로, swap: 다른 곳으로 바꿈, added: 내가 더함 */
  origin: 'course' | 'swap' | 'added'
}

export interface Plan {
  steps: PlanStep[]
  /** YYYY-MM-DD */
  date: string
  /** HH:MM */
  start: string
}

export interface TimedStep extends PlanStep {
  walkMinutes: number | null
  arrive: number
  depart: number
  /** 고정한 도착 시간보다 앞 일정이 늦게 끝나 늦게 도착함 */
  late: boolean
}

export const STAY_MAX = 720
/** 직접 넣은 가게 이름 최대 글자 수 (서버 PlanService.CUSTOM_NAME_MAX) */
export const CUSTOM_NAME_MAX = 30

/** 데이터에 없어 이름·주소로 직접 넣은 가게 (id 0, 분류 custom) */
export function isCustom(place: Place): boolean {
  return place.category === 'custom'
}

/** 이름과 주소 찾기로 고른 주소 → 직접 넣은 가게. 이름은 서버처럼 공백을 하나로 */
export function customPlace(name: string, address: { roadAddress: string; jibunAddress: string; lat: number; lng: number }): Place {
  const clean = name.trim().split(/\s+/).join(' ')
  const road = address.roadAddress || address.jibunAddress
  return {
    id: 0,
    name: clean,
    branch: null,
    category: 'custom',
    detailCategory: null,
    lat: address.lat,
    lng: address.lng,
    sido: '',
    sigungu: null,
    roadAddress: road,
    lotAddress: address.roadAddress ? address.jibunAddress || null : null,
    phone: null,
    imageUrl: null,
    description: null,
    naverMapUrl: `https://map.naver.com/p/search/${encodeURIComponent(`${clean} ${road}`)}`,
  }
}

const draftKey = (id: string | number) => `course-plan:${id}`
const oldSwapKey = (id: string | number) => `course-swaps:${id}`

/** 가게 종류별 기본 머무는 시간(분) */
export function defaultStay(category: string): number {
  switch (category) {
    case 'shop':
      return 30
    case 'restaurant':
      return 70
    case 'bar':
      return 90
    default:
      return 60
  }
}

export function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function initialPlan(course: CourseDetail): Plan {
  return {
    date: today(),
    start: '14:00',
    steps: course.steps.map((s) => ({
      key: `c${s.order}`,
      role: s.role,
      place: s.place,
      reasons: s.reasons,
      stayMinutes: s.stayMinutes ?? defaultStay(s.place.category),
      fixedArrive: null,
      memo: s.memo,
      courseOrder: s.order,
      origin: 'course',
    })),
  }
}

/** 서버에 저장한 일정 → 화면 일정 */
export function fromSaved(saved: PlanResponse, course: CourseDetail): Plan {
  const memoOf = (order: number | null) => course.steps.find((s) => s.order === order)?.memo ?? null
  return {
    date: saved.date ?? today(),
    start: saved.start,
    steps: saved.steps.map((s, i) => ({
      key: s.origin === 'added' ? `a${i}-${s.place.id}` : `c${s.courseOrder ?? i}-${s.place.id}`,
      role: s.role,
      place: s.place,
      reasons: s.reasons,
      stayMinutes: s.stayMinutes,
      fixedArrive: s.fixedArrive,
      memo: s.origin === 'course' ? memoOf(s.courseOrder) : null,
      courseOrder: s.courseOrder,
      origin: s.origin,
    })),
  }
}

/** 저장 요청 몸통 (장소는 id 만 보낸다: 서버가 지금 DB 의 장소로 이어 붙임. 직접 넣은 가게만 이름·주소·좌표) */
export function toRequest(plan: Plan) {
  return {
    date: plan.date || null,
    start: plan.start,
    steps: plan.steps.map((s) =>
      isCustom(s.place)
        ? {
            placeId: null,
            role: 'mine',
            stayMinutes: s.stayMinutes,
            fixedArrive: s.fixedArrive,
            origin: 'added',
            courseOrder: null,
            custom: { name: s.place.name, address: s.place.roadAddress ?? '', lat: s.place.lat, lng: s.place.lng },
          }
        : {
            placeId: s.place.id,
            role: s.role,
            stayMinutes: s.stayMinutes,
            fixedArrive: s.fixedArrive,
            origin: s.origin,
            courseOrder: s.courseOrder,
          },
    ),
  }
}

export async function loadSaved(courseId: string): Promise<PlanResponse | null> {
  try {
    return await api<PlanResponse>(`/plans/${courseId}`)
  } catch {
    return null // 저장한 적 없음 (404) 또는 서버 오류: 코스 그대로 보여준다
  }
}

export function saveToServer(courseId: string, plan: Plan): Promise<PlanResponse> {
  return api<PlanResponse>(`/plans/${courseId}`, { method: 'PUT', body: JSON.stringify(toRequest(plan)) })
}

/** 저장하지 않고 고친 내용 (이 브라우저 임시). 예전 "바꾸기" 저장분(course-swaps:)은 옮겨 온다 */
export function loadDraft(id: string, course: CourseDetail): Plan | null {
  const draft = readJson<Plan | null>(draftKey(id), null)
  if (draft && Array.isArray(draft.steps) && draft.steps.length > 0) {
    return { ...draft, steps: draft.steps.map((s) => ({ ...s, fixedArrive: s.fixedArrive ?? null })) }
  }
  const oldSwaps = readJson<Record<number, { place: Place; reasons: string[] }> | null>(oldSwapKey(id), null)
  if (!oldSwaps) return null
  const plan = initialPlan(course)
  plan.steps = plan.steps.map((s) =>
    s.courseOrder != null && oldSwaps[s.courseOrder]
      ? { ...s, place: oldSwaps[s.courseOrder].place, reasons: oldSwaps[s.courseOrder].reasons, origin: 'swap' as const, memo: null }
      : s,
  )
  writeJson(oldSwapKey(id), null)
  writeJson(draftKey(id), plan)
  return plan
}

export function saveDraft(id: string, plan: Plan | null) {
  writeJson(draftKey(id), plan)
}

/** 일정 내용(장소·순서·시간)이 같은가 */
export function samePlan(a: Plan, b: Plan): boolean {
  return JSON.stringify(toRequest(a)) === JSON.stringify(toRequest(b))
}

/** 원래 코스와 장소·순서·머무는 시간·고정 시간이 다른가 (날짜·출발 시간만 바꾼 것은 "내가 바꾼 코스"가 아님) */
export function isChanged(plan: Plan, course: CourseDetail): boolean {
  const base = initialPlan(course).steps
  if (base.length !== plan.steps.length) return true
  return plan.steps.some((s, i) => s.place.id !== base[i].place.id || s.stayMinutes !== base[i].stayMinutes || s.fixedArrive != null)
}

/** 시간표: 출발 시간부터 머무는 시간과 걷는 시간(직선 거리로 어림)을 더해 간다. 고정한 도착 시간이 있으면 그때까지 기다린다 */
export function timeline(plan: Plan): TimedStep[] {
  let clock = toMinutes(plan.start)
  return plan.steps.map((s, i) => {
    const prev = plan.steps[i - 1]
    const walk = prev ? walkMinutes(distanceMeters(prev.place.lat, prev.place.lng, s.place.lat, s.place.lng)) : null
    const natural = clock + (walk ?? 0)
    const arrive = s.fixedArrive != null ? Math.max(natural, s.fixedArrive) : natural
    const depart = arrive + s.stayMinutes
    clock = depart
    return { ...s, walkMinutes: walk, arrive, depart, late: s.fixedArrive != null && natural > s.fixedArrive }
  })
}

export function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return (Number.isFinite(h) ? h : 0) * 60 + (Number.isFinite(m) ? m : 0)
}

/** 분 → "HH:MM" (input type=time 에 넣을 값, 하루 안으로) */
export function timeValue(minutes: number): string {
  const m = ((minutes % 1440) + 1440) % 1440
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
}

/** 분 → "18:05" (자정을 넘기면 "다음날 00:30") */
export function clockText(minutes: number): string {
  return minutes >= 1440 ? `다음날 ${timeValue(minutes)}` : timeValue(minutes)
}
