// 내 일정: 코스를 바탕으로 장소를 바꾸고·더하고·빼고·순서를 바꾸고, 출발 시간과 머무는 시간으로 시간표를 만든다.
// 이 브라우저에만 저장한다 (로그인 없음)
import { distanceMeters, walkMinutes } from './naverMap'
import { readJson, writeJson } from './storage'
import type { CourseDetail, Place, StepRole } from './types'

export type PlanRole = StepRole | 'mine'

export interface PlanStep {
  /** 화면 key (코스 단계는 c{순서}, 더한 곳은 a{시각}) */
  key: string
  role: PlanRole
  place: Place
  reasons: string[]
  stayMinutes: number
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
}

const planKey = (id: string | number) => `course-plan:${id}`
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
      memo: s.memo,
      courseOrder: s.order,
      origin: 'course',
    })),
  }
}

/** 저장해 둔 내 일정 (예전 "바꾸기" 저장분이 있으면 옮겨 온다) */
export function loadPlan(id: string, course: CourseDetail): Plan {
  const saved = readJson<Plan | null>(planKey(id), null)
  if (saved && Array.isArray(saved.steps) && saved.steps.length > 0) return saved
  const plan = initialPlan(course)
  const oldSwaps = readJson<Record<number, { place: Place; reasons: string[] }> | null>(oldSwapKey(id), null)
  if (oldSwaps) {
    plan.steps = plan.steps.map((s) =>
      s.courseOrder != null && oldSwaps[s.courseOrder]
        ? { ...s, place: oldSwaps[s.courseOrder].place, reasons: oldSwaps[s.courseOrder].reasons, origin: 'swap' as const, memo: null }
        : s,
    )
    writeJson(oldSwapKey(id), null)
    writeJson(planKey(id), plan)
  }
  return plan
}

export function savePlan(id: string, plan: Plan | null) {
  writeJson(planKey(id), plan)
}

/** 원래 코스와 장소·순서·머무는 시간이 다른가 (날짜·출발 시간은 바꿔도 "내가 바꾼 코스"로 보지 않는다) */
export function isChanged(plan: Plan, course: CourseDetail): boolean {
  const base = initialPlan(course).steps
  if (base.length !== plan.steps.length) return true
  return plan.steps.some((s, i) => s.place.id !== base[i].place.id || s.stayMinutes !== base[i].stayMinutes)
}

/** 시간표: 출발 시간부터 머무는 시간과 걷는 시간(직선 거리로 어림)을 더해 간다 */
export function timeline(plan: Plan): TimedStep[] {
  let clock = toMinutes(plan.start)
  return plan.steps.map((s, i) => {
    const prev = plan.steps[i - 1]
    const walk = prev ? walkMinutes(distanceMeters(prev.place.lat, prev.place.lng, s.place.lat, s.place.lng)) : null
    const arrive = clock + (walk ?? 0)
    const depart = arrive + s.stayMinutes
    clock = depart
    return { ...s, walkMinutes: walk, arrive, depart }
  })
}

export function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return (Number.isFinite(h) ? h : 0) * 60 + (Number.isFinite(m) ? m : 0)
}

/** 분 → "18:05" (자정을 넘기면 "다음날 00:30") */
export function clockText(minutes: number): string {
  const day = Math.floor(minutes / 1440)
  const m = minutes % 1440
  const text = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
  return day > 0 ? `다음날 ${text}` : text
}

/** 캘린더(.ics) 파일 내용: 단계마다 일정 하나 (시간대는 기기 시간 그대로) */
export function toIcs(title: string, plan: Plan, steps: TimedStep[], roleLabel: (r: PlanRole) => string): string {
  const base = new Date(`${plan.date || today()}T00:00:00`)
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z')
  const at = (minutes: number) => {
    const d = new Date(base.getTime() + minutes * 60000)
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}T${p(d.getHours())}${p(d.getMinutes())}00`
  }
  const esc = (v: string) => v.replace(/\\/g, '\\\\').replace(/[,;]/g, (c) => `\\${c}`).replace(/\n/g, '\\n')
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//date-course//KO', 'CALSCALE:GREGORIAN']
  steps.forEach((s, i) => {
    lines.push(
      'BEGIN:VEVENT',
      `UID:${plan.date}-${s.key}-${i}@date-course.kimseongmin.co.kr`,
      `DTSTAMP:${stamp}`,
      `DTSTART:${at(s.arrive)}`,
      `DTEND:${at(s.depart)}`,
      `SUMMARY:${esc(`${i + 1}. ${roleLabel(s.role)} · ${s.place.name}`)}`,
      `LOCATION:${esc(s.place.roadAddress ?? s.place.lotAddress ?? '')}`,
      `DESCRIPTION:${esc(`${title}\n${s.place.naverMapUrl}`)}`,
      'END:VEVENT',
    )
  })
  lines.push('END:VCALENDAR')
  return lines.join('\r\n')
}
